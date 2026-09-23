package org.pipelineframework.paging;

import java.util.Objects;
import java.util.Optional;

/** Provider-opaque progress made by a normally terminated page. */
public record PagedSourceCompletion(
    int consumedRecords,
    Optional<String> nextCheckpoint,
    boolean exhausted
) {
    public PagedSourceCompletion {
        if (consumedRecords < 0) {
            throw new IllegalArgumentException("consumedRecords must not be negative");
        }
        nextCheckpoint = Objects.requireNonNull(nextCheckpoint, "nextCheckpoint must not be null");
        nextCheckpoint.ifPresent(value -> {
            if (value.isBlank()) {
                throw new IllegalArgumentException("nextCheckpoint must not be blank");
            }
        });
        if (!exhausted && nextCheckpoint.isEmpty()) {
            throw new IllegalArgumentException("a non-exhausted page requires a next checkpoint");
        }
    }

    /** Validate the completion against the request without interpreting the token. */
    public void validateAgainst(PagedSourceRequest<?> request) {
        Objects.requireNonNull(request, "request must not be null");
        if (consumedRecords > request.maxRecords()) {
            throw new IllegalArgumentException("page consumed more records than maxRecords");
        }
        if (!exhausted && nextCheckpoint.equals(request.checkpoint())) {
            throw new IllegalArgumentException("non-exhausted page did not advance its checkpoint");
        }
    }
}
