package org.pipelineframework.connector;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class OwnedPayloadTransferTest {
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
}
