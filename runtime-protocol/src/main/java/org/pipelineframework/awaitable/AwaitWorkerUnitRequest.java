package org.pipelineframework.awaitable;

import java.util.Objects;
import java.util.Optional;
import org.pipelineframework.invocation.PipelineInvocationPath;

/** Exact execution occurrence used by bounded prepare, seal, suspension and resume operations. */
public record AwaitWorkerUnitRequest(String workerId, String entryId, AwaitTransitionScope scope, String stepId,
    int stepIndex, String identityScope, String unitId, Optional<PipelineInvocationPath> invocationPath) {
    public AwaitWorkerUnitRequest {
        for (String value : new String[] {workerId, entryId, stepId, identityScope, unitId}) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("Await unit identities are required");
        }
        Objects.requireNonNull(scope, "scope");
        if (stepIndex < 0) throw new IllegalArgumentException("Await unit step index must be nonnegative");
        Objects.requireNonNull(invocationPath, "invocationPath").ifPresent(PipelineInvocationPath::requireResumable);
    }
}
