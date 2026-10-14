package org.pipelineframework.connector;

/** The boundary does not permit the supplied media type. */
public final class PayloadMediaTypeException extends IllegalArgumentException {
    public PayloadMediaTypeException(String message) {
        super(message);
    }
}
