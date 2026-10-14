package org.pipelineframework.awaitable;

import java.util.Objects;

/** A unit-scoped continuation whose position is opaque outside its store provider. */
public record AwaitInteractionCursor(String tenantId, String unitId, String position) {
    public AwaitInteractionCursor {
        tenantId = required(tenantId, "tenantId");
        unitId = required(unitId, "unitId");
        position = required(position, "position");
        if (position.length() > 8192) {
            throw new IllegalArgumentException("position exceeds cursor bound");
        }
    }

    public void requireScope(String tenant, String unit) {
        if (!tenantId.equals(tenant) || !unitId.equals(unit)) {
            throw new IllegalArgumentException("Await cursor does not belong to tenant and unit");
        }
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
