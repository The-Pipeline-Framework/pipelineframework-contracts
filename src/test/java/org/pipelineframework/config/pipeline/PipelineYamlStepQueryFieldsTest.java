package org.pipelineframework.config.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.pipelineframework.config.boundary.PipelineObjectPublishConfig;
import org.pipelineframework.config.boundary.PipelineObjectSourceConfig;
import org.pipelineframework.connector.ConnectorOperationKind;

class PipelineYamlStepQueryFieldsTest {

    @Test
    void queryStepConstructorSetsQueryIdAndCapture() {
        PipelineYamlQueryCapture capture = new PipelineYamlQueryCapture(List.of("customerId"));
        PipelineYamlStep step = new PipelineYamlStep(
            "Load Customer Risk",
            "query",
            "ONE_TO_ONE",
            "com.example.CustomerRiskLookup",
            null,
            "com.example.CustomerRiskSnapshot",
            null,
            null,
            List.of(),
            null,
            "customer-risk-by-id",
            capture);

        assertEquals("Load Customer Risk", step.name());
        assertEquals("query", step.kind());
        assertEquals("customer-risk-by-id", step.queryId());
        assertEquals(List.of("customerId"), step.queryCapture().keyFields());
    }

    @Test
    void defaultsQueryCaptureToEmptyWhenNull() {
        PipelineYamlStep step = new PipelineYamlStep(
            "Load Risk",
            "query",
            "ONE_TO_ONE",
            "com.example.RiskLookup",
            null,
            "com.example.RiskSnapshot",
            null,
            null,
            List.of(),
            null,
            "risk-query",
            null);

        assertNotNull(step.queryCapture());
        assertTrue(step.queryCapture().keyFields().isEmpty());
    }

    @Test
    void nonQueryStepHasNullQueryId() {
        PipelineYamlStep step = new PipelineYamlStep(
            "Process Order",
            "com.example.OrderType",
            "com.example.OrderResult");

        assertNull(step.queryId());
    }

    @Test
    void nonQueryStepHasEmptyQueryCapture() {
        PipelineYamlStep step = new PipelineYamlStep(
            "Process Order",
            "com.example.OrderType",
            "com.example.OrderResult");

        assertNotNull(step.queryCapture());
        assertTrue(step.queryCapture().keyFields().isEmpty());
    }

    @Test
    void defaultsKindToInternalWhenNull() {
        PipelineYamlStep step = new PipelineYamlStep(
            "My Step",
            null,
            "ONE_TO_ONE",
            "com.example.Input",
            null,
            "com.example.Output",
            null,
            null,
            null,
            null,
            null,
            null);

        assertEquals("internal", step.kind());
    }

    @Test
    void defaultsCardinalityToOneToOneWhenNull() {
        PipelineYamlStep step = new PipelineYamlStep(
            "My Step",
            "internal",
            null,
            "com.example.Input",
            null,
            "com.example.Output",
            null,
            null,
            null,
            null,
            null,
            null);

        assertEquals("ONE_TO_ONE", step.cardinality());
    }

    @Test
    void defaultsIdempotencyKeyFieldsToEmptyListWhenNull() {
        PipelineYamlStep step = new PipelineYamlStep(
            "My Step",
            "query",
            "ONE_TO_ONE",
            "com.example.Input",
            null,
            "com.example.Output",
            null,
            null,
            null,
            null,
            "some-query",
            null);

        assertNotNull(step.idempotencyKeyFields());
        assertTrue(step.idempotencyKeyFields().isEmpty());
    }

    @Test
    void twoArgConvenienceConstructorSetsNullQueryFields() {
        PipelineYamlStep step = new PipelineYamlStep(
            "Process Entity",
            "com.example.EntityInput",
            "com.example.EntityOutput");

        assertNull(step.queryId());
        assertNotNull(step.queryCapture());
        assertTrue(step.queryCapture().keyFields().isEmpty());
    }

    @Test
    void snapshotsNestedYamlConfigurationAtEveryBoundary() {
        List<Object> endpoints = new ArrayList<>(List.of("primary"));
        Map<String, Object> config = Map.of("routing", Map.of("endpoints", endpoints));
        PipelineYamlAwaitTransport await = new PipelineYamlAwaitTransport("webhook", config);
        PipelineYamlConnectorBinding binding = new PipelineYamlConnectorBinding("primary", "file", 1, config);
        PipelineYamlOperationSelection operation =
            new PipelineYamlOperationSelection("file.fetch", 1, "primary", config);
        PipelineObjectSourceConfig source =
            new PipelineObjectSourceConfig("source", "object", "file", config, null, null, null, null);
        PipelineObjectPublishConfig publish =
            new PipelineObjectPublishConfig("target", "object", "file", config, null, null, null);
        PipelineYamlStep step = new PipelineYamlStep(
            "Send", "command", "ONE_TO_ONE", "Input", null, "Output", null, null,
            List.of(), null, "send", null, null, config, null, null, List.of(), false);
        endpoints.add("secondary");

        for (Map<String, Object> snapshot : List.of(await.config(), binding.config(), operation.policy(),
            source.location(), publish.location(), step.commandConfig())) {
            List<?> copied = (List<?>) ((Map<?, ?>) snapshot.get("routing")).get("endpoints");
            assertEquals(List.of("primary"), copied);
            assertThrows(UnsupportedOperationException.class,
                () -> ((List<Object>) copied).add("third"));
        }
        assertEquals(Map.of(), new PipelineYamlAwaitTransport("webhook", null).config());
        assertThrows(IllegalArgumentException.class, () ->
            new PipelineYamlAwaitTransport("webhook", Map.of("mutable", new StringBuilder("x"))));

        Map<String, Object> optional = new java.util.LinkedHashMap<>();
        optional.put("value", null);
        Map<?, ?> optionalSnapshot = (Map<?, ?>) new PipelineYamlAwaitTransport(
            "webhook", Map.of("optional", optional)).config().get("optional");
        assertTrue(optionalSnapshot.containsKey("value"));
        assertNull(optionalSnapshot.get("value"));
    }

    @Test
    void callableKindMustBeCommandOrQuery() {
        assertEquals("command", new PipelineYamlCallable(
            "send", "primary", "file.send", ConnectorOperationKind.COMMAND, 1, "Input").kindToken());
        assertEquals("query", new PipelineYamlCallable(
            "fetch", "primary", "file.fetch", ConnectorOperationKind.QUERY, 1, "Input").kindToken());
        assertThrows(IllegalArgumentException.class, () -> new PipelineYamlCallable(
            "source", "primary", "file.fetch", ConnectorOperationKind.OBJECT_SOURCE, 1, "Input"));
        assertThrows(NullPointerException.class, () -> new PipelineYamlCallable(
            "source", "primary", "file.fetch", null, 1, "Input"));
    }
}
