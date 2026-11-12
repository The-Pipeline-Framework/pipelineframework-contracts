package org.pipelineframework.awaitable;

import java.util.Objects;
import org.pipelineframework.orchestrator.SerializedTransitionPayload;

/**
 * Proposed canonical value from a typed runtime, never a committed completion.
 * The Coordinator matches the operation and digest and validates canonical framing
 * before a conditional commit against the expected interaction snapshot.
 */
public record AwaitValueOperationResult(
    AwaitValueOperation operation,
    String operationId,
    String requestDigest,
    SerializedTransitionPayload value
) {
    public AwaitValueOperationResult {
        Objects.requireNonNull(operation, "operation");
        if (operationId == null || operationId.isBlank() || requestDigest == null || requestDigest.isBlank()) {
            throw new IllegalArgumentException("Operation identity and request digest are required");
        }
        Objects.requireNonNull(value, "value");
    }

    public boolean matches(AwaitValueOperationRequest request) {
        return operation == request.operation() && operationId.equals(request.operationId())
            && requestDigest.equals(request.requestDigest());
    }
}
