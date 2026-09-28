package org.pipelineframework.orchestrator.release;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.zip.ZipInputStream;

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
        extractCompiledTruth(compiledTruthArtifact, compiledTruthDirectory);
        Path contractFile = compiledTruthDirectory.resolve(PipelineContractDescriptor.RESOURCE_PATH);
        if (!Files.isRegularFile(contractFile)) {
            throw new IllegalArgumentException(
                "Compiled Truth carrier is missing " + PipelineContractDescriptor.RESOURCE_PATH);
        }
        PipelineContractDescriptor contract = contractReader.read(contractFile);
        validator.validate(descriptor, contract);
        return new ResolvedPipelineRelease(descriptor, contract, resolved, compiledTruthDirectory);
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

    private static void extractCompiledTruth(Path archive, Path destination) throws IOException {
        Set<String> extracted = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(COMPILED_TRUTH_PREFIX)) {
                    continue;
                }
                if (!extracted.add(name)) {
                    throw new IllegalArgumentException("Duplicate Compiled Truth archive entry " + name);
                }
                Path output = destination.resolve(name).normalize();
                if (!output.startsWith(destination)) {
                    throw new IllegalArgumentException("Compiled Truth archive entry escapes destination: " + name);
                }
                Files.createDirectories(output.getParent());
                Files.copy(zip, output);
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
