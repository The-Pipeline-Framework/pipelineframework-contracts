package org.pipelineframework.orchestrator;

/** Stable route and header constants for the REST transition-worker boundary. */
public final class RestTransitionWorkerProtocol {

    public static final String RESOURCE_ROOT = "/pipeline/worker";
    public static final String EXECUTE_RESOURCE_PATH = "/transitions/execute";
    public static final String CAPABILITIES_RESOURCE_PATH = "/capabilities";
    public static final String EXECUTE_PATH = RESOURCE_ROOT + EXECUTE_RESOURCE_PATH;
    public static final String CAPABILITIES_PATH = RESOURCE_ROOT + CAPABILITIES_RESOURCE_PATH;
    public static final String EXECUTE_METHOD = "POST";
    public static final String CAPABILITIES_METHOD = "GET";
    /** Paired native capability responses advertise the loaded projection as sha256:&lt;64 lowercase hex&gt;. */
    public static final String PROJECTION_DIGEST_HEADER = "X-TPF-Worker-Projection-Digest";

    private RestTransitionWorkerProtocol() {
    }
}
