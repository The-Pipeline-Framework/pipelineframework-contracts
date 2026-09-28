package org.pipelineframework.orchestrator.release;

/** Canonical Maven coordinates carried by a {@code maven:} release artifact URI. */
public record MavenArtifactCoordinates(
    String groupId,
    String artifactId,
    String extension,
    String classifier,
    String version
) {
    public String repositoryPath() {
        String fileName = artifactId + "-" + version
            + (classifier.isEmpty() ? "" : "-" + classifier)
            + "." + extension;
        return groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + fileName;
    }
}
