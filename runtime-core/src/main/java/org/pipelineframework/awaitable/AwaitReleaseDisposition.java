package org.pipelineframework.awaitable;

/** Semantic result of acknowledging an Await unit's consequences in its parent execution. */
public enum AwaitReleaseDisposition {
    CONTINUATION_QUEUED,
    TERMINAL_FAILED
}
