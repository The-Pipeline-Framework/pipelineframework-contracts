package org.pipelineframework.orchestrator;

import java.util.Objects;
import java.util.Optional;

/** Durable start boundary for the one page currently owned by a logical execution. */
public record PagedExecutionState(
    int pageIndex,
    String sourceIdentity,
    Optional<String> startCheckpoint,
    int maxRecords
) {
    public PagedExecutionState {
        if (pageIndex < 0) {
            throw new IllegalArgumentException("pageIndex must not be negative");
        }
        if (sourceIdentity == null || sourceIdentity.isBlank()) {
            throw new IllegalArgumentException("sourceIdentity must not be blank");
        }
        startCheckpoint = Objects.requireNonNull(startCheckpoint, "startCheckpoint must not be null");
        startCheckpoint.ifPresent(value -> {
            if (value.isBlank()) {
                throw new IllegalArgumentException("startCheckpoint must not be blank");
            }
        });
        if (maxRecords < 1) {
            throw new IllegalArgumentException("maxRecords must be positive");
        }
    }

    public PagedTransitionContext toTransitionContext() {
        return new PagedTransitionContext(pageIndex, sourceIdentity, startCheckpoint, maxRecords);
    }

    public PagedExecutionState successor(String checkpoint) {
        return new PagedExecutionState(pageIndex + 1, sourceIdentity,
            Optional.of(Objects.requireNonNull(checkpoint, "checkpoint must not be null")), maxRecords);
    }
}
