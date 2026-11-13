package org.pipelineframework.awaitable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.pipelineframework.orchestrator.SerializedTransitionPayload;
import static org.junit.jupiter.api.Assertions.*;

class AwaitLifecycleObservationRequestTest {
    private static final AwaitTransitionScope SCOPE = new AwaitTransitionScope("tenant", "execution", "pipeline", "contract", "release", 1, "transition");
    private static final SerializedTransitionPayload ACK = new SerializedTransitionPayload("ack", "json", "{}");

    @Test void commandAcknowledgementCannotMasqueradeAsOrdinaryDispatchOrFailure() {
        assertThrows(IllegalArgumentException.class, () -> report(AwaitDispatchReportKind.DISPATCHED, Map.of(), Optional.empty(), Optional.empty(), Optional.of(ACK)));
        assertThrows(IllegalArgumentException.class, () -> report(AwaitDispatchReportKind.FAILED, Map.of(), Optional.of("failed"), Optional.empty(), Optional.of(ACK)));
        for (var disposition : List.of(CommandDispatchSettlement.AMBIGUOUS, CommandDispatchSettlement.RETRYABLE, CommandDispatchSettlement.TERMINAL, CommandDispatchSettlement.USER_ACTION_REQUIRED)) {
            assertThrows(IllegalArgumentException.class, () -> report(AwaitDispatchReportKind.COMMAND_SETTLEMENT, Map.of(), Optional.empty(), Optional.of(disposition), Optional.of(ACK)));
        }
        assertEquals(Optional.of(ACK), report(AwaitDispatchReportKind.COMMAND_SETTLEMENT, Map.of(), Optional.empty(), Optional.of(CommandDispatchSettlement.SUCCEEDED), Optional.of(ACK)).acknowledgement());
    }

    @Test void malformedKindEvidenceAndForgedAuthorityMetadataFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> report(AwaitDispatchReportKind.FAILED, Map.of(), Optional.empty(), Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> report(AwaitDispatchReportKind.COMMAND_SETTLEMENT, Map.of(), Optional.empty(), Optional.empty(), Optional.empty()));
        for (String key : List.of("tpf.await.admission.lease_token", "tpf.await.completion.projector", "tenantId", "callbackSelection", "commandSettlement", "resumeToken", "credentialRef")) {
            assertThrows(IllegalArgumentException.class, () -> report(AwaitDispatchReportKind.DISPATCHED, Map.of(key, "forged"), Optional.empty(), Optional.empty(), Optional.empty()));
        }
        assertEquals(Map.of("providerReceipt", "observed"), report(AwaitDispatchReportKind.DISPATCHED, Map.of("providerReceipt", "observed"), Optional.empty(), Optional.empty(), Optional.empty()).transportMetadata());
    }

    @Test void streamSealProposesOnlyNonnegativeAdmissionCount() {
        var unit = new AwaitWorkerUnitRequest("worker", "entry", SCOPE, "step", 8, "execution", "unit", Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> new AwaitUnitSealRequest(unit, -1));
        assertEquals(0, new AwaitUnitSealRequest(unit, 0).expectedItemCount());
    }

    private static AwaitDispatchReportRequest report(AwaitDispatchReportKind kind, Map<String,Object> metadata,
        Optional<String> failure, Optional<CommandDispatchSettlement> settlement, Optional<SerializedTransitionPayload> ack) {
        return new AwaitDispatchReportRequest("worker", "entry", SCOPE, "interaction", 2, kind, metadata, failure, settlement, ack);
    }
}
