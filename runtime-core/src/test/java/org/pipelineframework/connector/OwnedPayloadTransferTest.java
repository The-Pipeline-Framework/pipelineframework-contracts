package org.pipelineframework.connector;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pipelineframework.config.boundary.PipelineHttpPayloadBoundaryConfig;
import org.pipelineframework.config.boundary.PipelineObjectSourceConfig;
import org.pipelineframework.repository.PayloadReference;

class OwnedPayloadTransferTest {
    @TempDir
    Path tempDir;

    @Test
    void rejectsASelectedSourceWithAnotherBoundaryName() {
        PipelineHttpPayloadBoundaryConfig boundary = new PipelineHttpPayloadBoundaryConfig(
            "receipt", PipelineHttpPayloadBoundaryConfig.Direction.DOWNLOAD, "receipts",
            "Receipt", "payload", List.of("application/pdf"), 0, "receipt");
        PipelineObjectSourceConfig source = source("other", "s3", Map.of("bucket", "receipts"));
        OwnedPayloadTransfer transfer = new OwnedPayloadTransfer(ConnectorBindingRegistry.empty(),
            ConnectorRuntimeContext.empty(), request -> new PayloadBoundaryOwner("tenant", "scope"));

        assertThrows(SecurityException.class, () -> transfer.checkDownload(boundary, source,
            "principal", "tenant", "scope", reference("s3", "receipts", "key")));
    }

    @Test
    void rejectsReferencesOutsideSelectedSourceLocation() throws Exception {
        PipelineObjectSourceConfig s3 = source("receipts", "s3",
            Map.of("bucket", "receipts", "prefix", "tenant-a/"));
        OwnedPayloadTransfer.requireSourceLocation(s3, reference("s3", "receipts", "tenant-a/item"));
        assertThrows(SecurityException.class, () ->
            OwnedPayloadTransfer.requireSourceLocation(s3, reference("s3", "other", "tenant-a/item")));
        assertThrows(SecurityException.class, () ->
            OwnedPayloadTransfer.requireSourceLocation(s3, reference("s3", "receipts", "tenant-b/item")));

        PipelineObjectSourceConfig filesystem = source("receipts", "filesystem",
            Map.of("root", tempDir.toString(), "prefix", "tenant-a"));
        OwnedPayloadTransfer.requireSourceLocation(filesystem,
            reference("filesystem", tempDir.toRealPath().toString(), "tenant-a/item"));
        assertThrows(SecurityException.class, () ->
            OwnedPayloadTransfer.requireSourceLocation(filesystem,
                reference("filesystem", tempDir.resolve("other").toString(), "tenant-a/item")));
        assertThrows(SecurityException.class, () ->
            OwnedPayloadTransfer.requireSourceLocation(filesystem,
                reference("filesystem", tempDir.toRealPath().toString(), "tenant-b/item")));
    }

    @Test
    void rejectsStreamWhoseChecksumDiffersFromReference() throws Exception {
        byte[] bytes = "payload".getBytes(StandardCharsets.UTF_8);
        try (OwnedPayloadTransfer.DownloadLease lease =
                 new OwnedPayloadTransfer.DownloadLease(session(bytes), bytes.length, "00")) {
            assertThrows(IllegalStateException.class, () -> lease.writeTo(new ByteArrayOutputStream()));
        }
    }

    @Test
    void allowsStreamWithoutReferenceChecksum() throws Exception {
        byte[] bytes = "payload".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (OwnedPayloadTransfer.DownloadLease lease =
                 new OwnedPayloadTransfer.DownloadLease(session(bytes), bytes.length, null)) {
            lease.writeTo(output);
        }
        assertArrayEquals(bytes, output.toByteArray());
    }

    private static ObjectReadSession session(byte[] bytes) {
        return new ObjectReadSession() {
            private boolean read;

            @Override
            public CompletionStage<Optional<ByteBuffer>> read(int maxBytes) {
                if (read) {
                    return CompletableFuture.completedFuture(Optional.empty());
                }
                read = true;
                return CompletableFuture.completedFuture(Optional.of(ByteBuffer.wrap(bytes)));
            }

            @Override
            public void close() {
            }
        };
    }

    private static PipelineObjectSourceConfig source(String name, String provider, Map<String, Object> location) {
        return new PipelineObjectSourceConfig(name, "object", provider, location, null, null, null, null);
    }

    private static PayloadReference reference(String provider, String container, String key) {
        return new PayloadReference(provider, container, key, "application/pdf", "raw", null, 1, null,
            Map.of(), Optional.empty());
    }
}
