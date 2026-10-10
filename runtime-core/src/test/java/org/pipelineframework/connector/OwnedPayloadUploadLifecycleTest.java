package org.pipelineframework.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.pipelineframework.config.boundary.PipelineHttpPayloadBoundaryConfig;
import org.pipelineframework.config.boundary.PipelineObjectNamingConfig;
import org.pipelineframework.config.boundary.PipelineObjectPublishConfig;
import org.pipelineframework.config.boundary.PipelineObjectPublishGroupingConfig;
import org.pipelineframework.config.boundary.PipelineObjectPublishPayloadConfig;
import org.pipelineframework.objectpublish.ObjectTargetProvider;
import org.pipelineframework.objectpublish.ObjectWriteCloseRequest;
import org.pipelineframework.objectpublish.ObjectWriteOpenRequest;
import org.pipelineframework.objectpublish.ObjectWriteResult;
import org.pipelineframework.objectpublish.ObjectWriteSession;
import org.pipelineframework.repository.PayloadReference;

/** Independent lifecycle oracles for #728: provider demand and abort after disconnect. */
class OwnedPayloadUploadLifecycleTest {
    private static final PipelineHttpPayloadBoundaryConfig BOUNDARY = new PipelineHttpPayloadBoundaryConfig(
        "invoice", PipelineHttpPayloadBoundaryConfig.Direction.UPLOAD, "invoices", "Invoice", "payload_ref",
        List.of("application/pdf"), 200_000, "invoice.write");
    private static final PipelineObjectPublishConfig TARGET = new PipelineObjectPublishConfig(
        "invoices", "object", "test", Optional.of("files"), Map.of(), PipelineObjectNamingConfig.defaults(),
        PipelineObjectPublishPayloadConfig.defaults(), PipelineObjectPublishGroupingConfig.defaults());

