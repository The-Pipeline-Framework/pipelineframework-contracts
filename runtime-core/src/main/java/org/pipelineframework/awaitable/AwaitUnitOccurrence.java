package org.pipelineframework.awaitable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * Exact semantic scope of one Await cycle. The complete identity is carried as the unit ID
 * across stores, worker imports, parent suspension, completion admissions and release ACKs.
 * The execution owner retains the cause before exposing any externally completable member.
 * Legacy unit IDs have no occurrence coverage; parsing does not manufacture one for them.
 */
public record AwaitUnitOccurrence(
    String tenantId,
    String executionId,
    String identityScope,
    String stepId,
    int stepIndex,
    String occurrenceCause
) {
    private static final String PREFIX = "await2.";

    public AwaitUnitOccurrence {
        tenantId = required(tenantId, "tenantId");
        executionId = required(executionId, "executionId");
        identityScope = required(identityScope, "identityScope");
        stepId = required(stepId, "stepId");
        occurrenceCause = required(occurrenceCause, "occurrenceCause");
        if (stepIndex < 0) throw new IllegalArgumentException("stepIndex must not be negative");
    }

    public String unitId() {
        return PREFIX + encode(tenantId) + "." + encode(executionId) + "." + encode(identityScope)
            + "." + encode(stepId) + "." + stepIndex + "." + encode(occurrenceCause);
    }

    public boolean matches(String tenant, String execution, String step, int index) {
        return tenantId.equals(tenant) && executionId.equals(execution)
            && stepId.equals(step) && stepIndex == index;
    }

    /** Malformed occurrence-qualified IDs fail closed; unqualified retained IDs remain legacy. */
    public static Optional<AwaitUnitOccurrence> fromUnitId(String unitId) {
        Objects.requireNonNull(unitId, "unitId");
        if (!unitId.startsWith(PREFIX)) return Optional.empty();
        String[] components = unitId.substring(PREFIX.length()).split("\\.", -1);
        if (components.length != 6) throw new IllegalArgumentException("Malformed Await occurrence ID");
        try {
            var value = new AwaitUnitOccurrence(decode(components[0]), decode(components[1]),
                decode(components[2]), decode(components[3]), Integer.parseInt(components[4]), decode(components[5]));
            if (!value.unitId().equals(unitId)) throw new IllegalArgumentException("Noncanonical Await occurrence ID");
            return Optional.of(value);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Malformed Await occurrence ID", failure);
        }
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
