package org.pipelineframework.connector;

/** Authorised tenant and business/execution scope for one HTTP payload boundary request. */
public record PayloadBoundaryOwner(String tenantId, String scopeId) {
    public PayloadBoundaryOwner {
        if (tenantId == null || tenantId.isBlank() || scopeId == null || scopeId.isBlank()) {
            throw new IllegalArgumentException("payload owner requires tenantId and scopeId");
        }
        tenantId = tenantId.trim();
        scopeId = scopeId.trim();
    }
}
