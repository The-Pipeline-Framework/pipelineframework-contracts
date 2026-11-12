package org.pipelineframework.orchestrator;

/** Stable route and signature constants for the REST transition-worker boundary. */
public final class RestTransitionWorkerProtocol {

    public static final String RESOURCE_ROOT = "/pipeline/worker";
    public static final String EXECUTE_RESOURCE_PATH = "/transitions/execute";
    public static final String CAPABILITIES_RESOURCE_PATH = "/capabilities";
    public static final String AWAIT_VALUE_OPERATION_RESOURCE_PATH = "/await/value-operations";
    public static final String EXECUTE_PATH = RESOURCE_ROOT + EXECUTE_RESOURCE_PATH;
    public static final String CAPABILITIES_PATH = RESOURCE_ROOT + CAPABILITIES_RESOURCE_PATH;
    public static final String AWAIT_VALUE_OPERATION_PATH = RESOURCE_ROOT + AWAIT_VALUE_OPERATION_RESOURCE_PATH;
    public static final String EXECUTE_METHOD = "POST";
    public static final String CAPABILITIES_METHOD = "GET";
    public static final String AWAIT_VALUE_OPERATION_METHOD = "POST";

    private RestTransitionWorkerProtocol() {
    }
}
