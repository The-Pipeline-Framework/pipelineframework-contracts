package org.pipelineframework.orchestrator.release;

import java.io.IOException;
import java.io.InputStream;

/** Resolves one canonical release artifact URI without embedding credentials in Release semantics. */
@FunctionalInterface
public interface PipelineReleaseArtifactResolver {
    InputStream open(PipelineReleaseArtifactDescriptor artifact) throws IOException;
}
