package org.pipelineframework.orchestrator.release;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Compiler-derived physical placement and execution-segment facts pinned into a Pipeline Contract.
 *
 * <p>Records only compiled release facts for the targeted build: per-invocation resolved locality
 * and the branch-/recursion-aware execution segments divided at actual reachable Await boundaries.</p>
 */
public record PipelinePlacementDescriptor(
    int schemaVersion,
    List<InvocationPlacement> invocations,
    List<ExecutionSegment> executionSegments
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String LOCAL_IN_SEGMENT = "LOCAL_IN_SEGMENT";
    public static final String REMOTE_BOUNDARY = "REMOTE_BOUNDARY";
    public static final String ROOT_ENTRY = "ROOT_ENTRY";
    public static final String AWAIT_RESUME = "AWAIT_RESUME";

    public PipelinePlacementDescriptor {
        invocations = invocations == null ? List.of() : List.copyOf(invocations);
        executionSegments = executionSegments == null ? List.of() : List.copyOf(executionSegments);
        invocations.forEach(item -> Objects.requireNonNull(item, "placement invocation must not be null"));
        executionSegments.forEach(item -> Objects.requireNonNull(item, "execution segment must not be null"));
        boolean hasEntries = !invocations.isEmpty() || !executionSegments.isEmpty();
        if (!hasEntries) {
            if (schemaVersion != 0 && schemaVersion != CURRENT_SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported pipeline placement schemaVersion " + schemaVersion);
            }
            schemaVersion = 0;
        } else {
            if (schemaVersion != CURRENT_SCHEMA_VERSION) {
                throw new IllegalArgumentException(
                    "Pipeline placement requires schemaVersion " + CURRENT_SCHEMA_VERSION);
            }
            Set<String> segmentIds = new LinkedHashSet<>();
            Set<String> resumeAwaitIds = new LinkedHashSet<>();
            for (ExecutionSegment segment : executionSegments) {
                if (!segmentIds.add(segment.segmentId())) {
                    throw new IllegalArgumentException("Duplicate execution segment id: " + segment.segmentId());
                }
                if (AWAIT_RESUME.equals(segment.startKind())) {
                    if (!resumeAwaitIds.add(segment.resumeAwaitBoundaryId())) {
                        throw new IllegalArgumentException(
                            "Duplicate resume segment for await boundary: " + segment.resumeAwaitBoundaryId());
                    }
                }
            }
            for (ExecutionSegment segment : executionSegments) {
                for (String nextAwait : segment.nextAwaitBoundaries()) {
                    if (!resumeAwaitIds.contains(nextAwait)) {
                        throw new IllegalArgumentException(
                            "Execution segment '" + segment.segmentId()
                                + "' references unknown await boundary '" + nextAwait + "'");
                    }
                }
            }
        }
    }

    public static PipelinePlacementDescriptor empty() {
        return new PipelinePlacementDescriptor(0, List.of(), List.of());
    }

    public boolean present() {
        return !invocations.isEmpty() || !executionSegments.isEmpty();
    }

    /** Distinct compiled modules owning execution segments or invocations in this placement. */
    public Set<String> deployedModules() {
        Set<String> modules = new LinkedHashSet<>();
        for (ExecutionSegment segment : executionSegments) {
            if (!segment.module().isEmpty()) {
                modules.add(segment.module());
            }
        }
        for (InvocationPlacement invocation : invocations) {
            if (!invocation.module().isEmpty()) {
                modules.add(invocation.module());
            }
        }
        return Set.copyOf(modules);
    }

    public record InvocationPlacement(
        String callerDefinitionId,
        String callsiteId,
        String targetDefinitionId,
        boolean recursive,
        String locality,
        String module,
        String runtimeId
    ) {
        public InvocationPlacement {
            callerDefinitionId = requireText(callerDefinitionId, "callerDefinitionId");
            callsiteId = requireText(callsiteId, "callsiteId");
            targetDefinitionId = requireText(targetDefinitionId, "targetDefinitionId");
            locality = requireText(locality, "locality");
            if (!LOCAL_IN_SEGMENT.equals(locality) && !REMOTE_BOUNDARY.equals(locality)) {
                throw new IllegalArgumentException("Unsupported invocation locality: " + locality);
            }
            module = normalizeOptional(module);
            runtimeId = normalizeOptional(runtimeId);
        }
    }

    public record ExecutionSegment(
        String segmentId,
        String startKind,
        String resumeAwaitBoundaryId,
        String module,
        String runtimeId,
        boolean canCompleteRoot,
        List<String> reachableDefinitions,
        List<String> nextAwaitBoundaries
    ) {
        public ExecutionSegment {
            segmentId = requireText(segmentId, "segmentId");
            startKind = requireText(startKind, "startKind");
            if (!ROOT_ENTRY.equals(startKind) && !AWAIT_RESUME.equals(startKind)) {
                throw new IllegalArgumentException("Unsupported execution segment startKind: " + startKind);
            }
            resumeAwaitBoundaryId = normalizeOptional(resumeAwaitBoundaryId);
            if (ROOT_ENTRY.equals(startKind) && !resumeAwaitBoundaryId.isEmpty()) {
                throw new IllegalArgumentException("ROOT_ENTRY segment must not declare resumeAwaitBoundaryId");
            }
            if (AWAIT_RESUME.equals(startKind) && resumeAwaitBoundaryId.isEmpty()) {
                throw new IllegalArgumentException("AWAIT_RESUME segment requires resumeAwaitBoundaryId");
            }
            module = normalizeOptional(module);
            runtimeId = normalizeOptional(runtimeId);
            reachableDefinitions = copyValidatedTexts(reachableDefinitions, "reachableDefinitions");
            nextAwaitBoundaries = copyValidatedTexts(nextAwaitBoundaries, "nextAwaitBoundaries");
        }
    }

    private static List<String> copyValidatedTexts(List<String> values, String field) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            unique.add(requireText(value, field));
        }
        return List.copyOf(unique);
    }

    private static String normalizeOptional(String value) {
        return value == null ? "" : value.strip();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Pipeline placement " + field + " is required");
        }
        String trimmed = value.strip();
        if (!trimmed.equals(value)) {
            throw new IllegalArgumentException("Pipeline placement " + field + " must not have leading or trailing whitespace");
        }
        return trimmed;
    }
}
