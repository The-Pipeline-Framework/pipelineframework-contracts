package org.pipelineframework.orchestrator.release;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Resolves canonical {@code maven:} artifact URIs against an environment-owned repository root. */
public final class MavenRepositoryPipelineReleaseArtifactResolver implements PipelineReleaseArtifactResolver {
    private final Path repositoryRoot;

    public MavenRepositoryPipelineReleaseArtifactResolver(Path repositoryRoot) {
        this.repositoryRoot = Objects.requireNonNull(repositoryRoot, "repositoryRoot").toAbsolutePath().normalize();
    }

    @Override
    public InputStream open(PipelineReleaseArtifactDescriptor artifact) throws IOException {
        PipelineReleaseArtifactUri uri = PipelineReleaseArtifactUri.parse(artifact.uri());
        MavenArtifactCoordinates coordinates = uri.maven().orElseThrow(() -> new IllegalArgumentException(
            "Maven repository resolver cannot resolve " + artifact.uri()));
        Path artifactPath = repositoryRoot.resolve(coordinates.repositoryPath()).normalize();
        if (!artifactPath.startsWith(repositoryRoot)) {
            throw new IllegalArgumentException("Maven artifact path escapes repository root");
        }
        return Files.newInputStream(artifactPath);
    }
}
