package org.pipelineframework.invocation;

import java.util.List;
import java.util.Objects;

/** Immutable invocation identity and return positions retained across durable suspension. */
public record PipelineInvocationPath(int version, List<Frame> frames, int stepIndex) {
    public PipelineInvocationPath(List<Frame> frames) {
        this(2, frames, -1);
    }

    public PipelineInvocationPath {
        frames = List.copyOf(Objects.requireNonNull(frames, "frames"));
        if (version != 2) {
            throw new IllegalArgumentException("Unsupported invocation continuation version: " + version);
        }
        if (stepIndex < -1) {
            throw new IllegalArgumentException("Invocation step index must be -1 or non-negative");
        }
    }

    public PipelineInvocationPath atStep(int index) {
        if (index < 0) throw new IllegalArgumentException("Invocation step index must be non-negative");
        return new PipelineInvocationPath(version, frames, index);
    }

    public void requireResumable() {
        if (!frames.isEmpty() && (stepIndex < 0 || frames.stream().anyMatch(frame -> frame.callerStepIndex() < 0))) {
            throw new IllegalStateException("Invocation continuation is missing a suspended step or caller return position");
        }
    }

    public record Frame(String definitionId, String callsiteId, long occurrence, int callerStepIndex, boolean recursive) {
        public Frame(String definitionId, String callsiteId, long occurrence) {
            this(definitionId, callsiteId, occurrence, -1, true);
        }

        public Frame {
            Objects.requireNonNull(definitionId, "definitionId");
            Objects.requireNonNull(callsiteId, "callsiteId");
            if (definitionId.isBlank() || callsiteId.isBlank() || occurrence < 1 || callerStepIndex < -1) {
                throw new IllegalArgumentException("Invocation frames require named definitions, callsites, positive occurrences and valid caller positions");
            }
        }

        public String display() {
            return definitionId + ":" + callsiteId + "#" + occurrence;
        }
    }
}
