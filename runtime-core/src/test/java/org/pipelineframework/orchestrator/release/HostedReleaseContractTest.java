package org.pipelineframework.orchestrator.release;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HostedReleaseContractTest {
    private final PipelineReleaseDescriptorValidator validator = new PipelineReleaseDescriptorValidator();

    @Test void acceptsMultiplePhysicalEntriesWithoutDuplicatingSemanticOwnership() {
        var release = release(deployment("app", "lib/application.jar"));
        validator.validate(release, contract());
        assertEquals(2, release.deployment().entries().size());
        assertEquals(List.of("Transform"), release.artifacts().getFirst().stepIds());
    }

    @Test void contractValidationRejectsDuplicatedStepOwnershipAcrossValidPhysicalEntries() {
        var original = release(deployment("app", "lib/application.jar"));
        var second = new PipelineReleaseArtifactDescriptor("second", "lambda-zip", "maven:example:second:zip:1",
            "sha256:" + "c".repeat(64), List.of("Transform"), List.of("local"));
        var entries = new java.util.ArrayList<>(original.deployment().entries());
        entries.add(new PipelineReleaseDeployment.Entry("second-http", "second", "app", "lib/application.jar",
            "example.HttpEntry", "HTTP", List.of("route")));
        var deployment = new PipelineReleaseDeployment(original.deployment().profile(), entries,
            original.deployment().bindings(), original.deployment().check());
        var duplicate = new PipelineReleaseDescriptor(2, "sample", "contract", "A", "truth",
            List.of(original.artifacts().getFirst(), second, original.artifacts().get(1)), deployment);
        assertDoesNotThrow(() -> validator.validate(duplicate));
        var failure = assertThrows(IllegalArgumentException.class, () -> validator.validate(duplicate, contract()));
        assertTrue(failure.getMessage().contains("exactly one deployable artifact"), failure.getMessage());
    }

    @Test void legacyReleaseRemainsReadableButIsNotHostedDeployable() {
        var legacy = new PipelineReleaseDescriptor(1, "sample", "contract", "A", "truth", artifacts());
        validator.validate(legacy);
        assertThrows(IllegalArgumentException.class, () -> validator.validateHosted(legacy));
    }

    @Test void rejectsDanglingArtifactsAndUnsafePackagePathsBeforeDelivery() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(release(deployment("absent", "lib/application.jar"))));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(release(deployment("app", "../application.jar"))));
    }

    @Test void rejectsUnsupportedExecutablePackageBeforeArtifactResolution() {
        var original = release(deployment("app", "lib/application.jar"));
        var unsupported = new PipelineReleaseArtifactDescriptor("app", "native-binary", "maven:example:app:bin:1",
            "sha256:"+"a".repeat(64), List.of("Transform"), List.of("local"));
        var release = new PipelineReleaseDescriptor(2, "sample", "contract", "A", "truth",
            List.of(unsupported, original.artifacts().get(1)), original.deployment());
        var effects = new java.util.concurrent.atomic.AtomicInteger();
        var resolver = new PipelineReleaseClosureResolver(Map.of(PipelineReleaseArtifactUri.Scheme.MAVEN,
            artifact -> { effects.incrementAndGet(); throw new java.io.IOException("Must not resolve"); }), ignored -> null);
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(release, java.nio.file.Path.of("unused")));
        assertEquals(0, effects.get());
    }

    @Test void rejectsUnknownBindingsAndCheckEntries() {
        var valid = deployment("app", "lib/application.jar");
        var noBindings = new PipelineReleaseDeployment(valid.profile(), valid.entries(), List.of(), valid.check());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(release(noBindings)));
        var wrongCheck = new PipelineReleaseDeployment.Check("absent", payload("x"), payload("y"), 1000);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(release(
            new PipelineReleaseDeployment(valid.profile(), valid.entries(), valid.bindings(), wrongCheck))));
    }

    /** Matching one-step semantic contract used independently of physical entry count. */
    static PipelineContractDescriptor contract() {
        return new PipelineContractDescriptor(1, "sample", "contract", "hash", "FUNCTION", "REST", "app", false, "monolith",
            List.of(new org.pipelineframework.orchestrator.PipelineBundleStepDescriptor(0, "Transform", "service", "ONE_TO_ONE",
                "input", "output", "example.Transform", "", Map.of())),
            new org.pipelineframework.orchestrator.PipelineBundleCapabilities(true, List.of()));
    }

    static PipelineReleaseDescriptor release(PipelineReleaseDeployment deployment) {
        return new PipelineReleaseDescriptor(2, "sample", "contract", "A", "truth", artifacts(), deployment);
    }
    static List<PipelineReleaseArtifactDescriptor> artifacts() {
        return List.of(new PipelineReleaseArtifactDescriptor("app", "lambda-zip", "maven:example:app:zip:1", "sha256:"+"a".repeat(64), List.of("Transform"), List.of("local")),
            new PipelineReleaseArtifactDescriptor("truth", "compiled-truth", "maven:example:truth:1", "sha256:"+"b".repeat(64), List.of(), List.of()));
    }
    static PipelineReleaseDeployment deployment(String artifact, String path) {
        return new PipelineReleaseDeployment("aws-durable-java-zip-v1", List.of(
            new PipelineReleaseDeployment.Entry("http", artifact, "app", path, "example.HttpEntry", "HTTP", List.of("route")),
            new PipelineReleaseDeployment.Entry("work", artifact, "app", path, "example.WorkEntry", "SQS", List.of("queue"))),
            List.of(new PipelineReleaseDeployment.Binding("route", "HTTP_ROUTE"), new PipelineReleaseDeployment.Binding("queue", "SQS_QUEUE")),
            new PipelineReleaseDeployment.Check("http", payload("x"), payload("y"), 1000));
    }
    static Map<String,String> payload(String body) { return Map.of("payloadTypeId", "java.lang.String", "payloadEncoding", "json", "payload", "\""+body+"\""); }
}
