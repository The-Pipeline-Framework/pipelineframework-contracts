package org.pipelineframework.paging;

import java.util.Objects;
import java.util.Optional;

/** One bounded read of a release-pinned, provider-owned source snapshot. */
public record PagedSourceRequest<I>(
    I input,
    String sourceIdentity,
    Optional<String> checkpoint,
    int maxRecords
) {
    public PagedSourceRequest {
        Objects.requireNonNull(input, "paged source input must not be null");
        if (sourceIdentity == null || sourceIdentity.isBlank()) {
            throw new IllegalArgumentException("paged source identity must not be blank");
        }
        checkpoint = Objects.requireNonNull(checkpoint, "paged source checkpoint must not be null");
        checkpoint.ifPresent(value -> {
            if (value.isBlank()) {
                throw new IllegalArgumentException("paged source checkpoint must not be blank");
            }
        });
        if (maxRecords < 1) {
            throw new IllegalArgumentException("paged source maxRecords must be positive");
        }
    }
}
