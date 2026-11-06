package org.pipelineframework.orchestrator.release;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Reconstructs and verifies an exact release from its descriptor and environment-owned URI resolvers. */
public final class PipelineReleaseClosureResolver {
    private static final String COMPILED_TRUTH_PREFIX = "META-INF/pipeline/";

    private final Map<PipelineReleaseArtifactUri.Scheme, PipelineReleaseArtifactResolver> artifactResolvers;
    private final PipelineContractReader contractReader;
    private final PipelineReleaseDescriptorValidator validator;

    public PipelineReleaseClosureResolver(
        Map<PipelineReleaseArtifactUri.Scheme, PipelineReleaseArtifactResolver> artifactResolvers,
        PipelineContractReader contractReader
    ) {
        this.artifactResolvers = Map.copyOf(Objects.requireNonNull(artifactResolvers, "artifactResolvers"));
        this.contractReader = Objects.requireNonNull(contractReader, "contractReader");
        this.validator = new PipelineReleaseDescriptorValidator();
    }

    public ResolvedPipelineRelease resolve(PipelineReleaseDescriptor descriptor, Path destination) throws IOException {
        validator.validate(descriptor);
        Path root = prepareEmptyDestination(destination);
        try {
            Path artifactsDirectory = Files.createDirectories(root.resolve("artifacts"));
            List<ResolvedPipelineReleaseArtifact> resolved = new ArrayList<>();
            Path compiledTruthArtifact = root;
            for (int index = 0; index < descriptor.artifacts().size(); index++) {
                PipelineReleaseArtifactDescriptor artifact = descriptor.artifacts().get(index);
                PipelineReleaseArtifactUri uri = PipelineReleaseArtifactUri.parse(artifact.uri());
                PipelineReleaseArtifactResolver resolver = artifactResolvers.get(uri.scheme());
                if (resolver == null) {
                    throw new IllegalArgumentException("No release artifact resolver configured for " + uri.scheme());
                }
                Path file = artifactsDirectory.resolve(String.format("%03d-%s.artifact", index, artifact.artifactId()));
                resolveAndVerify(resolver, artifact, file);
                resolved.add(new ResolvedPipelineReleaseArtifact(artifact, file));
                if (artifact.artifactId().equals(descriptor.compiledTruthArtifactId())) {
                    compiledTruthArtifact = file;
                }
            }

            Path compiledTruthDirectory = Files.createDirectories(root.resolve("compiled-truth"));
            String packagePath = descriptor.deployment() == null ? "." : descriptor.deployment().entries().stream()
                .filter(entry -> entry.artifactId().equals(descriptor.compiledTruthArtifactId()))
                .map(PipelineReleaseDeployment.Entry::packagePath).findFirst().orElse(".");
            extractCompiledTruth(compiledTruthArtifact, compiledTruthDirectory, packagePath);
            Path contractFile = compiledTruthDirectory.resolve(PipelineContractDescriptor.RESOURCE_PATH);
            if (!Files.isRegularFile(contractFile)) {
                throw new IllegalArgumentException(
                    "Compiled Truth carrier is missing " + PipelineContractDescriptor.RESOURCE_PATH);
            }
            PipelineContractDescriptor contract = contractReader.read(contractFile);
            validator.validate(descriptor, contract);
            Map<String,Path> packages = new java.util.HashMap<>();
            for (var artifact : resolved) packages.put(artifact.descriptor().artifactId(), artifact.file());
            new PipelineReleasePackageVerifier().verify(descriptor, packages, compiledTruthDirectory.resolve(COMPILED_TRUTH_PREFIX));
            return new ResolvedPipelineRelease(descriptor, contract, resolved, compiledTruthDirectory);
        } catch (IOException | RuntimeException failure) {
            try {
                clearDestination(root);
            } catch (IOException | RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private static Path prepareEmptyDestination(Path destination) throws IOException {
        Path root = Objects.requireNonNull(destination, "destination").toAbsolutePath().normalize();
        Files.createDirectories(root);
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
            if (entries.iterator().hasNext()) {
                throw new IllegalArgumentException("Release destination must be empty: " + root);
            }
        }
        return root;
    }

    private static void clearDestination(Path root) throws IOException {
        Path directoryRoot = root.toRealPath();
        Files.walkFileTree(directoryRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                if (!directory.equals(directoryRoot)) {
                    Files.delete(directory);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void resolveAndVerify(
        PipelineReleaseArtifactResolver resolver,
        PipelineReleaseArtifactDescriptor artifact,
        Path destination
    ) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream source = resolver.open(artifact);
             DigestInputStream verified = new DigestInputStream(source, digest)) {
            Files.copy(verified, destination);
        }
        String actual = "sha256:" + HexFormat.of().formatHex(digest.digest());
        if (!actual.equals(artifact.digest())) {
            Files.deleteIfExists(destination);
            throw new IllegalArgumentException(
                "Release artifact digest mismatch for " + artifact.artifactId());
        }
    }

    /** Extracts the declared carrier location; every other entry is subsequently checked against this truth. */
    private static void extractCompiledTruth(Path archive, Path destination, String packagePath) throws IOException {
        if (packagePath.equals(".")) {
            extractRootCompiledTruth(archive, destination);
            return;
        }
        Path compiledTruthRoot = destination.resolve(COMPILED_TRUTH_PREFIX).normalize();
        Set<Path> extracted = new HashSet<>();
        var resources = PipelineReleasePackageVerifier.compiledTruth(archive, packagePath);
        for (var entry : resources.entrySet()) {
            String name = entry.getKey();
            if (!name.startsWith(COMPILED_TRUTH_PREFIX)) continue;
            Path output = destination.resolve(name).normalize();
            if (name.contains("\\") || !output.startsWith(compiledTruthRoot) || output.equals(compiledTruthRoot))
                throw new IllegalArgumentException("Compiled Truth archive entry escapes destination: " + name);
            if (!extracted.add(output)) throw new IllegalArgumentException("Duplicate Compiled Truth archive entry " + name);
            Files.createDirectories(output.getParent());
            Files.write(output, entry.getValue());
        }
        if (extracted.isEmpty()) {
            throw new IllegalArgumentException("Compiled Truth carrier contains no META-INF/pipeline resources");
        }
    }

    /** Preserves schema-1/root-carrier extraction limits, traversal checks and diagnostics. */
    private static void extractRootCompiledTruth(Path archive, Path destination) throws IOException {
        Path compiledTruthRoot = destination.resolve(COMPILED_TRUTH_PREFIX).normalize();
        Set<Path> extracted = new HashSet<>();
        long extractedBytes = 0;
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(COMPILED_TRUTH_PREFIX)) continue;
                Path output = destination.resolve(name).normalize();
                if (name.contains("\\") || !output.startsWith(compiledTruthRoot) || output.equals(compiledTruthRoot))
                    throw new IllegalArgumentException("Compiled Truth archive entry escapes destination: " + name);
                if (!extracted.add(output)) throw new IllegalArgumentException("Duplicate Compiled Truth archive entry " + name);
                if (extracted.size() > 4096) throw new IllegalArgumentException("Compiled Truth exceeds 4096 resources");
                byte[] bytes;
                try (var input = zip.getInputStream(entry)) { bytes = input.readNBytes(8 * 1024 * 1024 + 1); }
                extractedBytes += bytes.length;
                if (extractedBytes > 8 * 1024 * 1024) throw new IllegalArgumentException("Compiled Truth exceeds 8 MiB");
                Files.createDirectories(output.getParent());
                Files.write(output, bytes);
            }
        }
        if (extracted.isEmpty()) {
            throw new IllegalArgumentException("Compiled Truth carrier contains no META-INF/pipeline resources");
        }
    }


    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest algorithm is unavailable", e);
        }
    }
}
