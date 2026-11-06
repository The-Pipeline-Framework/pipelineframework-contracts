package org.pipelineframework.orchestrator.release;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/** Verifies packaged compiler resources and selected Java entry bytes, without executing application code. */
public final class PipelineReleasePackageVerifier {
    private static final String PREFIX = "META-INF/pipeline/";
    private static final int MAX_PACKAGE_BYTES = 64 * 1024 * 1024;
    private static final int MAX_METADATA_BYTES = 8 * 1024 * 1024;
    private static final int MAX_ENTRIES = 100_000;

    public void verify(PipelineReleaseDescriptor release, Map<String,Path> artifacts, Path metadataDirectory) throws IOException {
        if (release.deployment() == null) return; // Schema 1 retains its existing validation contract.
        new PipelineReleaseDescriptorValidator().validateHosted(release);
        Map<String,byte[]> expected = new HashMap<>();
        long metadataSize = 0;
        try (var files = Files.walk(metadataDirectory)) {
            var iterator = files.iterator();
            while (iterator.hasNext()) {
                Path file = iterator.next();
                if (Files.isSymbolicLink(file)) throw new IllegalArgumentException("Compiled Truth contains a symbolic link");
                if (Files.isRegularFile(file)) {
                    try (var input = Files.newInputStream(file)) {
                        byte[] resource = readBounded(input, MAX_METADATA_BYTES);
                        metadataSize += resource.length;
                        if (metadataSize > MAX_METADATA_BYTES || expected.size() >= 4096)
                            throw new IllegalArgumentException("Compiled Truth exceeds supported resource bounds");
                        expected.put(PREFIX + metadataDirectory.relativize(file).toString().replace('\\', '/'), resource);
                    }
                }
            }
        }
        if (!expected.containsKey(PipelineContractDescriptor.RESOURCE_PATH)) throw new IllegalArgumentException("Compiled Truth Contract is missing");
        for (var entry : release.deployment().entries()) {
            var artifact = release.artifacts().stream().filter(a -> a.artifactId().equals(entry.artifactId())).findFirst().orElseThrow();
            var kind = PipelineReleaseArtifactKind.fromWireValue(artifact.kind());
            if (!kind.supportsJavaEntryAssociation())
                throw new IllegalArgumentException("No executing-package association verifier for " + artifact.kind());
            Path file = artifacts.get(entry.artifactId());
            if (file == null) throw new IllegalArgumentException("Missing executing artifact " + entry.artifactId());
            try (var zip = new ZipFile(file.toFile())) {
                validateNames(zip);
                Map<String,byte[]> packagedTruth = readPackage(zip, entry.packagePath(), null);
                byte[] runtimeFacts = packagedTruth.remove(PipelineReleaseRuntimeCapabilities.RESOURCE_PATH);
                compare(expected, packagedTruth);
                var stores = release.deployment().bindings().stream()
                    .filter(binding -> entry.bindingIds().contains(binding.bindingId()))
                    .map(PipelineReleaseDeployment.Binding::capability)
                    .filter(PipelineReleaseRuntimeCapabilities::isStoreCapability).distinct().toList();
                if (!stores.isEmpty()) {
                    if (kind == PipelineReleaseArtifactKind.LAMBDA_ZIP && !entry.packagePath().equals(".")) {
                        runtimeFacts = lambdaRuntimeFacts(zip, runtimeFacts);
                    }
                    var capabilities = PipelineReleaseRuntimeCapabilities.parse(runtimeFacts);
                    stores.forEach(capabilities::requireRecoverable);
                }
                readPackage(zip, entry.entryPackagePath(), entry.entryClass());
            }
        }
    }

    private static byte[] lambdaRuntimeFacts(ZipFile zip, byte[] nestedFacts) throws IOException {
        var rootFacts = zip.getEntry(PipelineReleaseRuntimeCapabilities.RESOURCE_PATH);
        if (rootFacts == null) return nestedFacts;
        if (nestedFacts != null)
            throw new IllegalArgumentException("Lambda ZIP has runtime capability facts in multiple locations");
        try (var input = zip.getInputStream(rootFacts)) {
            return readBounded(input, PipelineReleaseRuntimeCapabilities.MAX_BYTES);
        }
    }

