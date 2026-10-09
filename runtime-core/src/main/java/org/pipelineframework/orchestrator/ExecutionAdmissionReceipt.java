package org.pipelineframework.orchestrator;

/**
 * Immutable proof of one admitted root, retained independently of execution expiry.
 * Release metadata fingerprint is evidence of verified registered Compiled Truth, not a new Release identity.
 * Providers derive artifact evidence from the registered release, never from client assertions.
 */
public record ExecutionAdmissionReceipt(
    int schemaVersion,
    String tenantId,
    String pipelineId,
    String clientKey,
    String contractVersion,
    String releaseVersion,
    String executionId,
    String releaseMetadataFingerprint,
    String primaryArtifactId,
    String primaryArtifactDigest,
    String intentFingerprint,
    long admittedAtEpochMs
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public ExecutionAdmissionReceipt {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported execution admission receipt schema version");
        }
        for (String value : new String[] {tenantId, pipelineId, clientKey, contractVersion,
            releaseVersion, executionId, primaryArtifactId, primaryArtifactDigest}) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Execution admission receipt identity must not be blank");
            }
        }
        requireDigest(releaseMetadataFingerprint);
        requireDigest(intentFingerprint);
        if (admittedAtEpochMs < 0) {
            throw new IllegalArgumentException("admittedAtEpochMs must not be negative");
        }
    }

    /**
     * Checks scoped tenant/pipeline/key/pin and intent-fingerprint correlation only, not full intent equality.
     * Stores must compare the retained actual intent and verified release evidence before accepting replay.
     * This helper never grants redispatch authority.
     */
    public boolean matches(ExecutionAdmissionIntent intent) {
        return intent != null && tenantId.equals(intent.tenantId()) && pipelineId.equals(intent.pipelineId())
            && clientKey.equals(intent.clientKey()) && contractVersion.equals(intent.contractVersion())
            && releaseVersion.equals(intent.releaseVersion()) && intentFingerprint.equals(intent.fingerprint());
    }

    private static void requireDigest(String digest) {
        if (digest == null || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Execution admission fingerprint must be lowercase SHA-256");
        }
    }
}
