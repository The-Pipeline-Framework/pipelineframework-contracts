package org.pipelineframework.awaitable;

import java.util.Objects;
import java.util.Optional;
import org.pipelineframework.invocation.PipelineInvocationPath;
import org.pipelineframework.orchestrator.SerializedTransitionPayload;

/** Worker-proposed occurrence and canonical value; policy and dispatch authority remain Coordinator-owned. */
public record AwaitRegistrationRequest(AwaitTransitionScope scope, String stepId, int stepIndex,
    String identityScope, String unitId, String causationId, Optional<Integer> itemIndex,
    SerializedTransitionPayload canonicalRequest, Optional<String> assignee, Optional<String> group,
    Optional<PipelineInvocationPath> invocationPath) {
    public AwaitRegistrationRequest {
        Objects.requireNonNull(scope, "scope");
        for (String value : new String[] {stepId, identityScope, unitId, causationId}) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("Await occurrence identities must not be blank");
        }
        if (stepIndex < 0) throw new IllegalArgumentException("Step index must be nonnegative");
        Objects.requireNonNull(itemIndex, "itemIndex").ifPresent(index -> {
            if (index < 0) throw new IllegalArgumentException("Item index must be nonnegative");
        });
        Objects.requireNonNull(canonicalRequest, "canonicalRequest");
        Objects.requireNonNull(assignee, "assignee");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(invocationPath, "invocationPath").ifPresent(PipelineInvocationPath::requireResumable);
    }
}
