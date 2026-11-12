package org.pipelineframework.awaitable;

/** Exact leased transition and immutable Release identity authorized by the Coordinator. */
public record AwaitTransitionScope(String tenantId, String executionId, String pipelineId,
    String contractVersion, String releaseVersion, long claimedExecutionVersion, String transitionKey) {
    public AwaitTransitionScope {
        for (String value : new String[] {tenantId, executionId, pipelineId, contractVersion, releaseVersion, transitionKey}) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("Await transition identities must not be blank");
        }
        if (claimedExecutionVersion < 0) throw new IllegalArgumentException("Claimed version must be nonnegative");
    }
}
