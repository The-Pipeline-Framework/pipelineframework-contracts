package org.pipelineframework.awaitable;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Immutable first admission of one correlated Await completion, including one item completion. */
public record AwaitCompletionAdmission(
    int schemaVersion,
    String tenantId,
    String executionId,
    String interactionId,
    Optional<String> unitId,
    String stepId,
    int stepIndex,
    OptionalInt itemIndex,
    Optional<String> causationId,
    long firstAdmittedAtEpochMs,
    long ttlEpochS
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public AwaitCompletionAdmission {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Await completion admission schema version");
        }
        tenantId = required(tenantId, "tenantId");
        executionId = required(executionId, "executionId");
        interactionId = required(interactionId, "interactionId");
        unitId = optional(unitId, "unitId");
        stepId = required(stepId, "stepId");
        var occurrence = unitId.flatMap(AwaitUnitOccurrence::fromUnitId);
        if (occurrence.isPresent() && !occurrence.orElseThrow().matches(tenantId, executionId, stepId, stepIndex)) {
            throw new IllegalArgumentException("Await admission differs from its occurrence scope");
        }
        itemIndex = Objects.requireNonNull(itemIndex, "itemIndex");
        causationId = optional(causationId, "causationId");
        if (stepIndex < 0 || (itemIndex.isPresent() && itemIndex.getAsInt() < 0)) {
            throw new IllegalArgumentException("Await completion indices must not be negative");
        }
        if (firstAdmittedAtEpochMs < 0 || ttlEpochS < 0
            || (ttlEpochS > 0 && ttlEpochS <= firstAdmittedAtEpochMs / 1_000L)) {
            throw new IllegalArgumentException("Await completion admission has invalid time or retention");
        }
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static Optional<String> optional(Optional<String> value, String name) {
        return Objects.requireNonNull(value, name).filter(text -> !text.isBlank());
    }
}
