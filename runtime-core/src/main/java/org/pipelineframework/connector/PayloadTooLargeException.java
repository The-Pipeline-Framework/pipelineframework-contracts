package org.pipelineframework.connector;

/** Declared payload byte limit was exceeded while streaming. */
public final class PayloadTooLargeException extends IllegalArgumentException {
    public PayloadTooLargeException(String message) {
        super(message);
    }
}
