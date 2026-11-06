package org.pipelineframework.orchestrator.release;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.pipelineframework.orchestrator.PipelineBundleStepDescriptor;

/** Shared structural and semantic validation for producer and independent release consumers. */
public final class PipelineReleaseDescriptorValidator {
    private static final Pattern DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");
    private static final Pattern ARTIFACT_ID = Pattern.compile("[A-Za-z0-9_.-]+");

    public void validate(PipelineReleaseDescriptor descriptor) {
        if (descriptor == null) {
            throw new IllegalArgumentException("Pipeline Release Descriptor is required");
        }
        requireText(descriptor.pipelineId(), "pipelineId");
        requireText(descriptor.contractVersion(), "contractVersion");
        requireText(descriptor.releaseVersion(), "releaseVersion");
        String compiledTruthArtifactId = requireText(
            descriptor.compiledTruthArtifactId(), "compiledTruthArtifactId");
        if (descriptor.artifacts().isEmpty()) {
            throw new IllegalArgumentException("Release descriptor must contain at least one artifact");
        }

        Set<String> artifactIds = new HashSet<>();
        PipelineReleaseArtifactDescriptor compiledTruthArtifact = null;
        for (PipelineReleaseArtifactDescriptor artifact : descriptor.artifacts()) {
            if (artifact == null) {
                throw new IllegalArgumentException("Release artifacts must not contain null entries");
            }
            String artifactId = requireText(artifact.artifactId(), "artifactId");
            if (!ARTIFACT_ID.matcher(artifactId).matches()) {
                throw new IllegalArgumentException(
                    "Release artifactId must contain only letters, digits, '.', '_' or '-': " + artifactId);
            }
            if (!artifactIds.add(artifactId)) {
                throw new IllegalArgumentException("Duplicate release artifactId " + artifactId);
            }
            PipelineReleaseArtifactKind kind = PipelineReleaseArtifactKind.fromWireValue(
                requireText(artifact.kind(), "kind"));
            PipelineReleaseArtifactUri uri = PipelineReleaseArtifactUri.parse(artifact.uri());
            validateDigest(artifact.digest());
            validateLocationKind(kind, uri, artifact.digest());
            validateAssociations(artifact);
            if (artifactId.equals(compiledTruthArtifactId)) {
                compiledTruthArtifact = artifact;
                if (!kind.compiledTruthCarrier()) {
                    throw new IllegalArgumentException(
                        "Compiled Truth artifact must be an inspectable JAR or archive");
                }
            }
            if (kind == PipelineReleaseArtifactKind.COMPILED_TRUTH
                && (!artifact.stepIds().isEmpty() || !artifact.capabilities().isEmpty())) {
                throw new IllegalArgumentException(
                    "Compiled Truth-only artifacts must not declare step or capability associations");
            }
        }
        validateDeployment(descriptor);
        if (compiledTruthArtifact == null) {
            throw new IllegalArgumentException(
                "compiledTruthArtifactId does not reference a release artifact: " + compiledTruthArtifactId);
        }
    }

    /** Structural Hosted readiness only. Provider support and binding authorization precede any effects. */
    public void validateHosted(PipelineReleaseDescriptor descriptor) {
        validate(descriptor);
        if (descriptor.deployment() == null) {
            throw new IllegalArgumentException("Hosted delivery requires Release schema 2 deployment information");
        }
    }

