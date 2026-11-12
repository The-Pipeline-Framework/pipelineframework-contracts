package org.pipelineframework.awaitable;

import java.util.Objects;

/** Requests one first dispatch grant; an existing intent never grants another send. */
public record AwaitDispatchClaimRequest(String workerId, String entryId, AwaitTransitionScope scope,
    String interactionId, long expectedInteractionVersion) {
    public AwaitDispatchClaimRequest {
        for (String value : new String[] {workerId, entryId, interactionId}) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("Dispatch identities are required");
        }
        Objects.requireNonNull(scope, "scope");
        if (expectedInteractionVersion < 0) throw new IllegalArgumentException("Expected interaction version must be nonnegative");
    }
}
