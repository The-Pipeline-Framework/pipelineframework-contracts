package org.pipelineframework.orchestrator.release;

import java.io.IOException;
import java.nio.file.Path;

/** Reads a recovered compiler-produced Pipeline Contract. */
@FunctionalInterface
public interface PipelineContractReader {
    PipelineContractDescriptor read(Path contractFile) throws IOException;
}
