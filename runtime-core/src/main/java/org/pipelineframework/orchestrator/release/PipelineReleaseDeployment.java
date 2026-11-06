package org.pipelineframework.orchestrator.release;

import java.util.List;
import java.util.Map;

/** Physical packaging facts in the immutable Release, never environment values or a semantic step graph. */
public record PipelineReleaseDeployment(String profile, List<Entry> entries, List<Binding> bindings, Check check) {
    public PipelineReleaseDeployment {
        entries = entries == null ? List.of() : List.copyOf(entries);
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
    }

    /** packagePath is '.' for the artifact root or one exact nested JAR; no archive discovery heuristic. */
    public record Entry(String entryId, String artifactId, String runtimeId, String packagePath,
                        String entryClass, String trigger, List<String> bindingIds, String entryPackagePath) {
        public Entry(String entryId, String artifactId, String runtimeId, String packagePath,
                     String entryClass, String trigger, List<String> bindingIds) {
            this(entryId, artifactId, runtimeId, packagePath, entryClass, trigger, bindingIds, packagePath);
        }
        public Entry {
            bindingIds = bindingIds == null ? List.of() : List.copyOf(bindingIds);
            entryPackagePath = entryPackagePath == null ? packagePath : entryPackagePath;
        }
    }

    /** Logical requirement; authorization and resource/secret values belong to the deployment environment. */
    public record Binding(String bindingId, String capability) {}

    /** Payload maps use the existing SerializedTransitionPayload wire envelope. Core does not depend on protocol. */
    public record Check(String entryId, Map<String,String> input, Map<String,String> expectedResult, long timeoutMillis) {
        public Check {
            input = input == null ? Map.of() : Map.copyOf(input);
            expectedResult = expectedResult == null ? Map.of() : Map.copyOf(expectedResult);
        }
    }
}
