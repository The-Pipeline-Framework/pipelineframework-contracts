package org.pipelineframework.awaitable;

import java.util.Objects;

/** Bounded typed rejection, correlated to a request; never carries application exception details. */
public record AwaitValueOperationFailure(
    AwaitValueOperation operation,
    String operationId,
    String requestDigest,
    Code code
) {
    public enum Code { COMMAND_COMPLETION_REJECTED }

    public AwaitValueOperationFailure {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(code, "code");
        if (operationId == null || operationId.isBlank() || requestDigest == null || requestDigest.isBlank()) {
            throw new IllegalArgumentException("Operation identity and request digest are required");
        }
    }

    public boolean matches(AwaitValueOperationRequest request) {
        return operation == request.operation() && operationId.equals(request.operationId())
            && requestDigest.equals(request.requestDigest());
    }
}
