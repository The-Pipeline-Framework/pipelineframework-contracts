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

    @Test void rejectsMissingWorkEntryEvenWhenHttpEntryAndCompilerResourcesMatch() throws Exception {
        Path zip = archive("missing-work.zip", "contract-A", true, false);
        var failure = assertThrows(IllegalArgumentException.class, () -> verifier.verify(
            HostedReleaseContractTest.release(HostedReleaseContractTest.deployment("app", "lib/application.jar")),
            Map.of("app", zip), metadata()));
        assertTrue(failure.getMessage().contains("Selected entry class is absent"));
    }

    @Test void rejectsEarlierLambdaJarShadowingTheDeclaredEntryPackage() throws Exception {
        Path zip = archive("shadowed.zip", "contract-A", true);
        var original = HostedReleaseContractTest.deployment("app", "lib/application.jar");
        var entries = original.entries().stream().map(e -> new PipelineReleaseDeployment.Entry(
            e.entryId(), e.artifactId(), e.runtimeId(), e.packagePath(), e.entryClass(), e.trigger(), e.bindingIds(), "lib/host.jar")).toList();
        var deployment = new PipelineReleaseDeployment(original.profile(), entries, original.bindings(), original.check());
        var failure = assertThrows(IllegalArgumentException.class,
            () -> verifier.verify(HostedReleaseContractTest.release(deployment), Map.of("app", zip), metadata()));
        assertTrue(failure.getMessage().contains("shadowed by lib/application.jar"), failure.getMessage());
    }

    @Test void rejectsRootClassShadowingTheDeclaredNestedEntry() throws Exception {
        Path zip = archive("root-shadow.zip", "contract-A", true);
        rewrite(zip, "example/HttpEntry.class", ignored -> new byte[]{9});
        var failure = assertThrows(IllegalArgumentException.class, () -> verifier.verify(
            HostedReleaseContractTest.release(HostedReleaseContractTest.deployment("app", "lib/application.jar")),
            Map.of("app", zip), metadata()));
        assertTrue(failure.getMessage().contains("shadowed by ZIP root"), failure.getMessage());
    }

    @Test void rejectsNestedJarWithoutEndOfCentralDirectoryDespiteReadableLocalEntries() throws Exception {
        Path zip = archive("truncated.zip", "contract-A", true);
        rewrite(zip, "lib/application.jar", bytes -> java.util.Arrays.copyOf(bytes, bytes.length - 22));
        assertThrows(java.io.IOException.class, () -> verifier.verify(
            HostedReleaseContractTest.release(HostedReleaseContractTest.deployment("app", "lib/application.jar")),
            Map.of("app", zip), metadata()));
    }

    @Test void rejectsUnreadableRootClassDespitePositiveDirectorySize() throws Exception {
        Path zip = directory.resolve("unreadable-root.zip");
        try (var out = new ZipOutputStream(Files.newOutputStream(zip))) {
            add(out, PipelineContractDescriptor.RESOURCE_PATH, "contract-A".getBytes());
            add(out, "example/HttpEntry.class", new byte[]{1, 2, 3});
            add(out, "example/WorkEntry.class", new byte[]{4, 5, 6});
        }
        byte[] bytes = Files.readAllBytes(zip);
        byte[] name = "example/HttpEntry.class".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length - 30 - name.length; i++) {
            if (bytes[i] == 0x50 && bytes[i+1] == 0x4b && bytes[i+2] == 3 && bytes[i+3] == 4
                    && java.util.Arrays.equals(java.util.Arrays.copyOfRange(bytes, i+30, i+30+name.length), name)) {
                int extraLength = Byte.toUnsignedInt(bytes[i+28]) + (Byte.toUnsignedInt(bytes[i+29]) << 8);
                bytes[i+30+name.length+extraLength] = 7; // Invalid DEFLATE block type; the central size remains positive.
                Files.write(zip, bytes);
                assertThrows(java.io.IOException.class, () -> verifier.verify(
                    HostedReleaseContractTest.release(HostedReleaseContractTest.deployment("app", ".")), Map.of("app", zip), metadata()));
                return;
            }
        }
        fail("Fixture root class header was not found");
    }

    @Test void closureExtractsCompiledTruthFromTheDeclaredNestedLambdaCarrier() throws Exception {
        Path zip = archive("nested-carrier.zip", "contract-A", true);
        var original = HostedReleaseContractTest.release(HostedReleaseContractTest.deployment("app", "lib/application.jar"));
        var artifact = new PipelineReleaseArtifactDescriptor("app", "lambda-zip", zip.toUri().toString(),
            "sha256:" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(zip))),
            List.of("Transform"), List.of("local"));
        var release = new PipelineReleaseDescriptor(2, original.pipelineId(), original.contractVersion(), original.releaseVersion(),
            "app", List.of(artifact), original.deployment());
        var resolver = new PipelineReleaseClosureResolver(Map.of(PipelineReleaseArtifactUri.Scheme.FILE,
            ignored -> Files.newInputStream(zip)), path -> {
                assertEquals("contract-A", Files.readString(path));
                return HostedReleaseContractTest.contract();
            });
        var resolved = resolver.resolve(release, directory.resolve("resolved"));
        assertEquals(HostedReleaseContractTest.contract(), resolved.contract());
        assertEquals("contract-A", Files.readString(resolved.compiledTruthDirectory().resolve(PipelineContractDescriptor.RESOURCE_PATH)));
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
        return archive(name, contract, includeEntry, includeEntry);
    }

    /** Independently controls the two declared entry classes without changing the original both-missing fixture. */
    private Path archive(String name, String contract, boolean httpEntry, boolean workEntry) throws Exception {
        var jarBytes = new ByteArrayOutputStream();
        try (var jar = new ZipOutputStream(jarBytes)) {
            add(jar, PipelineContractDescriptor.RESOURCE_PATH, contract.getBytes());
            if (httpEntry) add(jar, "example/HttpEntry.class", new byte[]{1});
            if (workEntry) add(jar, "example/WorkEntry.class", new byte[]{2});
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
    /** Rewrites only the selected nested artifact, preserving the outer ZIP directory. */
    private static void rewrite(Path file, String name, java.util.function.UnaryOperator<byte[]> change) throws Exception {
        var entries = new java.util.LinkedHashMap<String, byte[]>();
        try (var zip = new java.util.zip.ZipFile(file.toFile())) {
            var iterator = zip.entries();
            while (iterator.hasMoreElements()) {
                var entry = iterator.nextElement();
                try (var input = zip.getInputStream(entry)) {
                    byte[] bytes = input.readAllBytes();
                    entries.put(entry.getName(), entry.getName().equals(name) ? change.apply(bytes) : bytes);
                }
            }
        }
        if (!entries.containsKey(name)) entries.put(name, change.apply(new byte[0]));
        try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
            for (var entry : entries.entrySet()) add(zip, entry.getKey(), entry.getValue());
        }
    }

    private static void add(ZipOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new ZipEntry(name)); out.write(bytes); out.closeEntry();
    }
}
