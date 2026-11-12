package org.pipelineframework.orchestrator.release;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.pipelineframework.orchestrator.PipelineBundleCapabilities;
import org.pipelineframework.orchestrator.PipelineBundleStepDescriptor;
import org.pipelineframework.orchestrator.composition.PipelineCompositionDescriptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PipelineContractDescriptorTest {

    @Test
    void recordShapeRemainsTheGeneratedContractCompatibilitySurface() {
        assertEquals(
            List.of(
                "schemaVersion",
                "pipelineId",
                "contractVersion",
                "contractHash",
                "platform",
                "transport",
                "module",
                "pluginHost",
                "runtimeLayout",
                "steps",
                "capabilities",
                "canonicalTypes",
                "canonicalCatalogFingerprint",
                "composition",
                "importedDefinitions",
                "capabilityImports",
                "httpPayloadBoundaries",
                "rootBoundary"),
            Arrays.stream(PipelineContractDescriptor.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList());
    }

    @Test
    void canonicalRootBoundaryIsVersionedImmutableAndAbsentForLegacyConstructors() {
        var fields = new LinkedHashMap<String, Object>(Map.of("schemaVersion", 1,
            "inputCanonicalTypeId", "InputAlias", "outputCanonicalTypeId", "OutputAlias"));
        var descriptor = withBoundary(fields);
        fields.clear();
        assertEquals("InputAlias", descriptor.rootBoundary().get("inputCanonicalTypeId"));
        assertThrows(UnsupportedOperationException.class, () -> descriptor.rootBoundary().clear());
        assertEquals(Map.of(), PipelineContractDescriptor.localFallback().rootBoundary());
        assertEquals(Map.of(), withBoundary(null).rootBoundary());
        var mutableVersion = new java.util.concurrent.atomic.AtomicInteger(1);
        var normalized = withBoundary(Map.of("schemaVersion", mutableVersion, "inputCanonicalTypeId", "Input", "outputCanonicalTypeId", "Output"));
        mutableVersion.set(2);
        assertEquals(1, normalized.rootBoundary().get("schemaVersion"));
        assertThrows(IllegalArgumentException.class, () -> new PipelineContractDescriptor(2, "example", "v", "hash", "COMPUTE", "LOCAL", "module", false,
            "monolith", List.of(), PipelineBundleCapabilities.defaults(), Map.of(), "", PipelineCompositionDescriptor.empty(),
            List.of(), List.of(), List.of(), normalized.rootBoundary()));
        for (var invalid : List.<Map<String, Object>>of(
            Map.of("schemaVersion", 2, "inputCanonicalTypeId", "Input", "outputCanonicalTypeId", "Output"),
            Map.of("schemaVersion", 1.0d, "inputCanonicalTypeId", "Input", "outputCanonicalTypeId", "Output"),
            Map.of("schemaVersion", new java.math.BigDecimal("1.0000000000000000001"), "inputCanonicalTypeId", "Input", "outputCanonicalTypeId", "Output"),
            Map.of("schemaVersion", 1, "inputCanonicalTypeId", "Input", "outputCanonicalTypeId", "Output", "unrecognised", "field"),
            Map.of("schemaVersion", 1, "inputCanonicalTypeId", " Input", "outputCanonicalTypeId", "Output"),
            Map.of("schemaVersion", 1, "inputCanonicalTypeId", "Input"))) {
            assertThrows(IllegalArgumentException.class, () -> withBoundary(invalid));
        }
    }

    private static PipelineContractDescriptor withBoundary(Map<String, Object> boundary) {
        return new PipelineContractDescriptor(3, "example", "v", "hash", "COMPUTE", "LOCAL", "module", false,
            "monolith", List.of(), PipelineBundleCapabilities.defaults(), Map.of(), "", PipelineCompositionDescriptor.empty(),
            List.of(), List.of(), List.of(), boundary);
    }

    @Test
    void constructorSnapshotsNestedContractCollections() {
        Map<String, Object> canonicalFields = new LinkedHashMap<>();
        canonicalFields.put("type", "string");
        Map<String, Map<String, Object>> canonicalTypes = new LinkedHashMap<>();
        canonicalTypes.put("example.Name", canonicalFields);

        PipelineContractDescriptor descriptor = new PipelineContractDescriptor(
            PipelineContractDescriptor.CURRENT_SCHEMA_VERSION,
            "example",
            "contract-v1",
            "sha256:contract",
            "LOCAL",
            "LOCAL",
            "example-module",
            false,
            "MONOLITH",
            List.of(new PipelineBundleStepDescriptor(
                0, "step", "service", "ONE_TO_ONE", "input", "output", "Service", "Step", Map.of())),
            PipelineBundleCapabilities.defaults(),
            canonicalTypes,
            "sha256:catalog",
            PipelineCompositionDescriptor.empty(),
            List.of(),
            List.of());

        canonicalFields.put("format", "uuid");
        canonicalTypes.clear();

        assertEquals(Map.of("type", "string"), descriptor.canonicalTypes().get("example.Name"));
        assertThrows(
            UnsupportedOperationException.class,
            () -> descriptor.canonicalTypes().put("other", Map.of()));
    }

    @Test
    void payloadBoundaryCollectionsAreDeeplyImmutableAndRequireSchemaThree() {
        var operations = new java.util.ArrayList<>(List.of("initiate", "part", "complete", "abort"));
        var boundary = new LinkedHashMap<String, Object>();
        boundary.put("name", "csv-input-upload"); boundary.put("operations", operations);
        var source = new java.util.ArrayList<Map<String, Object>>(List.of(boundary));
        var descriptor = new PipelineContractDescriptor(3, "example", "v", "hash", "FUNCTION", "REST",
            "module", false, "PIPELINE_RUNTIME", List.of(), PipelineBundleCapabilities.defaults(),
            Map.of(), "", PipelineCompositionDescriptor.empty(), List.of(), List.of(), source);
        source.clear(); boundary.clear(); operations.clear();
        assertEquals(List.of("initiate", "part", "complete", "abort"), descriptor.httpPayloadBoundaries().getFirst().get("operations"));
        assertThrows(UnsupportedOperationException.class, () -> descriptor.httpPayloadBoundaries().clear());
        assertThrows(UnsupportedOperationException.class, () -> descriptor.httpPayloadBoundaries().getFirst().clear());
        assertThrows(UnsupportedOperationException.class, () -> ((List<?>) descriptor.httpPayloadBoundaries().getFirst().get("operations")).clear());
        assertThrows(IllegalArgumentException.class, () -> new PipelineContractDescriptor(2, "example", "v", "hash",
            "FUNCTION", "REST", "module", false, "PIPELINE_RUNTIME", List.of(), PipelineBundleCapabilities.defaults(),
            Map.of(), "", PipelineCompositionDescriptor.empty(), List.of(), List.of(), descriptor.httpPayloadBoundaries()));
    }

    @Test
    void nullPayloadBoundaryEntriesAndMetadataFailWithTheFieldContext() {
        var nullEntry = new java.util.ArrayList<Map<String, Object>>(); nullEntry.add(null);
        var nullRoute = new LinkedHashMap<String, Object>(); nullRoute.put("route", null);
        var nullScope = new LinkedHashMap<String, Object>(); nullScope.put("authorizationScope", null);
        for (var boundaries : List.<List<Map<String, Object>>>of(nullEntry, List.of(nullRoute), List.of(nullScope))) {
            var failure = assertThrows(IllegalArgumentException.class, () -> new PipelineContractDescriptor(3,
                "example", "v", "hash", "FUNCTION", "REST", "module", false, "PIPELINE_RUNTIME", List.of(),
                PipelineBundleCapabilities.defaults(), Map.of(), "", PipelineCompositionDescriptor.empty(), List.of(), List.of(), boundaries));
            org.junit.jupiter.api.Assertions.assertTrue(failure.getMessage().contains("httpPayloadBoundaries"));
        }
    }

    @Test
    void rejectsContractSchemasNewerThanTheRuntimeUnderstands() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new PipelineContractDescriptor(
                PipelineContractDescriptor.CURRENT_SCHEMA_VERSION + 1,
                "example",
                "contract-v1",
                "sha256:contract",
                "LOCAL",
                "LOCAL",
                "example-module",
                false,
                "MONOLITH",
                List.of(),
                PipelineBundleCapabilities.defaults(),
                Map.of(),
                "sha256:catalog",
                PipelineCompositionDescriptor.empty(),
                List.of(),
                List.of()));
    }
}
