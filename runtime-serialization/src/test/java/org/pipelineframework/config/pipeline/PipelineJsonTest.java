package org.pipelineframework.config.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.DescriptorProtos;
import org.junit.jupiter.api.Test;

class PipelineJsonTest {

    @Test
    void releaseSchemaOneKeepsOriginalWireShapeAndSchemaTwoDefaultsEntryPackagePath() throws Exception {
        var mapper = PipelineJson.mapper();
        String legacy = "{\"schemaVersion\":1,\"pipelineId\":\"p\",\"contractVersion\":\"c\",\"releaseVersion\":\"r\",\"compiledTruthArtifactId\":\"a\",\"artifacts\":[]}";
        var loaded = mapper.readValue(legacy, org.pipelineframework.orchestrator.release.PipelineReleaseDescriptor.class);
        assertEquals(mapper.readTree(legacy), mapper.readTree(mapper.writeValueAsBytes(loaded)));
        String entryJson = "{\"entryId\":\"entry\",\"artifactId\":\"app\",\"runtimeId\":\"module\",\"packagePath\":\"lib/app.jar\",\"entryClass\":\"example.Entry\",\"trigger\":\"HTTP\",\"bindingIds\":[]}";
        var entry = mapper.readValue(entryJson, org.pipelineframework.orchestrator.release.PipelineReleaseDeployment.Entry.class);
        assertEquals("lib/app.jar", entry.entryPackagePath());
        var payload = java.util.Map.of("payloadTypeId", "string", "payloadEncoding", "json", "payload", "null");
        var deployment = new org.pipelineframework.orchestrator.release.PipelineReleaseDeployment("profile",
            java.util.List.of(entry), java.util.List.of(),
            new org.pipelineframework.orchestrator.release.PipelineReleaseDeployment.Check("entry", payload, payload, 1000));
        var release = new org.pipelineframework.orchestrator.release.PipelineReleaseDescriptor(2,"p","c","r","a",java.util.List.of(),deployment);
        assertEquals(release, mapper.readValue(mapper.writeValueAsBytes(release), org.pipelineframework.orchestrator.release.PipelineReleaseDescriptor.class));
    }

    @Test
    void serializesProtobufMessagesAsStableJson() throws Exception {
        DescriptorProtos.FileDescriptorSet payload = DescriptorProtos.FileDescriptorSet.newBuilder()
            .addFile(DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("payments.proto")
                .setPackage("org.pipelineframework.csv")
                .build())
            .build();

        String json = PipelineJson.mapper().writeValueAsString(payload);

        assertTrue(json.contains("\"file\""));
        assertTrue(json.contains("\"payments.proto\""));
        assertFalse(json.contains("unknownFields"));
        assertFalse(json.contains("parserForType"));
    }

    @Test
    void valueToTreeUsesProtobufJsonFields() {
        DescriptorProtos.FileDescriptorSet payload = DescriptorProtos.FileDescriptorSet.newBuilder()
            .addFile(DescriptorProtos.FileDescriptorProto.newBuilder().setName("search.proto").build())
            .build();

        var tree = PipelineJson.mapper().valueToTree(payload);

        assertEquals("search.proto", tree.path("file").get(0).path("name").asText());
    }
}
