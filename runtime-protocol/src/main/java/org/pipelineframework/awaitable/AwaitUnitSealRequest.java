package org.pipelineframework.awaitable;

/** End-of-stream proposal; the Coordinator checks the count against retained admissions. */
public record AwaitUnitSealRequest(AwaitWorkerUnitRequest unit, int expectedItemCount) {
    public AwaitUnitSealRequest {
        java.util.Objects.requireNonNull(unit, "unit");
        if (expectedItemCount < 0) throw new IllegalArgumentException("Expected item count must be nonnegative");
    }
}
