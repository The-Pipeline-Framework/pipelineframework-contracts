package org.pipelineframework.orchestrator;

import java.util.Objects;
import java.util.Optional;

/** Control-plane context for one bounded source page. */
public record PagedTransitionContext(
    int pageIndex,
    String sourceIdentity,
    Optional<String> startCheckpoint,
    int maxRecords,
    Optional<PagedTransitionCompletion> suspendedCompletion
) {
    public PagedTransitionContext(
        int pageIndex,
        String sourceIdentity,
        Optional<String> startCheckpoint,
        int maxRecords
    ) {
        this(pageIndex, sourceIdentity, startCheckpoint, maxRecords, Optional.empty());
    }

    public PagedTransitionContext {
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
        suspendedCompletion = Objects.requireNonNull(
            suspendedCompletion, "suspendedCompletion must not be null");
        if (suspendedCompletion.isPresent()) {
            suspendedCompletion.orElseThrow().validateAgainst(
                new PagedTransitionContext(pageIndex, sourceIdentity, startCheckpoint, maxRecords));
        }
    }
}
