package org.pipelineframework.orchestrator.release;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves canonical absolute {@code file:} artifact URIs. */
public final class FilePipelineReleaseArtifactResolver implements PipelineReleaseArtifactResolver {
    @Override
    public InputStream open(PipelineReleaseArtifactDescriptor artifact) throws IOException {
        PipelineReleaseArtifactUri uri = PipelineReleaseArtifactUri.parse(artifact.uri());
        Path path = uri.file().orElseThrow(() -> new IllegalArgumentException(
            "File resolver cannot resolve " + artifact.uri()));
        return Files.newInputStream(path);
    }
}
