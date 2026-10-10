package org.pipelineframework.connector;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.pipelineframework.repository.PayloadReference;

class PayloadMaterializationContractTest {
    private static final ConnectorBindingName BINDING = ConnectorBindingName.of("documents");

    @Test
    void materializedPayloadDefensivelyCopiesBytes() {
        byte[] source = {1, 2, 3};
        PayloadReference reference = repositoryReference();

        MaterializedPayload payload = new MaterializedPayload(reference, source, "application/octet-stream", "raw", "sum");
        source[0] = 9;
        byte[] returned = payload.bytes();
        returned[1] = 9;

        assertArrayEquals(new byte[] {1, 2, 3}, payload.bytes());
    }

    @Test
    void bindingConfigurationDriftCannotResolveExistingReference() {
        ConnectorBindingRegistry first = registry("/data/one");
        ConnectorBindingRegistry changed = registry("/data/two");
        ConnectorPayloadOrigin origin = first.objectSourceOrigin(BINDING, "read", 1);

        assertNotEquals(origin, changed.objectSourceOrigin(BINDING, "read", 1));
        IllegalStateException failure = assertThrows(
            IllegalStateException.class, () -> changed.requireObjectSourceOperation(origin));
        assertEquals("connector payload binding provenance changed for 'documents'", failure.getMessage());
    }

    @Test
    void streamingReadRejectsChangedBindingBeforeProviderAccess() {
        ConnectorBindingRegistry first = registry("/data/one");
        ConnectorBindingRegistry changed = registry("/data/two");
        PayloadReference owned = repositoryReference().withConnectorOrigin(
            first.objectSourceOrigin(BINDING, "read", 1));

        CompletionException failure = assertThrows(CompletionException.class, () ->
            changed.openRead(owned).toCompletableFuture().join());
        assertEquals("connector payload binding provenance changed for 'documents'",
            failure.getCause().getMessage());
        assertThrows(IllegalArgumentException.class, () -> changed.openRead(repositoryReference()));

        TestProvider provider = new TestProvider();
        ConnectorBindingRegistry matching = registry("/data/one", provider);
        matching.activate(BINDING, ConnectorRuntimeContext.empty()).toCompletableFuture().join();
        ObjectReadSession session = new ObjectReadSession() {
            @Override
            public CompletionStage<Optional<ByteBuffer>> read(int maxBytes) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            @Override
            public void close() {
            }
        };
        provider.readStage = CompletableFuture.completedFuture(session);
        assertEquals(session, matching.openRead(owned).toCompletableFuture().join());
        provider.readStage = CompletableFuture.completedFuture(null);
        assertThrows(CompletionException.class, () -> matching.openRead(owned).toCompletableFuture().join());

        AtomicBoolean closed = new AtomicBoolean();
        CompletableFuture<ObjectReadSession> pending = new CompletableFuture<>();
        provider.readStage = pending;
        CompletableFuture<ObjectReadSession> cancelled = matching.openRead(owned).toCompletableFuture();
        cancelled.cancel(true);
        pending.complete(new ObjectReadSession() {
            @Override
            public CompletionStage<Optional<ByteBuffer>> read(int maxBytes) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            @Override
            public void close() {
                closed.set(true);
            }
        });
        assertEquals(true, closed.get());
    }

    private ConnectorBindingRegistry registry(String root) {
        return registry(root, new TestProvider());
    }

    private ConnectorBindingRegistry registry(String root, TestProvider provider) {
        return ConnectorBindingRegistry.fromProviders(
            List.of(new ConnectorBindingDefinition(
                BINDING,
                ConnectorProviderId.of("test.documents"),
                1,
                new ConnectorConfigurationDocument(Map.of("root", root)))),
            List.of(provider),
            prototype -> ConnectorProviderLease.of(provider));
    }

    private PayloadReference repositoryReference() {
        return new PayloadReference(
            "test", "payloads", "key", "application/octet-stream", "raw", "sum", 3, "v1", Map.of(),
            Optional.empty());
    }

    private record ProviderConfig(String root) {
    }

    private static final class TestProvider implements ConnectorProvider<ProviderConfig> {
        private CompletionStage<ObjectReadSession> readStage = CompletableFuture.failedFuture(
            new UnsupportedOperationException("not used"));
        private final ObjectSourceOperation operation = new ObjectSourceOperation() {
            @Override
            public String id() {
                return "read";
            }

            @Override
            public CompletionStage<MaterializedPayload> materialize(PayloadReference reference, long maxBytes) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException("not used"));
            }

            @Override
            public CompletionStage<ObjectReadSession> openRead(PayloadReference reference) {
                return readStage;
            }
        };

        @Override
        public ConnectorProviderId id() {
            return ConnectorProviderId.of("test.documents");
        }

        @Override
        public ConnectorProviderVersion version() {
            return new ConnectorProviderVersion(1, 0);
        }

        @Override
        public Optional<ConnectorConfigSchema<ProviderConfig>> configurationSchema() {
            return Optional.of(ConnectorConfigSchema.record(ProviderConfig.class, "test.documents.config", 1));
        }

        @Override
        public Collection<? extends ConnectorOperation> operations() {
            return List.of(operation);
        }
    }
}
