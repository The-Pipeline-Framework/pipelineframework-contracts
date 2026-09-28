package org.pipelineframework.orchestrator.release;

import java.util.Arrays;

/** Canonical byte-addressable artifact kinds in one immutable Pipeline Release closure. */
public enum PipelineReleaseArtifactKind {
    JAR("jar", true, true),
    APPLICATION_ARCHIVE("application-archive", true, true),
    NATIVE_BINARY("native-binary", true, false),
    LAMBDA_ZIP("lambda-zip", true, true),
    COMPILED_TRUTH("compiled-truth", false, true),
    CONTAINER_IMAGE("container-image", true, false),
    LAMBDA_IMAGE("lambda-image", true, false);

    private final String wireValue;
    private final boolean deployable;
    private final boolean compiledTruthCarrier;

    PipelineReleaseArtifactKind(String wireValue, boolean deployable, boolean compiledTruthCarrier) {
        this.wireValue = wireValue;
        this.deployable = deployable;
        this.compiledTruthCarrier = compiledTruthCarrier;
    }

    public String wireValue() {
        return wireValue;
    }

    public boolean deployable() {
        return deployable;
    }

    public boolean compiledTruthCarrier() {
        return compiledTruthCarrier;
    }

    public static PipelineReleaseArtifactKind fromWireValue(String value) {
        return Arrays.stream(values())
            .filter(kind -> kind.wireValue.equals(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported release artifact kind " + value));
    }
}
