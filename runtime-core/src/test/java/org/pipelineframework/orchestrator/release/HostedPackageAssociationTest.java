package org.pipelineframework.orchestrator.release;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HostedPackageAssociationTest {
    @TempDir Path directory;
    private final PipelineReleasePackageVerifier verifier = new PipelineReleasePackageVerifier();

    @Test void verifiesEveryEntryAgainstCompleteCompilerResourcesInSelectedNestedJar() throws Exception {
        Path metadata = metadata();
        Path zip = archive("valid.zip", "contract-A", true);
        verifier.verify(HostedReleaseContractTest.release(HostedReleaseContractTest.deployment("app", "lib/application.jar")), Map.of("app", zip), metadata);
    }

    @Test void rejectsCorrectlyHashedZipThatContainsAnotherCompiledApplication() throws Exception {
        Path zip = archive("wrong.zip", "contract-B", true);
        var release = HostedReleaseContractTest.release(HostedReleaseContractTest.deployment("app", "lib/application.jar"));
        var digest = "sha256:" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(zip)));
        var artifacts = List.of(new PipelineReleaseArtifactDescriptor("app", "lambda-zip", zip.toUri().toString(), digest, List.of("Transform"), List.of("local")), release.artifacts().get(1));
        var exactPackage = new PipelineReleaseDescriptor(2, release.pipelineId(), release.contractVersion(), release.releaseVersion(), "truth", artifacts, release.deployment());
        var failure = assertThrows(IllegalArgumentException.class, () -> verifier.verify(exactPackage, Map.of("app", zip), metadata()));
        assertTrue(failure.getMessage().contains("Compiled Truth differs"));
    }

    @Test void rejectsMissingSelectedEntryEvenWhenCompilerResourcesMatch() throws Exception {
        Path zip = archive("no-entry.zip", "contract-A", false);
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(
            HostedReleaseContractTest.release(HostedReleaseContractTest.deployment("app", "lib/application.jar")), Map.of("app", zip), metadata()));
    }

    @Test void supportsFrameworkEntryAndApplicationTruthInDifferentJarsOfSameZip() throws Exception {
        Path zip = archive("host.zip", "contract-A", false);
        var original = HostedReleaseContractTest.deployment("app", "lib/application.jar");
        var entries = original.entries().stream().map(e -> new PipelineReleaseDeployment.Entry(
            e.entryId(), e.artifactId(), e.runtimeId(), e.packagePath(), e.entryClass(), e.trigger(), e.bindingIds(), "lib/host.jar")).toList();
        var deployment = new PipelineReleaseDeployment(original.profile(), entries, original.bindings(), original.check());
        verifier.verify(HostedReleaseContractTest.release(deployment), Map.of("app", zip), metadata());
    }

    @Test void rejectsIncompleteCompilerResourceSet() throws Exception {
        Path zip = archive("valid.zip", "contract-A", true);
        Path metadata = metadata();
        Files.writeString(metadata.resolve("runtime-mapping.json"), "{}");
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(
            HostedReleaseContractTest.release(HostedReleaseContractTest.deployment("app", "lib/application.jar")), Map.of("app", zip), metadata));
    }

    private Path metadata() throws Exception {
        Path metadata = Files.createDirectories(directory.resolve("META-INF/pipeline"));
        Files.writeString(metadata.resolve("pipeline-contract.json"), "contract-A");
        return metadata;
    }
    private Path archive(String name, String contract, boolean includeEntry) throws Exception {
        var jarBytes = new ByteArrayOutputStream();
        try (var jar = new ZipOutputStream(jarBytes)) {
            add(jar, PipelineContractDescriptor.RESOURCE_PATH, contract.getBytes());
            if (includeEntry) {
                add(jar, "example/HttpEntry.class", new byte[]{1});
                add(jar, "example/WorkEntry.class", new byte[]{2});
            }
        }
        Path archive = directory.resolve(name);
        var hostBytes = new ByteArrayOutputStream();
        try (var jar = new ZipOutputStream(hostBytes)) {
            add(jar, "example/HttpEntry.class", new byte[]{1});
            add(jar, "example/WorkEntry.class", new byte[]{2});
        }
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            add(zip, "lib/application.jar", jarBytes.toByteArray());
            add(zip, "lib/host.jar", hostBytes.toByteArray());
        }
        return archive;
    }
    private static void add(ZipOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new ZipEntry(name)); out.write(bytes); out.closeEntry();
    }
}
