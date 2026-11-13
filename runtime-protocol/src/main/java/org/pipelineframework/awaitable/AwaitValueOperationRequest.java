package org.pipelineframework.awaitable;

import java.util.Objects;
import java.util.Optional;
import org.pipelineframework.orchestrator.SerializedTransitionPayload;

/**
 * Value-only request to a pinned typed runtime. It confers no completion admission,
 * execution lease, dispatch or durable-write authority. Authentication remains at
 * the Coordinator and authenticated transport boundary; credentials and resume
 * tokens must never be included in this request.
 *
 * <p>Canonical slots carry existing canonical durable envelopes inside the existing
 * serialized transition framing. NORMALIZE operations may receive a transport value
 * in the corresponding slot. The receiver validates its compiled Release, descriptor,
 * projector and canonical bindings before running application code.</p>
 *
 * <p>{@link #requestDigest()} deterministically covers every request field, including
 * scope, pin, expected interaction version, actor/time and exact payload bytes.
 * The returned digest binds a proposed result to the precise admitted request.</p>
 */
public record AwaitValueOperationRequest(
    AwaitValueOperation operation,
    String operationId,
    String tenantId,
    String executionId,
    String interactionId,
    String unitId,
    String stepId,
    long expectedInteractionVersion,
    String pipelineId,
    String contractVersion,
    String releaseVersion,
    Optional<String> completionProjectorId,
    Optional<String> callbackContractFingerprint,
    Optional<SerializedTransitionPayload> requestPayload,
    Optional<SerializedTransitionPayload> acknowledgementPayload,
    Optional<SerializedTransitionPayload> completionPayload,
    String actor,
    long admittedAtEpochMs
) {
    public static final String CAPABILITY = "await-value-operations-v1";

    public AwaitValueOperationRequest {
        Objects.requireNonNull(operation, "operation");
        require(operationId, "operationId");
        require(tenantId, "tenantId");
        require(executionId, "executionId");
        require(interactionId, "interactionId");
        require(unitId, "unitId");
        require(stepId, "stepId");
        require(pipelineId, "pipelineId");
        require(contractVersion, "contractVersion");
        require(releaseVersion, "releaseVersion");
        if (expectedInteractionVersion < 0 || admittedAtEpochMs < 0) {
            throw new IllegalArgumentException("Interaction version and admitted time must be nonnegative");
        }
        completionProjectorId = optionalIdentity(completionProjectorId, "completionProjectorId");
        callbackContractFingerprint = optionalIdentity(callbackContractFingerprint, "callbackContractFingerprint");
        requestPayload = Objects.requireNonNull(requestPayload, "requestPayload");
        acknowledgementPayload = Objects.requireNonNull(acknowledgementPayload, "acknowledgementPayload");
        completionPayload = Objects.requireNonNull(completionPayload, "completionPayload");
        actor = Objects.requireNonNull(actor, "actor");
        switch (operation) {
            case NORMALIZE_ACKNOWLEDGEMENT -> {
                if (acknowledgementPayload.isEmpty()) throw new IllegalArgumentException("Acknowledgement is required");
            }
            case NORMALIZE_COMPLETION -> {
                if (completionPayload.isEmpty()) throw new IllegalArgumentException("Completion is required");
            }
            case PROJECT_COMPLETION, NORMALIZE_AND_PROJECT_COMPLETION -> {
                if (requestPayload.isEmpty() || completionPayload.isEmpty()) {
                    throw new IllegalArgumentException("Request and completion are required for projection");
                }
            }
        }
    }

    /** SHA-256 of versioned, length-prefixed UTF-8 fields in declaration order. */
    public String requestDigest() {
        try {
            var bytes = new java.io.ByteArrayOutputStream();
            var out = new java.io.DataOutputStream(bytes);
            for (String field : new String[] { "tpf-await-value-operation-v1", operation.name(), operationId,
                tenantId, executionId, interactionId, unitId, stepId, Long.toString(expectedInteractionVersion),
                pipelineId, contractVersion, releaseVersion }) write(out, field);
            writeOptional(out, completionProjectorId);
            writeOptional(out, callbackContractFingerprint);
            writePayload(out, requestPayload);
            writePayload(out, acknowledgementPayload);
            writePayload(out, completionPayload);
            write(out, actor);
            write(out, Long.toString(admittedAtEpochMs));
            return "sha256:" + java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (java.nio.charset.CharacterCodingException failure) {
            throw new IllegalArgumentException("Await value operation contains malformed Unicode", failure);
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Unable to fingerprint Await value operation", failure);
        }
    }

    private static void writePayload(java.io.DataOutputStream out, Optional<SerializedTransitionPayload> payload)
        throws java.io.IOException {
        out.writeBoolean(payload.isPresent());
        if (payload.isPresent()) {
            write(out, payload.orElseThrow().payloadTypeId());
            write(out, payload.orElseThrow().payloadEncoding());
            write(out, payload.orElseThrow().payload());
        }
    }

    private static void writeOptional(java.io.DataOutputStream out, Optional<String> value)
        throws java.io.IOException {
        out.writeBoolean(value.isPresent());
        if (value.isPresent()) write(out, value.orElseThrow());
    }

    private static void write(java.io.DataOutputStream out, String value) throws java.io.IOException {
        var bytes = java.nio.charset.StandardCharsets.UTF_8.newEncoder()
            .encode(java.nio.CharBuffer.wrap(value));
        out.writeInt(bytes.remaining());
        out.write(bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.remaining());
    }

    private static Optional<String> optionalIdentity(Optional<String> value, String name) {
        Objects.requireNonNull(value, name).ifPresent(identity -> require(identity, name));
        return value;
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
