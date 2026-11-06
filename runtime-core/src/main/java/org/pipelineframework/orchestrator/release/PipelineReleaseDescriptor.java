package org.pipelineframework.orchestrator.release;

import java.util.List;
import java.util.Objects;

/**
 * Build-produced release closure that pins every artifact and its Compiled Truth carrier.
 */
public record PipelineReleaseDescriptor(
    int schemaVersion,
    String pipelineId,
    String contractVersion,
    String releaseVersion,
    String compiledTruthArtifactId,
    List<PipelineReleaseArtifactDescriptor> artifacts,
    PipelineReleaseDeployment deployment
) {
    /** Default writer remains schema 1 for existing customer-managed consumers. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int DEPLOYMENT_SCHEMA_VERSION = 2;

    /** Preserves the schema-1 constructor for consumers that do not declare hosted packaging facts. */
    public PipelineReleaseDescriptor(int schemaVersion, String pipelineId, String contractVersion,
        String releaseVersion, String compiledTruthArtifactId, List<PipelineReleaseArtifactDescriptor> artifacts) {
        this(schemaVersion, pipelineId, contractVersion, releaseVersion, compiledTruthArtifactId, artifacts, null);
    }

    /** Enforces the schema/deployment pairing and snapshots the immutable artifact closure. */
    public PipelineReleaseDescriptor {
        if (schemaVersion != CURRENT_SCHEMA_VERSION && schemaVersion != DEPLOYMENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported pipeline release schemaVersion " + schemaVersion);
        }
        if (schemaVersion == 1 && deployment != null) {
            throw new IllegalArgumentException("Deployment information requires Release schema 2");
        }
        if (schemaVersion == 2 && deployment == null) {
            throw new IllegalArgumentException("Release schema 2 requires deployment information");
        }
        Objects.requireNonNull(pipelineId, "pipelineId");
        Objects.requireNonNull(contractVersion, "contractVersion");
        Objects.requireNonNull(releaseVersion, "releaseVersion");
        Objects.requireNonNull(compiledTruthArtifactId, "compiledTruthArtifactId");
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }
}
