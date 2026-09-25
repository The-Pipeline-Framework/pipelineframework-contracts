package org.pipelineframework.orchestrator;

import java.util.Objects;
import java.util.Optional;

/** Provider-opaque page progress returned only after normal source and resource completion. */
public record PagedTransitionCompletion(
    int consumedRecords,
    Optional<String> nextCheckpoint,
    boolean exhausted
) {
    public PagedTransitionCompletion {
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
            throw new IllegalArgumentException("a non-exhausted page requires a successor checkpoint");
        }
    }

    public void validateAgainst(PagedTransitionContext context) {
        Objects.requireNonNull(context, "context must not be null");
        if (consumedRecords > context.maxRecords()) {
            throw new IllegalArgumentException("page consumed more records than maxRecords");
        }
        if (!exhausted && nextCheckpoint.equals(context.startCheckpoint())) {
            throw new IllegalArgumentException("a non-exhausted page must advance its checkpoint");
        }
    }
}
