package org.pipelineframework.connector;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.pipelineframework.config.boundary.PipelineObjectPublishConfig;
import org.pipelineframework.objectpublish.ObjectWriteResult;

/** Provider capability for an upload whose bytes go directly to object storage. */
public interface DelegatedPayloadTarget {
    record Reservation(String token, int partSizeBytes, Instant expiresAt) {
        public Reservation {
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }

    record PartCapability(String url, Map<String, String> headers, Instant expiresAt) {
        public PartCapability {
            Objects.requireNonNull(url, "url");
            Objects.requireNonNull(expiresAt, "expiresAt");
            headers = headers == null ? Map.of() : Map.copyOf(headers);
        }
    }

    record Request(String targetName, PipelineObjectPublishConfig target, String contentType,
                   Map<String, String> metadata, long bytes, String sha256, Instant expiresAt,
                   String idempotencyKey) {
        public Request {
            metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
        }
    }

    /**
     * The token must bind the requested size, SHA-256, target, and owner to the reservation.
     * A provider owns work that completes after the caller's wait deadline. It must reject expired
     * reservations and reclaim abandoned backing uploads through its storage lifecycle or on retry.
     */
    CompletionStage<Reservation> initiate(Request request);

    CompletionStage<PartCapability> part(PipelineObjectPublishConfig target, String token,
                                         Map<String, String> expectedOwner, int partNumber);

    /** Verifies the stored bytes and SHA-256 against the initiation request before issuing a reference. */
    CompletionStage<ObjectWriteResult> complete(PipelineObjectPublishConfig target, String token,
                                                Map<String, String> expectedOwner);

    CompletionStage<Void> abort(PipelineObjectPublishConfig target, String token,
                                Map<String, String> expectedOwner);
}
