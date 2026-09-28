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
    List<PipelineReleaseArtifactDescriptor> artifacts
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public PipelineReleaseDescriptor {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported pipeline release schemaVersion " + schemaVersion);
        }
        Objects.requireNonNull(pipelineId, "pipelineId");
        Objects.requireNonNull(contractVersion, "contractVersion");
        Objects.requireNonNull(releaseVersion, "releaseVersion");
        Objects.requireNonNull(compiledTruthArtifactId, "compiledTruthArtifactId");
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }
}
