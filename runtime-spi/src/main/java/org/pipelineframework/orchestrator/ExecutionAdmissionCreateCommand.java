package org.pipelineframework.orchestrator;

import java.util.Objects;

/**
 * Additive strict creation wrapper. Ingress must verify that the decoded execution input comes from intent bytes.
 * The store atomically binds this original intent and receipt to creation; legacy creation is not a fallback.
 * Release/artifact evidence must be derived from server-verified registered Compiled Truth.
 */
public record ExecutionAdmissionCreateCommand(
    ExecutionCreateCommand execution,
    ExecutionAdmissionIntent intent,
    String releaseMetadataFingerprint,
    String primaryArtifactId,
    String primaryArtifactDigest
) {
    public ExecutionAdmissionCreateCommand {
        execution = Objects.requireNonNull(execution, "execution");
        intent = Objects.requireNonNull(intent, "intent");
        if (!execution.tenantId().equals(intent.tenantId()) || !execution.pipelineId().equals(intent.pipelineId())
            || !execution.contractVersion().equals(intent.contractVersion())
            || !execution.releaseVersion().equals(intent.releaseVersion())) {
            throw new IllegalArgumentException("Execution creation must match the original admission pin and tenant");
        }
        if (releaseMetadataFingerprint == null || !releaseMetadataFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Release metadata fingerprint must be lowercase SHA-256");
        }
        if (primaryArtifactId == null || primaryArtifactId.isBlank()
            || primaryArtifactDigest == null || primaryArtifactDigest.isBlank()) {
            throw new IllegalArgumentException("Verified artifact evidence is required");
        }
    }
}
