package org.pipelineframework.awaitable;

import java.util.Objects;

/** Signed worker identity plus a scoped registration proposal, without credentials. */
public record AwaitWorkerRegistrationRequest(String workerId, String entryId, AwaitRegistrationRequest registration) {
    public AwaitWorkerRegistrationRequest {
        if (workerId == null || workerId.isBlank() || entryId == null || entryId.isBlank()) {
            throw new IllegalArgumentException("Worker and declared entry identities are required");
        }
        Objects.requireNonNull(registration, "registration");
    }
}
