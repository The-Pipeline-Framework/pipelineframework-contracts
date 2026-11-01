package org.pipelineframework.invocation;

import java.util.List;
import java.util.Objects;

/** Immutable invocation occurrences retained across a durable execution boundary. */
public record PipelineInvocationPath(List<Frame> frames) {
    public PipelineInvocationPath {
        frames = List.copyOf(Objects.requireNonNull(frames, "frames"));
    }

    public record Frame(String definitionId, String callsiteId, long occurrence) {
        public Frame {
            Objects.requireNonNull(definitionId, "definitionId");
            Objects.requireNonNull(callsiteId, "callsiteId");
            if (definitionId.isBlank() || callsiteId.isBlank() || occurrence < 1) {
                throw new IllegalArgumentException("Invocation frames require named definitions, callsites and positive occurrences");
            }
        }

        public String display() {
            return definitionId + ":" + callsiteId + "#" + occurrence;
        }
    }
}
