/*
 * Copyright (c) 2023-2025 Mariano Barcia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.pipelineframework.config.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.pipelineframework.protocol.ProtocolTypeIdentity;

class PipelineTemplateTypeModelTest {

    @Test
    void normalizesAndDefensivelyCopiesSemanticMetadata() {
        Map<String, PipelineTemplateTypeDefinition> definitions = new LinkedHashMap<>();
        definitions.put("Accepted", new PipelineTemplateTypeDefinition.RecordType("Accepted", List.of()));
        Map<String, Object> providerOptions = new LinkedHashMap<>();
        providerOptions.put("mode", "strict");
        RepresentationMapping mapping = new RepresentationMapping(
            "json", "Accepted", Optional.of(" com.example.AcceptedJson "), Optional.empty());

        PipelineTemplateTypeModel model = new PipelineTemplateTypeModel(
            definitions,
            Map.of("Accepted", Map.of("json", mapping)),
            Map.of("json", providerOptions),
            Map.of("Accepted", ProtocolTypeIdentity.of("example.Accepted")),
            Map.of("Accepted", "com.example.Accepted")
        );
        definitions.clear();
        providerOptions.put("mode", "relaxed");

        assertTrue(model.contains("Accepted"));
        assertEquals("strict", model.representationProviderConfiguration("json").orElseThrow().get("mode"));
        assertEquals(Optional.of("com.example.AcceptedJson"),
            model.representationMapping("Accepted", "json").orElseThrow().representationType());
        assertEquals("example.Accepted", model.contributedTypeIdentity("Accepted").orElseThrow().qualifiedName());
        assertEquals(Optional.of("com.example.Accepted"), model.javaTypeBinding("Accepted"));
        assertThrows(UnsupportedOperationException.class, () -> model.definitions().clear());
        assertThrows(UnsupportedOperationException.class,
            () -> model.representationProviderConfiguration("json").orElseThrow().clear());
    }

    @Test
    void freezesNestedProviderOptions() {
        List<Object> endpoints = new java.util.ArrayList<>(List.of("primary"));
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("routing", Map.of("endpoints", endpoints));
        PipelineTemplateTypeModel model = new PipelineTemplateTypeModel(
            Map.of(), Map.of(), Map.of("json", options));
        endpoints.add("secondary");

        Map<?, ?> routing = (Map<?, ?>) model.representationProviderConfiguration("json")
            .orElseThrow().get("routing");
        assertEquals(List.of("primary"), routing.get("endpoints"));
        assertThrows(UnsupportedOperationException.class,
            () -> ((List<Object>) routing.get("endpoints")).add("third"));
    }

    @Test
    void canonicalizesLegacyMapKeysAndRejectsUnsupportedOnes() {
        PipelineTemplateField mapField = PipelineTemplateTypeMappings.normalizeLegacyField(
            new PipelineTemplateField("entries", "Map<Integer, String>", null));
        PipelineTemplateTypeModel model = new LegacyPipelineTemplateTypeModelAdapter().adapt(
            Map.of("Lookup", new PipelineTemplateMessage("Lookup", List.of(mapField), null)), Map.of());
        PipelineTemplateTypeDefinition.RecordType lookup =
            (PipelineTemplateTypeDefinition.RecordType) model.definitions().get("Lookup");
        PipelineTemplateTypeReference.MapType map =
            (PipelineTemplateTypeReference.MapType) lookup.fields().getFirst().type();
        assertEquals("int32", map.keyType().name());
        assertEquals(new PipelineTemplateTypeReference.Scalar("string"), map.valueType());

        PipelineTemplateField unsupported = PipelineTemplateTypeMappings.normalizeLegacyField(
            new PipelineTemplateField("entries", "Map<Float, String>", null));
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            new LegacyPipelineTemplateTypeModelAdapter().adapt(
                Map.of("Lookup", new PipelineTemplateMessage("Lookup", List.of(unsupported), null)), Map.of()));
        assertTrue(exception.getMessage().contains("unsupported keyType 'Float'"));
    }

    @Test
    void clearsMessageReferencesWhenResolvedAsScalar() {
        PipelineTemplateField message = new PipelineTemplateField(
            1, "value", "Order", "message", "Order", null, null, null, null,
            false, false, false, null, null, null, null, null);
        assertEquals("Order", message.withCanonicalType("message", null).messageRef());
        assertEquals(null, message.withCanonicalType("string", null).messageRef());
    }

    @Test
    void rejectsConflictingWrapperBounds() {
        assertThrows(IllegalArgumentException.class, () -> new PipelineTemplateWrapperConstraints(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.of(java.math.BigDecimal.ONE), Optional.of(java.math.BigDecimal.TWO),
            Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new PipelineTemplateWrapperConstraints(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(),
            Optional.of(java.math.BigDecimal.ONE), Optional.of(java.math.BigDecimal.TWO)));
    }

    @Test
    void snapshotsTemplateAspectConfigAndStepFields() {
        List<Object> flags = new java.util.ArrayList<>(List.of("on"));
        PipelineTemplateAspect aspect = new PipelineTemplateAspect(
            true, "GLOBAL", "BEFORE_STEP", 0, Map.of("flags", flags));
        List<PipelineTemplateField> fields = new java.util.ArrayList<>(
            List.of(new PipelineTemplateField("id", "String", null)));
        PipelineTemplateStep step = new PipelineTemplateStep(
            "Read", "ONE_TO_ONE", "Input", fields, "Output", fields);
        flags.add("off");
        fields.clear();

        assertEquals(List.of("on"), aspect.config().get("flags"));
        assertThrows(UnsupportedOperationException.class,
            () -> ((List<Object>) aspect.config().get("flags")).add("later"));
        assertEquals(1, step.inputFields().size());
        assertEquals(1, step.outputFields().size());
        assertThrows(UnsupportedOperationException.class, () -> step.inputFields().clear());
    }

    @Test
    void resolvesAliasesAndPreservesNominalAndUnionAssignability() {
        PipelineTemplateTypeReference.Named accepted = new PipelineTemplateTypeReference.Named("Accepted");
        PipelineTemplateTypeModel model = new PipelineTemplateTypeModel(Map.of(
            "OrderId", new PipelineTemplateTypeDefinition.WrapperType(
                "OrderId", new PipelineTemplateTypeReference.Scalar("string")),
            "LegacyOrderId", new PipelineTemplateTypeDefinition.AliasType(
                "LegacyOrderId", new PipelineTemplateTypeReference.Named("OrderId")),
            "Accepted", new PipelineTemplateTypeDefinition.RecordType("Accepted", List.of()),
            "Rejected", new PipelineTemplateTypeDefinition.RecordType("Rejected", List.of()),
            "Outcome", new PipelineTemplateTypeDefinition.UnionType("Outcome", Map.of(
                "accepted", new PipelineTemplateTypeDefinition.Variant("accepted", accepted),
                "rejected", new PipelineTemplateTypeDefinition.Variant(
                    "rejected", new PipelineTemplateTypeReference.Named("Rejected"))))
        ));

        assertEquals(new PipelineTemplateTypeReference.Named("OrderId"),
            model.resolveAliases(new PipelineTemplateTypeReference.Named("LegacyOrderId")));
        assertTrue(model.isAssignable("LegacyOrderId", "OrderId"));
        assertTrue(model.isAssignable("Accepted", "Outcome"));
        assertFalse(model.isAssignable("string", "OrderId"));
        assertFalse(model.isAssignable("Outcome", "Accepted"));
    }

    @Test
    void rejectsInvalidReferencesAndBuiltInTypeNames() {
        IllegalStateException unknown = assertThrows(IllegalStateException.class,
            () -> new PipelineTemplateTypeModel(Map.of(
                "Invoice", new PipelineTemplateTypeDefinition.RecordType("Invoice", List.of(
                    new PipelineTemplateTypeDefinition.Field(
                        "customer", new PipelineTemplateTypeReference.Named("Customer")))))));
        IllegalStateException builtIn = assertThrows(IllegalStateException.class,
            () -> new PipelineTemplateTypeModel(Map.of(
                " string ", new PipelineTemplateTypeDefinition.RecordType(" string ", List.of()))));

        assertEquals("Type 'Invoice.customer' references unknown type 'Customer'", unknown.getMessage());
        assertEquals("Type name ' string ' conflicts with a built-in semantic type", builtIn.getMessage());
    }
}
