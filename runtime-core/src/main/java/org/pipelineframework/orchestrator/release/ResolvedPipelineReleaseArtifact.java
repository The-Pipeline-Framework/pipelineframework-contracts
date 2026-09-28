package org.pipelineframework.orchestrator.release;

import java.nio.file.Path;
import java.util.Objects;

/** One resolved and digest-verified member of a release closure. */
public record ResolvedPipelineReleaseArtifact(PipelineReleaseArtifactDescriptor descriptor, Path file) {
    public ResolvedPipelineReleaseArtifact {
        Objects.requireNonNull(descriptor, "descriptor");
        file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }
}
