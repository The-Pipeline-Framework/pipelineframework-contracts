package org.pipelineframework.orchestrator;

import java.util.Objects;

/** The original immutable Release entry observation selected when an execution was admitted. */
public record ExecutionDispatchPin(String entryId, String deploymentOperationId) {
    public ExecutionDispatchPin {
        for (String value : new String[] {entryId, deploymentOperationId}) {
            Objects.requireNonNull(value, "Dispatch pin identity");
            if (value.isBlank() || !value.equals(value.trim())) {
                throw new IllegalArgumentException("Dispatch pin identities must be nonblank canonical values");
            }
        }
    }
}