    /** Checks package declarations, references and readiness envelopes without resolving external artifacts. */
    private static void validateDeployment(PipelineReleaseDescriptor descriptor) {
        var deployment = descriptor.deployment();
        if (deployment == null) return;
        requireText(deployment.profile(), "deployment profile");
        if (deployment.entries().isEmpty() || deployment.entries().size() > 128)
            throw new IllegalArgumentException("Deployment requires 1 to 128 entries");
        if (deployment.bindings().size() > 256) throw new IllegalArgumentException("Deployment exceeds 256 bindings");
        Set<String> bindingIds = new HashSet<>();
        for (var binding : deployment.bindings()) {
            if (!bindingIds.add(requireText(binding.bindingId(), "bindingId")))
                throw new IllegalArgumentException("Duplicate deployment binding");
            requireText(binding.capability(), "binding capability");
        }
        Set<String> entryIds = new HashSet<>();
        Set<String> executableArtifacts = new HashSet<>();
        for (var entry : deployment.entries()) {
            if (!entryIds.add(requireText(entry.entryId(), "entryId")))
                throw new IllegalArgumentException("Duplicate deployment entry");
            var artifact = descriptor.artifacts().stream().filter(a -> a.artifactId().equals(entry.artifactId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown entry artifact " + entry.artifactId()));
            if (!PipelineReleaseArtifactKind.fromWireValue(artifact.kind()).supportsJavaEntryAssociation())
                throw new IllegalArgumentException("Entry artifact has no supported Java package association verifier");
            executableArtifacts.add(entry.artifactId());
            requireText(entry.runtimeId(), "runtimeId");
            validatePackagePath(entry.packagePath());
            validatePackagePath(entry.entryPackagePath());
            String entryClass = requireText(entry.entryClass(), "entryClass");
            if (!entryClass.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*"))
                throw new IllegalArgumentException("entryClass must be a Java binary class name");
            requireText(entry.trigger(), "entry trigger");
            Set<String> used = new HashSet<>();
            for (String id : entry.bindingIds()) {
                if (!bindingIds.contains(id) || !used.add(id)) throw new IllegalArgumentException("Unknown or duplicate entry binding " + id);
            }
        }
        for (var artifact : descriptor.artifacts()) {
            if (PipelineReleaseArtifactKind.fromWireValue(artifact.kind()).deployable() && !executableArtifacts.contains(artifact.artifactId()))
                throw new IllegalArgumentException("Every executable artifact requires a physical entry");
        }
        var check = deployment.check();
        if (check == null || !entryIds.contains(check.entryId())) throw new IllegalArgumentException("Execution check must reference an entry");
        if (check.timeoutMillis() <= 0 || check.timeoutMillis() > 600_000) throw new IllegalArgumentException("Execution check timeout must be bounded to 600 seconds");
        validatePayload(check.input());
        validatePayload(check.expectedResult());
    }

    /** Accepts the archive root or an exact safe nested JAR path, including lib/application.jar. */
    private static void validatePackagePath(String value) {
        String path = requireText(value, "packagePath");
        if (!path.equals(".") && (!path.endsWith(".jar") || path.startsWith("/") || path.contains("\\")
            || java.util.Arrays.stream(path.split("/", -1)).anyMatch(v -> v.isEmpty() || v.equals(".") || v.equals(".."))))
            throw new IllegalArgumentException("packagePath must be '.' or a safe exact nested JAR path");
    }

    /** Restricts readiness data to the existing bounded serialized transition envelope. */
    private static void validatePayload(Map<String,String> payload) {
        if (!payload.keySet().equals(Set.of("payloadTypeId", "payloadEncoding", "payload")))
            throw new IllegalArgumentException("Check payload must use SerializedTransitionPayload wire fields");
        requireText(payload.get("payloadTypeId"), "payloadTypeId");
        requireText(payload.get("payloadEncoding"), "payloadEncoding");
        if (payload.get("payload").getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 1_048_576)
            throw new IllegalArgumentException("Check payload exceeds 1 MiB");
    }

    public void validate(PipelineReleaseDescriptor descriptor, PipelineContractDescriptor contract) {
        validate(descriptor);
        if (contract == null) {
            throw new IllegalArgumentException("Pipeline Contract is required");
        }
        if (!descriptor.pipelineId().equals(contract.pipelineId())) {
            throw new IllegalArgumentException("Release descriptor pipelineId does not match Pipeline Contract");
        }
        if (!descriptor.contractVersion().equals(contract.contractVersion())) {
            throw new IllegalArgumentException("Release descriptor contractVersion does not match Pipeline Contract");
        }
        if (descriptor.deployment() != null) {
            for (var entry : descriptor.deployment().entries()) {
                if (!entry.runtimeId().equals(contract.module())) {
                    throw new IllegalArgumentException("Entry runtimeId must reference the compiled module identity: " + contract.module());
                }
            }
        }
        if (contract.steps() == null || contract.steps().isEmpty()) {
            throw new IllegalArgumentException("Pipeline Contract must contain at least one step");
        }

        Map<String, Integer> stepPlacements = new HashMap<>();
        Set<String> contractStepIds = new LinkedHashSet<>();
        for (PipelineBundleStepDescriptor step : contract.steps()) {
            String stepId = requireText(step.authoredName(), "Pipeline Contract step authoredName");
            if (!contractStepIds.add(stepId)) {
                throw new IllegalArgumentException("Duplicate Pipeline Contract step authoredName " + stepId);
            }
            stepPlacements.put(stepId, 0);
        }

        Set<String> knownCapabilities = knownCapabilities(contract);
        Set<String> coveredCapabilities = new HashSet<>();
        for (PipelineReleaseArtifactDescriptor artifact : descriptor.artifacts()) {
            PipelineReleaseArtifactKind kind = PipelineReleaseArtifactKind.fromWireValue(artifact.kind());
            if (!kind.deployable()) {
                continue;
            }
            for (String stepId : artifact.stepIds()) {
                if (!stepPlacements.containsKey(stepId)) {
                    throw new IllegalArgumentException(
                        "Unknown stepId " + stepId + " for release artifact " + artifact.artifactId());
                }
                stepPlacements.compute(stepId, (ignored, count) -> count + 1);
            }
            for (String capability : artifact.capabilities()) {
                if (!knownCapabilities.contains(capability)) {
                    throw new IllegalArgumentException(
                        "Unknown capability " + capability + " for release artifact " + artifact.artifactId());
                }
                coveredCapabilities.add(capability);
            }
        }

        stepPlacements.forEach((stepId, placements) -> {
            if (placements != 1) {
                throw new IllegalArgumentException(
                    "Release step " + stepId + " must be associated with exactly one deployable artifact");
            }
        });
        if (!coveredCapabilities.containsAll(knownCapabilities)) {
            Set<String> missing = new LinkedHashSet<>(knownCapabilities);
            missing.removeAll(coveredCapabilities);
            throw new IllegalArgumentException("Release artifacts do not cover Pipeline Contract capabilities " + missing);
        }
    }

    public void validatePromotable(PipelineReleaseDescriptor descriptor) {
        validate(descriptor);
        for (PipelineReleaseArtifactDescriptor artifact : descriptor.artifacts()) {
            PipelineReleaseArtifactUri uri = PipelineReleaseArtifactUri.parse(artifact.uri());
            if (!uri.promotable()) {
                throw new IllegalArgumentException(
                    "Promotable release artifacts must use maven: or oci: URIs: " + artifact.artifactId());
            }
            uri.maven().ifPresent(coordinates -> {
                if (coordinates.version().toUpperCase(Locale.ROOT).endsWith("-SNAPSHOT")) {
                    throw new IllegalArgumentException(
                        "Promotable Maven artifact URIs must not use SNAPSHOT versions: " + artifact.artifactId());
                }
            });
        }
    }

    private static Set<String> knownCapabilities(PipelineContractDescriptor contract) {
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        if (contract.capabilities().localTransitionExecution()) {
            capabilities.add("local");
        }
        capabilities.addAll(contract.capabilities().transitionWorkerProtocols());
        return Set.copyOf(capabilities);
    }

    private static void validateAssociations(PipelineReleaseArtifactDescriptor artifact) {
        Set<String> stepIds = new HashSet<>();
        for (String stepId : artifact.stepIds()) {
            String value = requireText(stepId, "stepId for release artifact " + artifact.artifactId());
            if (!stepIds.add(value)) {
                throw new IllegalArgumentException(
                    "Duplicate stepId " + value + " for release artifact " + artifact.artifactId());
            }
        }
        Set<String> capabilities = new HashSet<>();
        for (String capability : artifact.capabilities()) {
            String value = requireText(capability, "capability for release artifact " + artifact.artifactId());
            if (!capabilities.add(value)) {
                throw new IllegalArgumentException(
                    "Duplicate capability " + value + " for release artifact " + artifact.artifactId());
            }
        }
    }

    private static void validateLocationKind(
        PipelineReleaseArtifactKind kind,
        PipelineReleaseArtifactUri uri,
        String digest
    ) {
        boolean image = kind == PipelineReleaseArtifactKind.CONTAINER_IMAGE
            || kind == PipelineReleaseArtifactKind.LAMBDA_IMAGE;
        if (image != (uri.scheme() == PipelineReleaseArtifactUri.Scheme.OCI)) {
            throw new IllegalArgumentException(
                "OCI image kinds must use oci: URIs and non-image kinds must use file: or maven: URIs");
        }
        uri.ociDigest().ifPresent(value -> {
            if (!value.equals(digest)) {
                throw new IllegalArgumentException("OCI URI digest must match release artifact digest");
            }
        });
    }

    private static void validateDigest(String digest) {
        if (digest == null || !DIGEST.matcher(digest).matches()) {
            throw new IllegalArgumentException("Release artifact digest must use sha256:<64 lowercase hex>");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        if (!value.equals(value.strip())) {
            throw new IllegalArgumentException(name + " must not have leading or trailing whitespace");
        }
        return value;
    }
}
