package org.pipelineframework.connector;

/** Application or host policy for public payload access. Denials throw a security exception. */
@FunctionalInterface
public interface PayloadBoundaryAuthorizer {
    PayloadBoundaryOwner authorize(PayloadBoundaryAuthorizationRequest request);
}
