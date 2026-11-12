package org.pipelineframework.awaitable;

/** Pure application-runtime operations used by authoritative Await coordination. */
public enum AwaitValueOperation {
    NORMALIZE_COMPLETION,
    NORMALIZE_ACKNOWLEDGEMENT,
    PROJECT_COMPLETION,
    NORMALIZE_AND_PROJECT_COMPLETION
}
