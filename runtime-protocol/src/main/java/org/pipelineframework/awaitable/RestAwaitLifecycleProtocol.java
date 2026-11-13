package org.pipelineframework.awaitable;

/** Stable paths signed by workers when requesting Coordinator-owned Await lifecycle actions. */
public final class RestAwaitLifecycleProtocol {
    public static final String ROOT = "/pipeline/coordinator/await";
    public static final String REGISTRATION_RESOURCE_PATH = "/registrations";
    public static final String DISPATCH_CLAIM_RESOURCE_PATH = "/dispatch-claims";
    public static final String REGISTRATION_PATH = ROOT + REGISTRATION_RESOURCE_PATH;
    public static final String DISPATCH_CLAIM_PATH = ROOT + DISPATCH_CLAIM_RESOURCE_PATH;
    public static final String REPORT_RESOURCE_PATH = "/dispatch-reports";
    public static final String UNIT_PREPARE_RESOURCE_PATH = "/units/prepare";
    public static final String UNIT_SEAL_RESOURCE_PATH = "/units/seal";
    public static final String UNIT_SUSPENSION_RESOURCE_PATH = "/units/suspensions";
    public static final String UNIT_RESUME_RESOURCE_PATH = "/units/resume";
    public static final String REPORT_PATH = ROOT + REPORT_RESOURCE_PATH;
    public static final String UNIT_PREPARE_PATH = ROOT + UNIT_PREPARE_RESOURCE_PATH;
    public static final String UNIT_SEAL_PATH = ROOT + UNIT_SEAL_RESOURCE_PATH;
    public static final String UNIT_SUSPENSION_PATH = ROOT + UNIT_SUSPENSION_RESOURCE_PATH;
    public static final String UNIT_RESUME_PATH = ROOT + UNIT_RESUME_RESOURCE_PATH;
    public static final String METHOD = "POST";
    private RestAwaitLifecycleProtocol() { }
}
