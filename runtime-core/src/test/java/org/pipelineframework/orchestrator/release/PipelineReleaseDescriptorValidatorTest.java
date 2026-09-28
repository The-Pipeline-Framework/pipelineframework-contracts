package org.pipelineframework.orchestrator.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pipelineframework.orchestrator.PipelineBundleCapabilities;
import org.pipelineframework.orchestrator.PipelineBundleStepDescriptor;
import org.pipelineframework.orchestrator.composition.PipelineCompositionDescriptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineReleaseDescriptorValidatorTest {
    @TempDir
    Path temporaryDirectory;

    private final PipelineReleaseDescriptorValidator validator = new PipelineReleaseDescriptorValidator();

    @Test
    void validatesCompletePromotableClosure() {
        PipelineContractDescriptor contract = contract();
        PipelineReleaseDescriptor descriptor = descriptor(
            "maven:example:orders:1.0.0",
            digest('a'),
            List.of("Validate"),
            List.of("local", "rest"));

        validator.validate(descriptor, contract);
        validator.validatePromotable(descriptor);
    }

    @Test
    void rejectsMissingCarrierIncompleteCoverageAndUnknownCapabilities() {
        PipelineContractDescriptor contract = contract();
        PipelineReleaseDescriptor missingCarrier = new PipelineReleaseDescriptor(
            1, "orders", contract.contractVersion(), "release-1", "missing",
            descriptor("maven:example:orders:1.0.0", digest('a'), List.of("Validate"),
                List.of("local", "rest")).artifacts());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(missingCarrier));

        PipelineReleaseDescriptor missingStep = descriptor(
            "maven:example:orders:1.0.0", digest('a'), List.of(), List.of("local", "rest"));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(missingStep, contract));

        PipelineReleaseDescriptor unknownCapability = descriptor(
            "maven:example:orders:1.0.0", digest('a'), List.of("Validate"), List.of("local", "unknown"));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(unknownCapability, contract));

        PipelineReleaseDescriptor unsafeArtifactId = new PipelineReleaseDescriptor(
            1, "orders", contract.contractVersion(), "release-1", "../orders",
            List.of(new PipelineReleaseArtifactDescriptor(
                "../orders", "jar", "maven:example:orders:1.0.0", digest('a'),
                List.of("Validate"), List.of("local", "rest"))));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(unsafeArtifactId));
    }

    @Test
    void enforcesCanonicalUriProfilesAndPromotionBoundary() {
        assertEquals(
            "example/orders/1.0.0/orders-1.0.0.jar",
            PipelineReleaseArtifactUri.parse("maven:example:orders:1.0.0")
                .maven().orElseThrow().repositoryPath());
        assertThrows(
            IllegalArgumentException.class,
            () -> PipelineReleaseArtifactUri.parse("maven://example:orders:1.0.0"));

        String ociDigest = digest('b');
        PipelineReleaseArtifactUri oci = PipelineReleaseArtifactUri.parse(
            "oci://registry.example/tpf/orders@" + ociDigest);
        assertEquals(ociDigest, oci.ociDigest().orElseThrow());

        String fileUri = temporaryDirectory.resolve("orders.jar").toUri().toASCIIString();
        PipelineReleaseDescriptor local = descriptor(
            fileUri, digest('a'), List.of("Validate"), List.of("local", "rest"));
        assertThrows(IllegalArgumentException.class, () -> validator.validatePromotable(local));

        PipelineReleaseDescriptor snapshot = descriptor(
            "maven:example:orders:1.0.0-SNAPSHOT", digest('a'), List.of("Validate"), List.of("local", "rest"));
        assertThrows(IllegalArgumentException.class, () -> validator.validatePromotable(snapshot));
    }

    @Test
    void resolvesAndVerifiesTheSameDescriptorThroughTwoMavenEnvironments() throws Exception {
        PipelineContractDescriptor contract = contract();
        Path artifact = archive(temporaryDirectory.resolve("orders.jar"));
        String digest = sha256(artifact);
        PipelineReleaseDescriptor descriptor = descriptor(
            "maven:example:orders:1.0.0", digest, List.of("Validate"), List.of("local", "rest"));

        for (String environment : List.of("repository-a", "repository-b")) {
            Path repository = temporaryDirectory.resolve(environment);
            Path repositoryArtifact = repository.resolve("example/orders/1.0.0/orders-1.0.0.jar");
            Files.createDirectories(repositoryArtifact.getParent());
            Files.copy(artifact, repositoryArtifact);
            PipelineReleaseClosureResolver resolver = new PipelineReleaseClosureResolver(
                Map.of(
                    PipelineReleaseArtifactUri.Scheme.MAVEN,
                    new MavenRepositoryPipelineReleaseArtifactResolver(repository)),
                ignored -> contract);

            ResolvedPipelineRelease resolved = resolver.resolve(
                descriptor, temporaryDirectory.resolve(environment + "-resolved"));

            assertEquals(descriptor, resolved.descriptor());
            assertEquals(contract, resolved.contract());
            assertTrue(Files.isRegularFile(resolved.compiledTruthDirectory()
                .resolve(PipelineContractDescriptor.RESOURCE_PATH)));
        }

        Path tamperedRepository = temporaryDirectory.resolve("repository-tampered");
        Path tamperedArtifact = tamperedRepository.resolve("example/orders/1.0.0/orders-1.0.0.jar");
        Files.createDirectories(tamperedArtifact.getParent());
        Files.writeString(tamperedArtifact, "different bytes");
        PipelineReleaseClosureResolver tamperedResolver = new PipelineReleaseClosureResolver(
            Map.of(
                PipelineReleaseArtifactUri.Scheme.MAVEN,
                new MavenRepositoryPipelineReleaseArtifactResolver(tamperedRepository)),
            ignored -> contract);
        assertThrows(IllegalArgumentException.class, () -> tamperedResolver.resolve(
            descriptor, temporaryDirectory.resolve("repository-tampered-resolved")));
    }

    @Test
    void resolvesAnExplicitlyLocalReleaseThroughItsCanonicalFileUri() throws Exception {
        PipelineContractDescriptor contract = contract();
        Path artifact = archive(temporaryDirectory.resolve("local-orders.jar"));
        PipelineReleaseDescriptor descriptor = descriptor(
            artifact.toUri().toASCIIString(), sha256(artifact), List.of("Validate"), List.of("local", "rest"));
        PipelineReleaseClosureResolver resolver = new PipelineReleaseClosureResolver(
            Map.of(PipelineReleaseArtifactUri.Scheme.FILE, new FilePipelineReleaseArtifactResolver()),
            ignored -> contract);

        ResolvedPipelineRelease resolved = resolver.resolve(
            descriptor, temporaryDirectory.resolve("local-resolved"));

        assertEquals(descriptor, resolved.descriptor());
        assertEquals(artifact.toFile().length(), resolved.artifacts().getFirst().file().toFile().length());
    }

    private PipelineReleaseDescriptor descriptor(
        String uri,
        String digest,
        List<String> stepIds,
        List<String> capabilities
    ) {
        return new PipelineReleaseDescriptor(
            1,
            "orders",
            contract().contractVersion(),
            "release-1",
            "orders",
            List.of(new PipelineReleaseArtifactDescriptor(
                "orders", "jar", uri, digest, stepIds, capabilities)));
    }

    private static PipelineContractDescriptor contract() {
        return new PipelineContractDescriptor(
            PipelineContractDescriptor.CURRENT_SCHEMA_VERSION,
            "orders",
            "sha256:" + "c".repeat(64),
            "c".repeat(64),
            "COMPUTE",
            "LOCAL",
            "orders",
            false,
            "MONOLITH",
            List.of(new PipelineBundleStepDescriptor(
                0, "Validate", "service", "ONE_TO_ONE", "input", "output", "ValidateService", "", Map.of())),
            new PipelineBundleCapabilities(true, List.of("rest")),
            Map.of(),
            "",
            PipelineCompositionDescriptor.empty(),
            List.of(),
            List.of());
    }

    private static Path archive(Path path) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            ZipEntry entry = new ZipEntry(PipelineContractDescriptor.RESOURCE_PATH);
            entry.setTime(0L);
            zip.putNextEntry(entry);
            zip.write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return path;
    }

    private static String sha256(Path path) throws Exception {
        return "sha256:" + HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static String digest(char value) {
        return "sha256:" + String.valueOf(value).repeat(64);
    }
}
