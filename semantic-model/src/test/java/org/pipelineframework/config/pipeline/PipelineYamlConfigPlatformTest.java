package org.pipelineframework.config.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.pipelineframework.config.boundary.PipelineCheckpointConfig;
import org.pipelineframework.config.boundary.PipelineInputBoundaryConfig;
import org.pipelineframework.config.boundary.PipelineOutputBoundaryConfig;
import org.pipelineframework.config.boundary.PipelineSubscriptionConfig;

class PipelineYamlConfigPlatformTest {

    @Test
    void canonicalizesLegacyPlatformAliases() {
        PipelineYamlConfig fromLegacy = new PipelineYamlConfig(
            "org.example",
            "REST",
            "LAMBDA",
            List.of(),
            List.of());
        PipelineYamlConfig fromCanonical = new PipelineYamlConfig(
            "org.example",
            "REST",
            "FUNCTION",
            List.of(),
            List.of());
        PipelineYamlConfig fromStandard = new PipelineYamlConfig(
            "org.example",
            "REST",
            "STANDARD",
            List.of(),
            List.of());

        assertEquals("FUNCTION", fromLegacy.platform());
        assertEquals("FUNCTION", fromCanonical.platform());
        assertEquals("COMPUTE", fromStandard.platform());
    }

    @Test
    void defaultsToComputeWhenPlatformMissing() {
        PipelineYamlConfig config = new PipelineYamlConfig(
            "org.example",
            "REST",
            null,
            List.of(),
            List.of());
        assertEquals("COMPUTE", config.platform());
    }

    @Test
    void snapshotsListsAndKeepsRootDefinitionNonNull() {
        List<PipelineYamlStep> steps = new ArrayList<>();
        List<PipelineYamlAspect> aspects = new ArrayList<>();
        PipelineYamlConfig config = new PipelineYamlConfig("org.example", "LOCAL", null, steps, aspects);
        steps.add(new PipelineYamlStep("Later", "com.example.Input", "com.example.Output"));
        aspects.add(new PipelineYamlAspect("trace", true, "GLOBAL", "BEFORE_STEP", null));
        assertEquals(List.of(), config.stepDefinitions().get("$root"));
        assertEquals(List.of(), config.aspects());

        PipelineYamlConfig missing = new PipelineYamlConfig("org.example", "LOCAL", null, null, null);
        assertEquals(List.of(), missing.stepDefinitions().get("$root"));

        List<String> targets = new ArrayList<>(List.of("First"));
        PipelineYamlAspect aspect = new PipelineYamlAspect("trace", true, "STEPS", "BEFORE_STEP", targets);
        targets.add("Second");
        assertEquals(List.of("First"), aspect.targetSteps());
    }

    @Test
    void withTransportPreservesCheckpointBoundaries() {
        PipelineYamlConfig config = new PipelineYamlConfig(
            "org.example",
            "REST",
            "FUNCTION",
            List.of(),
            List.of(),
            new PipelineInputBoundaryConfig(new PipelineSubscriptionConfig("orders-ready", "com.example.Mapper")),
            new PipelineOutputBoundaryConfig(new PipelineCheckpointConfig("orders-dispatched", List.of("orderId"))));

        PipelineYamlConfig updated = config.withTransport("GRPC");

        assertEquals("GRPC", updated.transport());
        assertEquals(config.input(), updated.input());
        assertEquals(config.output(), updated.output());
    }
}
