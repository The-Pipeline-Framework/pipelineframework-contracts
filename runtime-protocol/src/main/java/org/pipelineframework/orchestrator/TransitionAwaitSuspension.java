package org.pipelineframework.orchestrator;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.pipelineframework.awaitable.AwaitInteractionRecord;
import org.pipelineframework.awaitable.AwaitUnitRecord;

/**
 * Await suspension metadata returned by a worker.
 */
public record TransitionAwaitSuspension(
    String tenantId,
    String executionId,
    String unitId,
    int stepIndex,
    AwaitUnitRecord unit,
    List<AwaitInteractionRecord> interactions,
    Optional<PagedTransitionCompletion> pageCompletion
) {
    public TransitionAwaitSuspension(
        String tenantId,
        String executionId,
        String unitId,
        int stepIndex,
        AwaitUnitRecord unit,
        List<AwaitInteractionRecord> interactions
    ) {
        this(tenantId, executionId, unitId, stepIndex, unit, interactions, Optional.empty());
    }

    public TransitionAwaitSuspension(String tenantId, String executionId, String unitId, int stepIndex) {
        this(tenantId, executionId, unitId, stepIndex, null, List.of(), Optional.empty());
    }

    public TransitionAwaitSuspension {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("executionId must not be blank");
        }
        if (unitId == null || unitId.isBlank()) {
            throw new IllegalArgumentException("unitId must not be blank");
        }
        if (stepIndex < 0) {
            throw new IllegalArgumentException("stepIndex must be >= 0");
        }
        var occurrence = org.pipelineframework.awaitable.AwaitUnitOccurrence.fromUnitId(unitId);
        if (occurrence.isPresent() && (!tenantId.equals(occurrence.orElseThrow().tenantId())
            || !executionId.equals(occurrence.orElseThrow().executionId())
            || stepIndex != occurrence.orElseThrow().stepIndex())) {
            throw new IllegalArgumentException("Await suspension differs from occurrence scope");
        }
        interactions = interactions == null ? List.of() : List.copyOf(interactions);
        if (unit != null && (!tenantId.equals(unit.tenantId()) || !executionId.equals(unit.executionId())
            || !unitId.equals(unit.unitId()) || stepIndex != unit.stepIndex())) {
            throw new IllegalArgumentException("Await suspension differs from its unit");
        }
        for (var interaction : interactions) {
            if (!tenantId.equals(interaction.tenantId()) || !executionId.equals(interaction.executionId())
                || !unitId.equals(interaction.unitId()) || stepIndex != interaction.stepIndex()
                || (unit != null && !unit.stepId().equals(interaction.stepId()))) {
                throw new IllegalArgumentException("Await suspension contains a foreign interaction");
            }
        }
        pageCompletion = Objects.requireNonNull(pageCompletion, "pageCompletion must not be null");
    }

    public TransitionAwaitSuspension withPageCompletion(PagedTransitionCompletion completion) {
        return new TransitionAwaitSuspension(
            tenantId, executionId, unitId, stepIndex, unit, interactions,
            Optional.of(Objects.requireNonNull(completion, "completion must not be null")));
    }
}
