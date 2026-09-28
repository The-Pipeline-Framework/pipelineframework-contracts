package org.pipelineframework.orchestrator.release;

import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsed canonical location of one immutable release artifact. */
public record PipelineReleaseArtifactUri(
    Scheme scheme,
    String value,
    Optional<Path> file,
    Optional<MavenArtifactCoordinates> maven,
    Optional<String> ociDigest
) {
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9_.+~-]+");
    private static final Pattern OCI = Pattern.compile(
        "oci://[A-Za-z0-9._:-]+/[A-Za-z0-9._~/-]+@(sha256:[0-9a-f]{64})");

    public enum Scheme {
        FILE,
        MAVEN,
        OCI
    }

    public PipelineReleaseArtifactUri {
        Objects.requireNonNull(scheme, "scheme");
        Objects.requireNonNull(value, "value");
        file = Objects.requireNonNull(file, "file");
        maven = Objects.requireNonNull(maven, "maven");
        ociDigest = Objects.requireNonNull(ociDigest, "ociDigest");
    }

    public boolean promotable() {
        return scheme != Scheme.FILE;
    }

    public static PipelineReleaseArtifactUri parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Release artifact uri is required");
        }
        if (value.startsWith("file:")) {
            return parseFile(value);
        }
        if (value.startsWith("maven:")) {
            return parseMaven(value);
        }
        if (value.startsWith("oci:")) {
            return parseOci(value);
        }
        throw new IllegalArgumentException("Unsupported release artifact URI scheme: " + value);
    }

    private static PipelineReleaseArtifactUri parseFile(String value) {
        try {
            URI uri = URI.create(value);
            if (!"file".equals(uri.getScheme()) || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Release file URI must be an absolute canonical file: URI");
            }
            Path path = Path.of(uri);
            if (!path.isAbsolute()) {
                throw new IllegalArgumentException("Release file URI must be absolute");
            }
            String canonical = path.toAbsolutePath().normalize().toUri().toASCIIString();
            if (!canonical.equals(value)) {
                throw new IllegalArgumentException("Release file URI must be canonical: " + canonical);
            }
            return new PipelineReleaseArtifactUri(
                Scheme.FILE, value, Optional.of(path), Optional.empty(), Optional.empty());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid release file URI: " + value, e);
        }
    }

    private static PipelineReleaseArtifactUri parseMaven(String value) {
        if (value.startsWith("maven://")) {
            throw new IllegalArgumentException("Canonical Maven artifact URIs are opaque maven: coordinates");
        }
        List<String> parts = List.of(value.substring("maven:".length()).split(":", -1));
        MavenArtifactCoordinates coordinates = switch (parts.size()) {
            case 3 -> coordinates(parts.get(0), parts.get(1), "jar", "", parts.get(2));
            case 4 -> coordinates(parts.get(0), parts.get(1), parts.get(2), "", parts.get(3));
            case 5 -> coordinates(parts.get(0), parts.get(1), parts.get(2), parts.get(3), parts.get(4));
            default -> throw new IllegalArgumentException(
                "Maven artifact URI must be maven:groupId:artifactId[:extension[:classifier]]:version");
        };
        String canonical = canonicalMavenUri(coordinates);
        if (!canonical.equals(value)) {
            throw new IllegalArgumentException("Release Maven URI must be canonical: " + canonical);
        }
        return new PipelineReleaseArtifactUri(
            Scheme.MAVEN, value, Optional.empty(), Optional.of(coordinates), Optional.empty());
    }

    private static MavenArtifactCoordinates coordinates(
        String groupId,
        String artifactId,
        String extension,
        String classifier,
        String version
    ) {
        if (groupId.isBlank() || Arrays.stream(groupId.split("\\.")).anyMatch(part -> !TOKEN.matcher(part).matches())) {
            throw new IllegalArgumentException("Invalid Maven groupId in release artifact URI");
        }
        requireToken(artifactId, "artifactId");
        requireToken(extension, "extension");
        if (!classifier.isEmpty()) {
            requireToken(classifier, "classifier");
        }
        if (!VERSION.matcher(version).matches()) {
            throw new IllegalArgumentException("Invalid Maven version in release artifact URI");
        }
        return new MavenArtifactCoordinates(groupId, artifactId, extension, classifier, version);
    }

    private static String canonicalMavenUri(MavenArtifactCoordinates coordinates) {
        if (coordinates.extension().equals("jar") && coordinates.classifier().isEmpty()) {
            return "maven:" + coordinates.groupId() + ":" + coordinates.artifactId() + ":" + coordinates.version();
        }
        if (coordinates.classifier().isEmpty()) {
            return "maven:" + coordinates.groupId() + ":" + coordinates.artifactId() + ":"
                + coordinates.extension() + ":" + coordinates.version();
        }
        return "maven:" + coordinates.groupId() + ":" + coordinates.artifactId() + ":"
            + coordinates.extension() + ":" + coordinates.classifier() + ":" + coordinates.version();
    }

    private static PipelineReleaseArtifactUri parseOci(String value) {
        Matcher matcher = OCI.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                "OCI artifact URI must be oci://registry/repository@sha256:<lowercase-hex>");
        }
        return new PipelineReleaseArtifactUri(
            Scheme.OCI, value, Optional.empty(), Optional.empty(), Optional.of(matcher.group(1)));
    }

    private static void requireToken(String value, String name) {
        if (!TOKEN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid Maven " + name + " in release artifact URI");
        }
    }
}
