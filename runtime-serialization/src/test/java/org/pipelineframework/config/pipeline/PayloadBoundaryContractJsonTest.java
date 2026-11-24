package org.pipelineframework.config.pipeline;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.pipelineframework.orchestrator.release.PipelineContractDescriptor;
import static org.junit.jupiter.api.Assertions.*;

/** The unmodified descriptor comes from the verified public compiler #34 CSV build. */
class PayloadBoundaryContractJsonTest {
    private byte[] fixture() throws Exception {
        try(var input=getClass().getResourceAsStream("/contracts/csv-function-upload.json")) {
            assertNotNull(input);return input.readAllBytes();
        }
    }
    @Test void acceptsAndRetainsActualPublishedCompilerPayloadBoundaryMetadata() throws Exception {
        byte[] bytes=fixture();
        assertEquals("b35ffa002a0b84f37d5634c33ea73842586ee0c9df103a79b0a54aaa54cb83cf",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        var original=PipelineJson.mapper().readTree(bytes);
        var descriptor=PipelineJson.mapper().readValue(bytes,PipelineContractDescriptor.class);
        var restored=PipelineJson.mapper().valueToTree(descriptor);
        assertEquals(original.get("httpPayloadBoundaries"),restored.get("httpPayloadBoundaries"));
        assertEquals(original.get("contractHash").asText(),descriptor.contractHash());
        assertEquals(original.get("contractVersion").asText(),descriptor.contractVersion());
        assertEquals(1,restored.get("httpPayloadBoundaries").size());
        assertEquals("DELEGATED_S3",restored.at("/httpPayloadBoundaries/0/transfer").asText());
    }
    @Test void retainsExplicitRootBoundaryWithoutChangingLegacyWireShape() throws Exception {
        var original = (com.fasterxml.jackson.databind.node.ObjectNode) PipelineJson.mapper().readTree(fixture());
        original.put("schemaVersion", 3);
        var boundary = original.putObject("rootBoundary");
        boundary.put("schemaVersion", 1);
        boundary.put("inputCanonicalTypeId", "InputAlias");
        boundary.put("outputCanonicalTypeId", "OutputAlias");
        var descriptor = PipelineJson.mapper().treeToValue(original, PipelineContractDescriptor.class);
        assertEquals(boundary, PipelineJson.mapper().valueToTree(descriptor).get("rootBoundary"));
    }
    @Test void stillRejectsUnknownContractFields() throws Exception {
        var original=(com.fasterxml.jackson.databind.node.ObjectNode)PipelineJson.mapper().readTree(fixture());
        // This keeps the negative independent of the missing known-boundary field.
        original.remove("httpPayloadBoundaries");original.put("inventedContractAuthority",true);
        assertThrows(com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException.class,()->PipelineJson.mapper().treeToValue(original,PipelineContractDescriptor.class));
    }
    @Test void existingContractsWithoutBoundariesStillLoad() throws Exception {
        var legacy=(com.fasterxml.jackson.databind.node.ObjectNode)PipelineJson.mapper().readTree(fixture());
        legacy.remove("httpPayloadBoundaries");
        var descriptor=PipelineJson.mapper().treeToValue(legacy,PipelineContractDescriptor.class);
        assertEquals(legacy.get("contractHash").asText(),descriptor.contractHash());
        assertEquals(legacy.get("contractVersion").asText(),descriptor.contractVersion());
        assertTrue(descriptor.httpPayloadBoundaries().isEmpty());
        assertTrue(descriptor.rootBoundary().isEmpty());
        assertFalse(PipelineJson.mapper().valueToTree(descriptor).has("rootBoundary"),
            "absent root boundaries must not alter retained Release fingerprints");
        assertFalse(PipelineJson.mapper().valueToTree(descriptor).has("httpPayloadBoundaries"),
            "legacy wire output must not introduce a field that strict older readers do not know");
        assertFalse(PipelineJson.mapper().valueToTree(descriptor).has("placement"),
            "absent placement metadata must not alter retained Release fingerprints");
    }

    @Test void retainsExplicitPlacementWithoutChangingLegacyWireShape() throws Exception {
        var original = (com.fasterxml.jackson.databind.node.ObjectNode) PipelineJson.mapper().readTree(fixture());
        original.put("schemaVersion", 3);
        var placement = original.putObject("placement");
        placement.put("schemaVersion", 1);
        var invocations = placement.putArray("invocations");
        invocations.addObject()
            .put("callerDefinitionId", "$root")
            .put("callsiteId", "Review")
            .put("targetDefinitionId", "review")
            .put("recursive", false)
            .put("locality", "LOCAL_IN_SEGMENT")
            .put("module", "app")
            .put("runtimeId", "application");
        var segments = placement.putArray("executionSegments");
        var entry = segments.addObject();
        entry.put("segmentId", "entry");
        entry.put("startKind", "ROOT_ENTRY");
        entry.put("resumeAwaitBoundaryId", "");
        entry.put("module", "app");
        entry.put("runtimeId", "application");
        entry.put("canCompleteRoot", true);
        entry.putArray("reachableDefinitions").add("$root").add("review");
        entry.putArray("nextAwaitBoundaries");
        var descriptor = PipelineJson.mapper().treeToValue(original, PipelineContractDescriptor.class);
        assertTrue(descriptor.placement().present());
        assertEquals(placement, PipelineJson.mapper().valueToTree(descriptor).get("placement"));
    }
}
