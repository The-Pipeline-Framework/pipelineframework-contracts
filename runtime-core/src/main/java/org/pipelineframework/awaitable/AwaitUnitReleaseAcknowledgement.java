package org.pipelineframework.awaitable;

import java.util.Objects;

/** Immutable acknowledgement written atomically with the parent execution's first disposition of one Await unit. */
public record AwaitUnitReleaseAcknowledgement(
    int schemaVersion,
    String tenantId,
    String executionId,
    String unitId,
    String pipelineId,
    String contractVersion,
    String releaseVersion,
    int stepIndex,
    long parentVersion,
    AwaitReleaseDisposition disposition,
    long acknowledgedAtEpochMs,
    long ttlEpochS
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public AwaitUnitReleaseAcknowledgement {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Await unit release acknowledgement schema version");
        }
        tenantId = required(tenantId, "tenantId");
        executionId = required(executionId, "executionId");
        unitId = required(unitId, "unitId");
        var occurrence = AwaitUnitOccurrence.fromUnitId(unitId);
        if (occurrence.isPresent() && (!occurrence.orElseThrow().tenantId().equals(tenantId)
            || !occurrence.orElseThrow().executionId().equals(executionId)
            || occurrence.orElseThrow().stepIndex() != stepIndex)) {
            throw new IllegalArgumentException("Await release acknowledgement differs from its occurrence scope");
        }
        pipelineId = required(pipelineId, "pipelineId");
        contractVersion = required(contractVersion, "contractVersion");
        releaseVersion = required(releaseVersion, "releaseVersion");
        disposition = Objects.requireNonNull(disposition, "disposition");
        if (stepIndex < 0 || parentVersion < 0) {
            throw new IllegalArgumentException("Await release step index and parent version must not be negative");
        }
        if (acknowledgedAtEpochMs < 0 || ttlEpochS < 0
            || (ttlEpochS > 0 && ttlEpochS <= acknowledgedAtEpochMs / 1_000L)) {
            throw new IllegalArgumentException("Await unit release acknowledgement has invalid time or retention");
        }
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
