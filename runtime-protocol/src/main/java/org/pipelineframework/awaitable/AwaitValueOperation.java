package org.pipelineframework.awaitable;

/** Pure application-runtime operations and the evidence each operation requires. */
public enum AwaitValueOperation {
    NORMALIZE_COMPLETION(false, false, true),
    NORMALIZE_ACKNOWLEDGEMENT(false, true, false),
    PROJECT_COMPLETION(true, false, true),
    NORMALIZE_AND_PROJECT_COMPLETION(true, false, true);

    private final boolean requestRequired;
    private final boolean acknowledgementRequired;
    private final boolean completionRequired;

    AwaitValueOperation(boolean requestRequired, boolean acknowledgementRequired, boolean completionRequired) {
        this.requestRequired = requestRequired;
        this.acknowledgementRequired = acknowledgementRequired;
        this.completionRequired = completionRequired;
    }

    void validateEvidence(boolean request, boolean acknowledgement, boolean completion) {
        if (requestRequired && !request) throw new IllegalArgumentException("Request is required");
        if (acknowledgementRequired && !acknowledgement) throw new IllegalArgumentException("Acknowledgement is required");
        if (completionRequired && !completion) throw new IllegalArgumentException("Completion is required");
    }
}
