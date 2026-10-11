package org.pipelineframework.connector;

import java.util.Objects;
import java.util.Optional;
import org.pipelineframework.repository.PayloadReference;

/** HTTP host facts presented to the application's payload access policy. */
public record PayloadBoundaryAuthorizationRequest(
    Action action,
    String boundaryName,
    String authorizationScope,
    String principalName,
    String requestedTenantId,
    String requestedScopeId,
    Optional<PayloadReference> submittedReference
) {
    public enum Action { UPLOAD, DOWNLOAD }

    public PayloadBoundaryAuthorizationRequest {
        action = Objects.requireNonNull(action, "payload boundary action must not be null");
        boundaryName = Objects.requireNonNull(boundaryName, "boundary name must not be null");
        authorizationScope = Objects.requireNonNull(authorizationScope, "authorization scope must not be null");
        principalName = Objects.requireNonNull(principalName, "principal name must not be null");
        requestedTenantId = Objects.requireNonNull(requestedTenantId, "tenant id must not be null");
        requestedScopeId = Objects.requireNonNull(requestedScopeId, "scope id must not be null");
        submittedReference = Objects.requireNonNull(submittedReference, "submitted reference must not be null");
    }
}
