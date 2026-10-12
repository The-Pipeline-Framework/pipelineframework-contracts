package org.pipelineframework.connector;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import org.pipelineframework.repository.PayloadReference;

/** Provider capability for a short-lived download URL issued after reference verification. */
public interface DelegatedPayloadSource {
    record DownloadCapability(String url, Map<String, String> headers, Instant expiresAt,
                              long bytes, String sha256) {
        public DownloadCapability {
            headers = Map.copyOf(headers);
        }
    }

    CompletionStage<DownloadCapability> authorizeDownload(PayloadReference reference, Duration lifetime);
}
