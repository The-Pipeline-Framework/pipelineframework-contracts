package org.pipelineframework.orchestrator.release;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Exact locally reconstructed release closure and recovered Compiled Truth. */
public record ResolvedPipelineRelease(
    PipelineReleaseDescriptor descriptor,
    PipelineContractDescriptor contract,
    List<ResolvedPipelineReleaseArtifact> artifacts,
    Path compiledTruthDirectory
) {
    public ResolvedPipelineRelease {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(contract, "contract");
        artifacts = List.copyOf(Objects.requireNonNull(artifacts, "artifacts"));
        compiledTruthDirectory = Objects.requireNonNull(
            compiledTruthDirectory, "compiledTruthDirectory").toAbsolutePath().normalize();
    }
}
