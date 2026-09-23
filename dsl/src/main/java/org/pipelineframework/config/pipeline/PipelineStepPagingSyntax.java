package org.pipelineframework.config.pipeline;

import java.util.Map;
import java.util.Optional;

/** Shared strict parser for the opt-in step paging declaration. */
public final class PipelineStepPagingSyntax {
    private PipelineStepPagingSyntax() {
    }

    public static Optional<PipelineStepPaging> read(Map<?, ?> step, String stepName) {
        Object value = step.get("paging");
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof Map<?, ?> paging) || paging.size() != 1
            || !paging.containsKey("maxRecords")) {
            throw new IllegalArgumentException("step '" + stepName
                + "' paging must contain only positive maxRecords");
        }
        Object raw = paging.get("maxRecords");
        if (!(raw instanceof Integer count) || count < 1) {
            throw new IllegalArgumentException("step '" + stepName
                + "' paging.maxRecords must be a positive integer");
        }
        return Optional.of(new PipelineStepPaging(count));
    }
}
