package org.pipelineframework.orchestrator;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionAdmissionStoreContractTest {
    @Test
    void storeExposesSeparateStrictAdmissionAndReadOnlyInquiry() {
        assertDoesNotThrow(() -> ExecutionStateStore.class.getMethod("supportsExecutionAdmission"));
        assertDoesNotThrow(() -> ExecutionStateStore.class.getMethod("getExecutionAdmission",
            String.class, String.class, String.class));
    }

    @Test
    void legacyProviderDefaultsCannotDispatchCreateCleanUpOrEmulateInquiry() {
        AtomicInteger legacyCalls = new AtomicInteger();
        ExecutionStateStore store = (ExecutionStateStore) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {ExecutionStateStore.class}, (proxy, method, args) -> {
                if (method.isDefault()) {
                    return InvocationHandler.invokeDefault(proxy, method, args);
                }
                legacyCalls.incrementAndGet();
                throw new AssertionError("Strict admission must not call legacy " + method.getName());
            });
        assertFalse(store.supportsExecutionAdmission());
        assertThrows(UnsupportedOperationException.class,
            () -> store.createOrGetAdmittedExecution(command()).await().indefinitely());
        assertThrows(UnsupportedOperationException.class,
            () -> store.getExecutionAdmission("tenant", "pipeline", "key").await().indefinitely());
        assertEquals(0, legacyCalls.get());
    }

    @Test
    void wrapperPreservesExistingCreateShapeAndRejectsPinTenantMismatch() {
        ExecutionAdmissionCreateCommand original = command();
        assertEquals("legacy-scoped-key", original.execution().executionKey());
        assertEquals("key", original.intent().clientKey());
        for (ExecutionCreateCommand wrong : new ExecutionCreateCommand[] {
            create("other", "pipeline", "contract", "release"),
            create("tenant", "other", "contract", "release"),
            create("tenant", "pipeline", "other", "release"),
            create("tenant", "pipeline", "contract", "other")}) {
            assertThrows(IllegalArgumentException.class, () -> new ExecutionAdmissionCreateCommand(wrong,
                original.intent(), "a".repeat(64), "artifact", "sha256:artifact"));
        }
        assertThrows(IllegalArgumentException.class, () -> new ExecutionAdmissionCreateCommand(original.execution(),
            original.intent(), "client-asserted-id", "artifact", "sha256:artifact"));
    }

    @Test
    void resultCannotAssociateReceiptWithAnotherUuidTenantOrPin() {
        ExecutionAdmissionCreateCommand original = command();
        ExecutionAdmissionReceipt receipt = new ExecutionAdmissionReceipt(1, "tenant", "pipeline", "key",
            "contract", "release", "execution", "a".repeat(64), "artifact", "sha256:artifact",
            original.intent().fingerprint(), 1000L);
        CreateExecutionResult creation = new CreateExecutionResult(record("tenant", "pipeline", "contract", "release", "execution"), false);
        assertEquals(receipt, new ExecutionAdmissionResult(Optional.of(creation), receipt).receipt());
        for (ExecutionRecord<Object, Object> wrong : new ExecutionRecord[] {
            record("other", "pipeline", "contract", "release", "execution"),
            record("tenant", "other", "contract", "release", "execution"),
            record("tenant", "pipeline", "other", "release", "execution"),
            record("tenant", "pipeline", "contract", "other", "execution"),
            record("tenant", "pipeline", "contract", "release", "other")}) {
            assertThrows(IllegalArgumentException.class,
                () -> new ExecutionAdmissionResult(Optional.of(new CreateExecutionResult(wrong, true)), receipt));
        }
    }

    @Test
    void freshLiveAndHistoricalResultsPreserveReceiptWithoutResurrectingRecord() {
        ExecutionAdmissionCreateCommand original = command();
        ExecutionAdmissionReceipt receipt = new ExecutionAdmissionReceipt(1, "tenant", "pipeline", "key",
            "contract", "release", "execution", "a".repeat(64), "artifact", "sha256:artifact",
            original.intent().fingerprint(), 1000L);
        ExecutionRecord<Object, Object> execution = record("tenant", "pipeline", "contract", "release", "execution");
        ExecutionAdmissionResult fresh = new ExecutionAdmissionResult(
            Optional.of(new CreateExecutionResult(execution, false)), receipt);
        ExecutionAdmissionResult live = new ExecutionAdmissionResult(
            Optional.of(new CreateExecutionResult(execution, true)), receipt);
        ExecutionAdmissionResult historical = new ExecutionAdmissionResult(Optional.empty(), receipt);
        assertTrue(fresh.newlyCreated());
        assertFalse(live.newlyCreated());
        assertFalse(historical.newlyCreated());
        assertEquals(execution, fresh.creation().orElseThrow().record());
        assertEquals(execution, live.creation().orElseThrow().record());
        assertTrue(historical.creation().isEmpty());
        assertEquals(receipt, historical.receipt());
        assertEquals("execution", historical.receipt().executionId());
        assertEquals("contract", historical.receipt().contractVersion());
        assertEquals("release", historical.receipt().releaseVersion());
        assertThrows(NullPointerException.class, () -> new ExecutionAdmissionResult(null, receipt));
        assertThrows(NullPointerException.class, () -> new ExecutionAdmissionResult(Optional.empty(), null));
        assertThrows(NullPointerException.class,
            () -> new ExecutionAdmissionResult(Optional.of(new CreateExecutionResult(null, true)), receipt));
    }

    private static ExecutionRecord<Object, Object> record(String tenant, String pipeline, String contract,
        String release, String executionId) {
        return new ExecutionRecord<>(tenant, executionId, "legacy-scoped-key", pipeline, contract, release,
            ExecutionResultShape.SINGLE, ExecutionStatus.QUEUED, 0L, 0, 0, null, 0L, 1000L, null,
            "decoded-input", null, null, null, null, 1000L, 1000L, 10000L);
    }

    private static ExecutionAdmissionCreateCommand command() {
        ExecutionAdmissionIntent intent = new ExecutionAdmissionIntent(1, "tenant", "pipeline", "key", "contract",
            "release", "UNI", "type", "json", new byte[] {7}, false);
        return new ExecutionAdmissionCreateCommand(create("tenant", "pipeline", "contract", "release"),
            intent, "a".repeat(64), "artifact", "sha256:artifact");
    }

    private static ExecutionCreateCommand create(String tenant, String pipeline, String contract, String release) {
        return new ExecutionCreateCommand(tenant, "legacy-scoped-key", pipeline, contract, release,
            "decoded-input", ExecutionResultShape.SINGLE, 1000L, 10000L);
    }
}