    @Test
    void disconnectReleasesSessionThatFinishesOpeningAfterRequestEnds() throws Exception {
        UploadProvider provider = new UploadProvider();
        provider.delayOpen = true;
        AtomicBoolean cancelled = new AtomicBoolean();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var upload = executor.submit(() -> transfer(provider).upload(BOUNDARY, TARGET,
                "alice", "tenant-a", "invoice-1", "application/pdf", new ByteArrayInputStream(new byte[] {1}),
                cancelled::get));
            try {
                ObjectWriteSession late = provider.created.get(2, TimeUnit.SECONDS);
                cancelled.set(true);
                ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> upload.get(2, TimeUnit.SECONDS));
                assertTrue(failure.getCause() instanceof CancellationException);
                provider.opening.complete(late);
                assertTrue(provider.aborted.await(2, TimeUnit.SECONDS), "late session must be aborted");
                assertEquals(0, provider.writes.get());
                assertEquals(0, provider.closes.get());
            } finally {
                cancelled.set(true);
                provider.opening.complete(provider.created.get(2, TimeUnit.SECONDS));
                provider.writeGate.complete(true);
            }
        }
    }

    @Test
    void disconnectAbortsWhileProviderWriteIsStillPending() throws Exception {
        UploadProvider provider = new UploadProvider();
        AtomicBoolean cancelled = new AtomicBoolean();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var upload = executor.submit(() -> transfer(provider).upload(BOUNDARY, TARGET,
                "alice", "tenant-a", "invoice-1", "application/pdf", new ByteArrayInputStream(new byte[] {1}),
                cancelled::get));
            try {
                assertTrue(provider.writing.await(2, TimeUnit.SECONDS), "provider write was not reached");
                cancelled.set(true);
                assertTrue(provider.aborted.await(2, TimeUnit.SECONDS),
                    "disconnect must abort without waiting for provider write completion");
                ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> upload.get(2, TimeUnit.SECONDS));
                assertTrue(failure.getCause() instanceof CancellationException);
                assertEquals(0, provider.closes.get(), "cancelled content must never complete");
            } finally {
                cancelled.set(true);
                provider.writeGate.complete(true); // Release the defective revision too; no hung test worker.
            }
        }
    }

    @Test
    void respectsProviderDemandAndReturnsIndependentContentMetadata() throws Exception {
        UploadProvider provider = new UploadProvider();
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicInteger bodyReads = new AtomicInteger();
        byte[] bytes = new byte[131_073];
        try (var executor = Executors.newSingleThreadExecutor()) {
            var upload = executor.submit(() -> transfer(provider).upload(BOUNDARY, TARGET,
                "alice", "tenant-a", "invoice-1", "application/pdf", new ByteArrayInputStream(bytes) {
                    @Override
                    public synchronized int read(byte[] buffer, int offset, int length) {
                        bodyReads.incrementAndGet();
                        return super.read(buffer, offset, length);
                    }
                }, cancelled::get));
            try {
                assertTrue(provider.writing.await(2, TimeUnit.SECONDS));
                assertEquals(1, bodyReads.get(), "no second body read before downstream write completes");
                assertEquals(1, provider.writes.get());
                provider.writeGate.complete(true);
                PayloadReference result = upload.get(2, TimeUnit.SECONDS);
                assertEquals(bytes.length, result.sizeBytes());
                assertEquals("application/pdf", result.contentType());
                assertEquals("tenant-a", result.metadata().get(OwnedPayloadTransfer.OWNER_TENANT));
                assertEquals("invoice-1", result.metadata().get(OwnedPayloadTransfer.OWNER_SCOPE));
                assertTrue(result.connectorOrigin().isPresent());
                assertEquals(1, provider.closes.get());
                assertEquals(1, provider.aborted.getCount());
                assertEquals(3, provider.writes.get());
                assertTrue(provider.largestChunk.get() <= 65_536);
            } finally {
                cancelled.set(true);
                provider.writeGate.complete(true);
            }
        }
    }

    private OwnedPayloadTransfer transfer(UploadProvider provider) {
        ConnectorBindingRegistry bindings = ConnectorBindingRegistry.fromProviders(
            List.of(new ConnectorBindingDefinition(ConnectorBindingName.of("files"), provider.id(), 1,
                new ConnectorConfigurationDocument(Map.of()))), List.of(provider),
            ignored -> ConnectorProviderLease.of(provider));
        return new OwnedPayloadTransfer(bindings, ConnectorRuntimeContext.empty(),
            request -> new PayloadBoundaryOwner(request.requestedTenantId(), request.requestedScopeId()));
    }

    private static final class UploadProvider implements ConnectorProvider<Object> {
        private final CompletableFuture<Boolean> writeGate = new CompletableFuture<>();
        private final CompletableFuture<Void> write = CompletableFuture.allOf(writeGate);
        private final CountDownLatch writing = new CountDownLatch(1);
        private final CountDownLatch aborted = new CountDownLatch(1);
        private final AtomicInteger writes = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();
        private final AtomicInteger largestChunk = new AtomicInteger();
        private final CompletableFuture<ObjectWriteSession> opening = new CompletableFuture<>();
        private final CompletableFuture<ObjectWriteSession> created = new CompletableFuture<>();
        private boolean delayOpen;

        @Override
        public ConnectorProviderId id() { return ConnectorProviderId.of("test.payload"); }

        @Override
        public ConnectorProviderVersion version() { return new ConnectorProviderVersion(1, 0); }

        @Override
        public Collection<? extends ConnectorOperation> operations() {
            // The canonical reference needs source provenance even for upload.
            return List.of(new ObjectTargetProvider() {
                @Override public String providerName() { return "test"; }
                @Override public CompletionStage<ObjectWriteSession> open(ObjectWriteOpenRequest request) {
                    return UploadProvider.this.open(request);
                }
            }, new ObjectSourceOperation() {
                @Override public String id() { return "test"; }
            });
        }

        public CompletionStage<ObjectWriteSession> open(ObjectWriteOpenRequest request) {
            ObjectWriteSession session = new ObjectWriteSession() {
                @Override
                public CompletionStage<Void> write(ByteBuffer chunk) {
                    largestChunk.accumulateAndGet(chunk.remaining(), Math::max);
                    writes.incrementAndGet();
                    writing.countDown();
                    return write;
                }

                @Override
                public CompletionStage<ObjectWriteResult> close(ObjectWriteCloseRequest close) {
                    closes.incrementAndGet();
                    PayloadReference reference = new PayloadReference("test", "objects", request.objectKey(),
                        request.contentType(), "raw", close.checksum(), close.bytes(), "v1", request.metadata(),
                        Optional.empty());
                    return CompletableFuture.completedFuture(new ObjectWriteResult(reference, close.bytes(),
                        close.checksum(), Instant.EPOCH));
                }

                @Override
                public CompletionStage<Void> abort(Throwable cause) {
                    aborted.countDown();
                    return ConnectorCompletionStages.completed();
                }
            };
            created.complete(session);
            return delayOpen ? opening : CompletableFuture.completedFuture(session);
        }
    }
}
