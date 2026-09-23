package org.pipelineframework.config.pipeline;

/** An opt-in upper bound on logical source records consumed by one page. */
public record PipelineStepPaging(int maxRecords) {
    public PipelineStepPaging {
        if (maxRecords < 1) {
            throw new IllegalArgumentException("paging.maxRecords must be positive");
        }
    }
}
