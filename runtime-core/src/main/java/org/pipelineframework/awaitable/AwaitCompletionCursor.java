package org.pipelineframework.awaitable;

import java.util.Objects;

/** Tenant-scoped continuation over open completion admissions; only its store understands the position. */
public record AwaitCompletionCursor(String tenantId, String position) {
    public AwaitCompletionCursor {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(position, "position");
        if (tenantId.isBlank() || position.isBlank() || position.length() > 8192) {
            throw new IllegalArgumentException("Await completion cursor requires a tenant and bounded opaque position");
        }
    }

    public void requireScope(String tenant) {
        if (!tenantId.equals(tenant)) {
            throw new IllegalArgumentException("Await completion cursor belongs to another tenant");
        }
    }
}
