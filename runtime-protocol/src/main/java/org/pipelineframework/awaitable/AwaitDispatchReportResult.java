package org.pipelineframework.awaitable;

/** Retained observation result, including which side owns final Command value delivery. */
public record AwaitDispatchReportResult(AwaitInteractionRecord record, boolean completedByDispatch) {
    public AwaitDispatchReportResult {
        java.util.Objects.requireNonNull(record, "record");
        if (completedByDispatch && record.status() != AwaitInteractionStatus.COMPLETED) {
            throw new IllegalArgumentException("Dispatch completion delivery requires a completed interaction");
        }
    }
}
