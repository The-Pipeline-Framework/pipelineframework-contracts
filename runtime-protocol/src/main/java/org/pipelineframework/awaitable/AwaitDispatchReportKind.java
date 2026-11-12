package org.pipelineframework.awaitable;

/** Evidence reported after an already authorized send; no kind authorizes a new send. */
public enum AwaitDispatchReportKind { DISPATCHED, FAILED, COMMAND_SETTLEMENT }
