package org.pipelineframework.orchestrator;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Original strict admission intent. The client key is scoped by tenant and pipeline, never by Release.
 * Input bytes are the exact submitted representation, not a reserialization of the decoded value.
 * Stores must compare the complete value on replay; a fingerprint alone is not conflict authority.
 * Shape validation belongs to the ingress owner; this portable value does not select a transport.
 */
public record ExecutionAdmissionIntent(
    int schemaVersion,
    String tenantId,
    String pipelineId,
    String clientKey,
    String contractVersion,
    String releaseVersion,
    String inputShape,
    String payloadTypeId,
    String payloadEncoding,
    byte[] inputBytes,
    boolean outputStreaming
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public ExecutionAdmissionIntent {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported execution admission intent schema version");
        }
        requireText(tenantId, "tenantId");
        requireText(pipelineId, "pipelineId");
        requireText(clientKey, "clientKey");
        requireText(contractVersion, "contractVersion");
        requireText(releaseVersion, "releaseVersion");
        requireText(inputShape, "inputShape");
        requireText(payloadTypeId, "payloadTypeId");
        requireText(payloadEncoding, "payloadEncoding");
        inputBytes = Objects.requireNonNull(inputBytes, "inputBytes").clone();
    }

    @Override
    public byte[] inputBytes() {
        return inputBytes.clone();
    }

    /** Whether two intents address the same client-key authority, regardless of their requested pins. */
    public boolean sameClientKey(ExecutionAdmissionIntent other) {
        return other != null && tenantId.equals(other.tenantId) && pipelineId.equals(other.pipelineId)
            && clientKey.equals(other.clientKey);
    }

    /**
     * Lowercase SHA-256 of versioned, length-framed UTF-8 metadata and the exact original input bytes.
     * Malformed surrogate metadata fails closed; replacement or Unicode normalization is never performed.
     */
    public String fingerprint() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(schemaVersion);
                for (String value : new String[] {tenantId, pipelineId, clientKey, contractVersion,
                    releaseVersion, inputShape, payloadTypeId, payloadEncoding}) {
                    writeBytes(output, utf8(value));
                }
                writeBytes(output, inputBytes);
                output.writeBoolean(outputStreaming);
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException error) {
            throw new IllegalStateException("Cannot fingerprint execution admission intent", error);
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ExecutionAdmissionIntent value && schemaVersion == value.schemaVersion
            && outputStreaming == value.outputStreaming && tenantId.equals(value.tenantId)
            && pipelineId.equals(value.pipelineId) && clientKey.equals(value.clientKey)
            && contractVersion.equals(value.contractVersion) && releaseVersion.equals(value.releaseVersion)
            && inputShape.equals(value.inputShape) && payloadTypeId.equals(value.payloadTypeId)
            && payloadEncoding.equals(value.payloadEncoding) && Arrays.equals(inputBytes, value.inputBytes);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(schemaVersion, tenantId, pipelineId, clientKey, contractVersion,
            releaseVersion, inputShape, payloadTypeId, payloadEncoding, outputStreaming)
            + Arrays.hashCode(inputBytes);
    }

    @Override
    public String toString() {
        return "ExecutionAdmissionIntent[schemaVersion=" + schemaVersion + ", inputBytes=<redacted>]";
    }

    private static void writeBytes(DataOutputStream output, byte[] value) throws IOException {
        output.writeInt(value.length);
        output.write(value);
    }

    private static byte[] utf8(String value) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(value));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException error) {
            throw new IllegalArgumentException("Execution admission metadata must be valid UTF-8", error);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
