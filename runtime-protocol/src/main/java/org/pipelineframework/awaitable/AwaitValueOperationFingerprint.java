package org.pipelineframework.awaitable;

import java.util.Optional;
import org.pipelineframework.orchestrator.SerializedTransitionPayload;

/** Versioned, length-prefixed UTF-8 fingerprint of an Await value operation. */
final class AwaitValueOperationFingerprint {
    private AwaitValueOperationFingerprint() { }

    static String of(AwaitValueOperationRequest request) {
        try {
            var bytes = new java.io.ByteArrayOutputStream();
            var out = new java.io.DataOutputStream(bytes);
            for (String field : new String[] { "tpf-await-value-operation-v1", request.operation().name(), request.operationId(),
                request.tenantId(), request.executionId(), request.interactionId(), request.unitId(), request.stepId(), Long.toString(request.expectedInteractionVersion()),
                request.pipelineId(), request.contractVersion(), request.releaseVersion() }) write(out, field);
            writeOptional(out, request.completionProjectorId());
            writeOptional(out, request.callbackContractFingerprint());
            writePayload(out, request.requestPayload());
            writePayload(out, request.acknowledgementPayload());
            writePayload(out, request.completionPayload());
            write(out, request.actor());
            write(out, Long.toString(request.admittedAtEpochMs()));
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

}
