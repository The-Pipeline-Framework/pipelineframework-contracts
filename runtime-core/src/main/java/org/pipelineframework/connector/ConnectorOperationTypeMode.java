package org.pipelineframework.connector;

/** How a connector operation relates its published Java types to application types. */
public enum ConnectorOperationTypeMode {
    /** The application step must use the published input and output types. */
    EXACT,
    /** The operation validates and projects application-owned input and output types. */
    APPLICATION_PROJECTED
}
