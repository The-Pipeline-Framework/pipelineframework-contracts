package org.pipelineframework.orchestrator.release;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.pipelineframework.orchestrator.PipelineBundleCapabilities;
import org.pipelineframework.orchestrator.PipelineBundleStepDescriptor;
import org.pipelineframework.orchestrator.composition.PipelineCompositionDescriptor;

/**
 * Shared generated semantic pipeline contract emitted at build time.
 */
public record PipelineContractDescriptor(
    int schemaVersion,
    String pipelineId,
    String contractVersion,
    String contractHash,
    String platform,
    String transport,
    String module,
    boolean pluginHost,
    String runtimeLayout,
    List<PipelineBundleStepDescriptor> steps,
    PipelineBundleCapabilities capabilities,
    Map<String, Map<String, Object>> canonicalTypes,
    String canonicalCatalogFingerprint,
    PipelineCompositionDescriptor composition,
    List<ImportedPipelineDefinitionDescriptor> importedDefinitions,
    List<Map<String, Object>> capabilityImports,
    List<Map<String, Object>> httpPayloadBoundaries
) {
    public static final int CURRENT_SCHEMA_VERSION = 3;
    public static final String RESOURCE_PATH = "META-INF/pipeline/pipeline-contract.json";
    public static final String DEFAULT_PIPELINE_ID = "local-pipeline";
    public static final String DEFAULT_CONTRACT_VERSION = "local-contract";
    public static final String DEFAULT_CONTRACT_HASH = "local-contract-hash";

    public PipelineContractDescriptor {
        if (schemaVersion < 1 || schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported pipeline contract schemaVersion " + schemaVersion);
        }
        Objects.requireNonNull(pipelineId, "pipelineId");
        Objects.requireNonNull(contractVersion, "contractVersion");
        Objects.requireNonNull(contractHash, "contractHash");
        steps = steps == null ? List.of() : List.copyOf(steps);
        capabilities = capabilities == null ? PipelineBundleCapabilities.defaults() : capabilities;
        canonicalTypes = canonicalTypes == null ? Map.of() : canonicalTypes.entrySet().stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> Map.copyOf(entry.getValue())));
        canonicalCatalogFingerprint = canonicalCatalogFingerprint == null ? "" : canonicalCatalogFingerprint;
        composition = composition == null ? PipelineCompositionDescriptor.empty() : composition;
        importedDefinitions = importedDefinitions == null ? List.of() : List.copyOf(importedDefinitions);
        capabilityImports = capabilityImports == null ? List.of() : capabilityImports.stream()
            .map(PipelineContractDescriptor::immutableMap)
            .toList();
        httpPayloadBoundaries = httpPayloadBoundaries == null ? List.of() : httpPayloadBoundaries.stream()
            .map(boundary -> {
                if (boundary == null) {
                    throw new IllegalArgumentException("httpPayloadBoundaries entries must not be null");
                }
                for (Map.Entry<String, Object> field : boundary.entrySet()) {
                    if (field.getKey() == null || field.getValue() == null) {
                        throw new IllegalArgumentException("httpPayloadBoundaries metadata "
                            + field.getKey() + " must not be null");
                    }
                }
                return immutableMap(boundary);
            })
            .toList();
        if (schemaVersion < 3 && !httpPayloadBoundaries.isEmpty()) {
            throw new IllegalArgumentException("HTTP payload boundaries require contract schemaVersion 3");
        }
        if (schemaVersion < 3 && composition.present()) {
            throw new IllegalArgumentException("Pipeline composition requires contract schemaVersion 3");
        }
        if (schemaVersion < 3 && !importedDefinitions.isEmpty()) {
            throw new IllegalArgumentException("Imported pipeline definitions require contract schemaVersion 3");
        }
        if (schemaVersion < 3 && !capabilityImports.isEmpty()) {
            throw new IllegalArgumentException("Capability imports require contract schemaVersion 3");
        }
    }

    /** Binary/source compatibility for contracts created before HTTP payload metadata was retained. */
    public PipelineContractDescriptor(
        int schemaVersion,
        String pipelineId,
        String contractVersion,
        String contractHash,
        String platform,
        String transport,
        String module,
        boolean pluginHost,
        String runtimeLayout,
        List<PipelineBundleStepDescriptor> steps,
        PipelineBundleCapabilities capabilities,
        Map<String, Map<String, Object>> canonicalTypes,
        String canonicalCatalogFingerprint,
        PipelineCompositionDescriptor composition,
        List<ImportedPipelineDefinitionDescriptor> importedDefinitions,
        List<Map<String, Object>> capabilityImports
    ) {
        this(schemaVersion, pipelineId, contractVersion, contractHash, platform, transport, module, pluginHost,
            runtimeLayout, steps, capabilities, canonicalTypes, canonicalCatalogFingerprint, composition,
            importedDefinitions, capabilityImports, List.of());
    }

    /** Schema-v1/v2 source compatibility; earlier contracts have no composition descriptor. */
    public PipelineContractDescriptor(
        int schemaVersion,
        String pipelineId,
        String contractVersion,
        String contractHash,
        String platform,
        String transport,
        String module,
        boolean pluginHost,
        String runtimeLayout,
        List<PipelineBundleStepDescriptor> steps,
        PipelineBundleCapabilities capabilities
    ) {
        this(schemaVersion, pipelineId, contractVersion, contractHash, platform, transport, module, pluginHost,
            runtimeLayout, steps, capabilities, Map.of(), "", PipelineCompositionDescriptor.empty(), List.of(),
            List.of());
    }

    /** Schema-v2 source compatibility; v2 contracts have no composition descriptor. */
    public PipelineContractDescriptor(
        int schemaVersion,
        String pipelineId,
        String contractVersion,
        String contractHash,
        String platform,
        String transport,
        String module,
        boolean pluginHost,
        String runtimeLayout,
        List<PipelineBundleStepDescriptor> steps,
        PipelineBundleCapabilities capabilities,
        Map<String, Map<String, Object>> canonicalTypes,
        String canonicalCatalogFingerprint
    ) {
        this(schemaVersion, pipelineId, contractVersion, contractHash, platform, transport, module, pluginHost,
            runtimeLayout, steps, capabilities, canonicalTypes, canonicalCatalogFingerprint,
            PipelineCompositionDescriptor.empty(), List.of(), List.of());
    }

    /** Source compatibility for schema-v3 contracts created before imported definition provenance was added. */
    public PipelineContractDescriptor(
        int schemaVersion,
        String pipelineId,
        String contractVersion,
        String contractHash,
        String platform,
        String transport,
        String module,
        boolean pluginHost,
        String runtimeLayout,
        List<PipelineBundleStepDescriptor> steps,
        PipelineBundleCapabilities capabilities,
        Map<String, Map<String, Object>> canonicalTypes,
        String canonicalCatalogFingerprint,
        PipelineCompositionDescriptor composition
    ) {
        this(schemaVersion, pipelineId, contractVersion, contractHash, platform, transport, module, pluginHost,
            runtimeLayout, steps, capabilities, canonicalTypes, canonicalCatalogFingerprint, composition, List.of(),
            List.of());
    }

    /** Source compatibility for schema-v3 contracts created before capability-import provenance was added. */
    public PipelineContractDescriptor(
        int schemaVersion,
        String pipelineId,
        String contractVersion,
        String contractHash,
        String platform,
        String transport,
        String module,
        boolean pluginHost,
        String runtimeLayout,
        List<PipelineBundleStepDescriptor> steps,
        PipelineBundleCapabilities capabilities,
        Map<String, Map<String, Object>> canonicalTypes,
        String canonicalCatalogFingerprint,
        PipelineCompositionDescriptor composition,
        List<ImportedPipelineDefinitionDescriptor> importedDefinitions
    ) {
        this(schemaVersion, pipelineId, contractVersion, contractHash, platform, transport, module, pluginHost,
            runtimeLayout, steps, capabilities, canonicalTypes, canonicalCatalogFingerprint, composition,
            importedDefinitions, List.of());
    }

    public static PipelineContractDescriptor localFallback() {
        return new PipelineContractDescriptor(
            CURRENT_SCHEMA_VERSION,
            DEFAULT_PIPELINE_ID,
            DEFAULT_CONTRACT_VERSION,
            DEFAULT_CONTRACT_HASH,
            null,
            null,
            null,
            false,
            null,
            List.of(),
            PipelineBundleCapabilities.defaults(),
            Map.of(),
            "",
            PipelineCompositionDescriptor.empty(),
            List.of(),
            List.of());
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        if (source == null) return Map.of();
        return source.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
            Map.Entry::getKey,
            entry -> immutableValue(entry.getValue())));
    }

    @SuppressWarnings("unchecked")
    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return immutableMap((Map<String, Object>) map);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(PipelineContractDescriptor::immutableValue).toList();
        }
        return value;
    }
}
