package org.pipelineframework.orchestrator.release;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.pipelineframework.orchestrator.PipelineBundleCapabilities;
import org.pipelineframework.orchestrator.PipelineBundleStepDescriptor;
import org.pipelineframework.orchestrator.composition.PipelineCompositionDescriptor;

import static org.junit.jupiter.api.Assertions.*;

class PipelinePlacementDescriptorTest {

    @Test
    void validatesBranchAndRecursiveExecutionSegmentGraph() {
        var placement = new PipelinePlacementDescriptor(
            PipelinePlacementDescriptor.CURRENT_SCHEMA_VERSION,
            List.of(
                new PipelinePlacementDescriptor.InvocationPlacement(
                    "$root", "Run review", "review", false,
                    PipelinePlacementDescriptor.LOCAL_IN_SEGMENT, "app", "application"),
                new PipelinePlacementDescriptor.InvocationPlacement(
                    "$root", "Continue", "$root", true,
                    PipelinePlacementDescriptor.LOCAL_IN_SEGMENT, "app", "application")),
            List.of(
                new PipelinePlacementDescriptor.ExecutionSegment(
                    "entry", PipelinePlacementDescriptor.ROOT_ENTRY, "", "app", "application", true,
                    List.of("$root", "review"),
                    List.of("$root/RootAwait", "review@Run review/NestedAwait")),
                new PipelinePlacementDescriptor.ExecutionSegment(
                    "resume:$root/RootAwait", PipelinePlacementDescriptor.AWAIT_RESUME, "$root/RootAwait",
                    "app", "application", true, List.of("$root"), List.of()),
                new PipelinePlacementDescriptor.ExecutionSegment(
                    "resume:review@Run review/NestedAwait", PipelinePlacementDescriptor.AWAIT_RESUME,
                    "review@Run review/NestedAwait", "app", "application", true,
                    List.of("review", "$root"), List.of("review@Run review/NestedAwait"))));

        assertTrue(placement.present());
        assertEquals(Set.of("app"), placement.deployedModules());
    }

    @Test
    void rejectsUnknownNextAwaitBoundaryAndInvalidStartKindFields() {
        assertThrows(IllegalArgumentException.class, () -> new PipelinePlacementDescriptor(
            1,
            List.of(),
            List.of(new PipelinePlacementDescriptor.ExecutionSegment(
                "entry", PipelinePlacementDescriptor.ROOT_ENTRY, "", "app", "application", false,
                List.of("$root"), List.of("missing/Await")))));

        assertThrows(IllegalArgumentException.class, () -> new PipelinePlacementDescriptor.ExecutionSegment(
            "entry", PipelinePlacementDescriptor.ROOT_ENTRY, "unexpected", "app", "application", true,
            List.of("$root"), List.of()));

        assertThrows(IllegalArgumentException.class, () -> new PipelinePlacementDescriptor.ExecutionSegment(
            "resume", PipelinePlacementDescriptor.AWAIT_RESUME, "", "app", "application", true,
            List.of("$root"), List.of()));

        assertThrows(IllegalArgumentException.class, () -> new PipelinePlacementDescriptor.InvocationPlacement(
            "$root", "Call", "child", false, "INVENTED", "app", "application"));
    }

    @Test
    void contractRejectsPlacementBeforeSchema3() {
        var placement = new PipelinePlacementDescriptor(
            1,
            List.of(),
            List.of(new PipelinePlacementDescriptor.ExecutionSegment(
                "entry", PipelinePlacementDescriptor.ROOT_ENTRY, "", "app", "application", true,
                List.of("$root"), List.of())));

        assertThrows(IllegalArgumentException.class, () -> new PipelineContractDescriptor(
            2, "example", "sha256:abc", "abc", "FUNCTION", "REST", "app", false, "PIPELINE_RUNTIME",
            List.of(), PipelineBundleCapabilities.defaults(), Map.of(), "",
            PipelineCompositionDescriptor.empty(), List.of(), List.of(), List.of(), Map.of(), placement));
    }

    @Test
    void releaseValidatorCrossChecksDeploymentEntriesAgainstCompiledPlacementModules() {
        var placement = new PipelinePlacementDescriptor(
            1,
            List.of(),
            List.of(
                new PipelinePlacementDescriptor.ExecutionSegment(
                    "entry", PipelinePlacementDescriptor.ROOT_ENTRY, "", "app", "application", true,
                    List.of("$root"), List.of())));
        var contract = new PipelineContractDescriptor(
            3, "example", "sha256:abc", "abc", "FUNCTION", "REST", "app", false, "PIPELINE_RUNTIME",
            List.of(new PipelineBundleStepDescriptor(0, "Work", "internal", "ONE_TO_ONE",
                "example.In", "example.Out", "example.WorkService", "example.WorkLocalClientStep",
                null, Map.of())),
            PipelineBundleCapabilities.defaults(), Map.of(), "",
            PipelineCompositionDescriptor.empty(), List.of(), List.of(), List.of(), Map.of(), placement);

        String digest = "sha256:" + "a".repeat(64);
        var validator = new PipelineReleaseDescriptorValidator();

        validator.validate(release(digest, "app"), contract);

        var mismatched = assertThrows(IllegalArgumentException.class,
            () -> validator.validate(release(digest, "other-module"), contract));
        assertTrue(mismatched.getMessage().contains("does not match any module in compiled placement"),
            mismatched.getMessage());
    }

    private static PipelineReleaseDescriptor release(String digest, String runtimeId) {
        var deployment = new PipelineReleaseDeployment(
            "aws-durable-java21-rest-worker-v1",
            List.of(new PipelineReleaseDeployment.Entry(
                "worker-entry", "worker", runtimeId, ".", "example.Handler", "AWS_DURABLE_REST_WORKER", List.of())),
            List.of(),
            new PipelineReleaseDeployment.Check(
                "worker-entry",
                Map.of("payloadTypeId", "example.In", "payloadEncoding", "application/json", "payload", "{}"),
                Map.of("payloadTypeId", "example.Out", "payloadEncoding", "application/json", "payload", "{}"),
                1000L));
        return new PipelineReleaseDescriptor(
            PipelineReleaseDescriptor.DEPLOYMENT_SCHEMA_VERSION,
            "example", "sha256:abc", "1.0.0", "worker",
            List.of(new PipelineReleaseArtifactDescriptor(
                "worker", "lambda-zip", "file:///tmp/worker.zip", digest,
                List.of("Work"), List.of("local", "rest", "grpc", "sqs"))),
            deployment);
    }
}
