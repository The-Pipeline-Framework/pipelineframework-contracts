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
    String occurrenceCause,
    Optional<String> invocationFingerprint
) {
    private static final String PREFIX = "await2.";

    public AwaitUnitOccurrence(String tenantId, String executionId, String identityScope, String stepId,
        int stepIndex, String occurrenceCause) {
        this(tenantId, executionId, identityScope, stepId, stepIndex, occurrenceCause, Optional.empty());
    }

    public AwaitUnitOccurrence {
        tenantId = required(tenantId, "tenantId");
        executionId = required(executionId, "executionId");
        identityScope = required(identityScope, "identityScope");
        stepId = required(stepId, "stepId");
        occurrenceCause = required(occurrenceCause, "occurrenceCause");
        invocationFingerprint = Objects.requireNonNull(invocationFingerprint, "invocationFingerprint");
        invocationFingerprint.ifPresent(value -> {
            if (!value.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invocation fingerprint must be a canonical SHA-256 digest");
            }
        });
        if (stepIndex < 0) throw new IllegalArgumentException("stepIndex must not be negative");
    }

    public String unitId() {
        return PREFIX + encode(tenantId) + "." + encode(executionId) + "." + encode(identityScope)
            + "." + encode(stepId) + "." + stepIndex + "." + encode(occurrenceCause)
            + invocationFingerprint.map(value -> "." + value).orElse("");
    }

    /** Binds repeated live or durable visits to their immutable recursive invocation occurrence. */
    public AwaitUnitOccurrence withInvocationPath(org.pipelineframework.invocation.PipelineInvocationPath path) {
        Objects.requireNonNull(path, "path");
        var basis = new StringBuilder("invocation-path-v1");
        for (var frame : path.frames()) {
            basis.append(':').append(frame.definitionId().length()).append(':').append(frame.definitionId())
                .append(':').append(frame.callsiteId().length()).append(':').append(frame.callsiteId())
                .append(':').append(frame.occurrence());
        }
        var fingerprint = path.frames().isEmpty() ? Optional.<String>empty() : Optional.of(sha256(basis.toString()));
        return new AwaitUnitOccurrence(tenantId, executionId, identityScope, stepId, stepIndex, occurrenceCause, fingerprint);
    }

    private String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is required for Await continuation identity", unavailable);
        }
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
        if (components.length != 6 && components.length != 7) throw new IllegalArgumentException("Malformed Await occurrence ID");
        try {
            var value = new AwaitUnitOccurrence(decode(components[0]), decode(components[1]),
                decode(components[2]), decode(components[3]), Integer.parseInt(components[4]), decode(components[5]),
                components.length == 7 ? Optional.of(components[6]) : Optional.empty());
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
