package org.pipelineframework.config.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pipelineframework.config.template.PipelineTemplateConfigLoader;
import org.pipelineframework.config.template.PipelineTemplateConfig;

class PipelinePagingConfigTest {

    @TempDir Path tempDir;

    private static final String CONFIG = """
        version: 1
        appName: Paged source
        basePackage: com.example
        transport: LOCAL
        steps:
          - name: Read
            service: com.example.ReadSource
            cardinality: ONE_TO_MANY
            paging:
              maxRecords: %s
        """;

    private static final String V3_CONFIG = """
        version: 3
        appName: Paged source
        basePackage: com.example
        transport: LOCAL
        types:
          SourceRequest:
            fields: [[source, string]]
          SourceRecord:
            fields: [[value, string]]
        contract:
          input: SourceRequest
          output: SourceRecord
        steps:
          - name: Read
            service: com.example.ReadSource
            cardinality: ONE_TO_MANY
            input: SourceRequest
            output: SourceRecord
            paging:
              maxRecords: %s
        """;

    @Test
    void bothDslViewsPreservePaging() throws Exception {
        String yaml = CONFIG.formatted("250");

        PipelineYamlConfig pipeline = new PipelineYamlConfigLoader().load(new StringReader(yaml));
        var template = loadTemplate(yaml);

        assertEquals(250, pipeline.steps().getFirst().paging().orElseThrow().maxRecords());
        assertEquals(250, template.steps().getFirst().paging().orElseThrow().maxRecords());
    }

    @Test
    void versionThreeNormalizationPreservesPaging() throws Exception {
        String yaml = V3_CONFIG.formatted("375");

        PipelineYamlConfig pipeline = new PipelineYamlConfigLoader().load(new StringReader(yaml));
        var template = loadTemplate(yaml);

        assertEquals(375, pipeline.steps().getFirst().paging().orElseThrow().maxRecords());
        assertEquals(375, template.steps().getFirst().paging().orElseThrow().maxRecords());
    }

    @Test
    void pagingLimitMustBePositive() {
        assertThrows(IllegalArgumentException.class,
            () -> new PipelineYamlConfigLoader().load(new StringReader(CONFIG.formatted("0"))));
        assertThrows(IllegalArgumentException.class,
            () -> loadTemplate(CONFIG.formatted("0")));
    }

    @Test
    void pagingRejectsUnknownProperties() {
        String yaml = CONFIG.formatted("10\n      eager: true");

        assertThrows(IllegalArgumentException.class,
            () -> new PipelineYamlConfigLoader().load(new StringReader(yaml)));
        assertThrows(IllegalArgumentException.class,
            () -> loadTemplate(yaml));
    }

    private PipelineTemplateConfig loadTemplate(String yaml) throws IOException {
        Path config = tempDir.resolve("pipeline-" + Math.abs(yaml.hashCode()) + ".yaml");
        Files.writeString(config, yaml);
        return new PipelineTemplateConfigLoader().load(config);
    }
}