    private static Map<String,byte[]> readPackage(ZipFile outer, String path, String entryClass) throws IOException {
        Map<String,byte[]> resources = new HashMap<>();
        String selectedClass = entryClass == null ? null : entryClass.replace('.', '/') + ".class";
        boolean entryFound = selectedClass == null;
        long metadataSize = 0;
        if (path.equals(".")) {
            var names = outer.entries();
            while (names.hasMoreElements()) {
                var item = names.nextElement();
                if (item.isDirectory()) continue;
                if (selectedClass != null && item.getName().equals(selectedClass)) entryFound = item.getSize() > 0;
                if (selectedClass == null && (item.getName().startsWith(PREFIX) || item.getName().equals(PipelineReleaseRuntimeCapabilities.RESOURCE_PATH))) {
                    try (var input = outer.getInputStream(item)) {
                        byte[] value = readBounded(input, MAX_METADATA_BYTES);
                        metadataSize += value.length;
                        checkMetadataSize(metadataSize, resources.size());
                        resources.put(item.getName(), value);
                    }
                }
            }
        } else {
            var nested = outer.getEntry(path);
            if (nested == null || nested.isDirectory()) throw new IllegalArgumentException("Declared package JAR is absent: " + path);
            byte[] bytes;
            try (var input = outer.getInputStream(nested)) { bytes = readBounded(input, MAX_PACKAGE_BYTES); }
            Set<String> names = new HashSet<>();
            long expanded = 0;
            try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                java.util.zip.ZipEntry item;
                while ((item = zip.getNextEntry()) != null) {
                    validateName(item.getName(), names);
                    byte[] value = readBounded(zip, MAX_PACKAGE_BYTES);
                    expanded += value.length;
                    if (expanded > MAX_PACKAGE_BYTES) throw new IllegalArgumentException("Expanded package JAR exceeds 64 MiB");
                    if (selectedClass == null && !item.isDirectory() && (item.getName().startsWith(PREFIX) || item.getName().equals(PipelineReleaseRuntimeCapabilities.RESOURCE_PATH))) {
                        metadataSize += value.length;
                        checkMetadataSize(metadataSize, resources.size());
                        resources.put(item.getName(), value);
                    }
                    if (item.getName().equals(selectedClass) && value.length > 0) entryFound = true;
                }
            }
        }
        if (!entryFound) throw new IllegalArgumentException("Selected entry class is absent from declared entry package " + path);
        return resources;
    }

    private static void checkMetadataSize(long bytes, int entries) {
        if (bytes > MAX_METADATA_BYTES || entries >= 4096)
            throw new IllegalArgumentException("Packaged Compiled Truth exceeds supported resource bounds");
    }

    private static void validateNames(ZipFile zip) {
        Set<String> names = new HashSet<>();
        var entries = zip.entries();
        while (entries.hasMoreElements()) validateName(entries.nextElement().getName(), names);
    }

    private static void validateName(String name, Set<String> names) {
        if (!names.add(name) || names.size() > MAX_ENTRIES || name.startsWith("/") || name.contains("\\")
            || java.util.Arrays.stream(name.split("/")).anyMatch(p -> p.equals("..") || p.equals(".")))
            throw new IllegalArgumentException("Unsafe or duplicate archive entry " + name);
    }

    private static byte[] readBounded(InputStream input, int maximum) throws IOException {
        byte[] bytes = input.readNBytes(maximum + 1);
        if (bytes.length > maximum) throw new IllegalArgumentException("Package resource exceeds supported size limit");
        return bytes;
    }

    private static void compare(Map<String,byte[]> expected, Map<String,byte[]> actual) {
        if (!expected.keySet().equals(actual.keySet())) throw new IllegalArgumentException("Executing package has a different Compiled Truth resource set");
        for (String name : expected.keySet()) {
            if (!MessageDigest.isEqual(expected.get(name), actual.get(name)))
                throw new IllegalArgumentException("Executing package Compiled Truth differs: " + name);
        }
    }
}
