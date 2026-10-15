package org.pipelineframework.config.pipeline;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Objects;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.pipelineframework.type.CanonicalFieldValue;

class CanonicalFieldJsonTest {
    private final ObjectMapper mapper = PipelineJson.mapper();

    record Input(String name, CanonicalFieldValue<Boolean> flag) {
        Input { Objects.requireNonNull(flag); }
    }
    record NonNullInput(CanonicalFieldValue<String> value) {
        NonNullInput {
            Objects.requireNonNull(value);
            if (value.isNull()) throw new IllegalArgumentException("null forbidden");
        }
    }
    record Nested(String key) {}
    record Reference(CanonicalFieldValue<Nested> reference) {}
    record Values(CanonicalFieldValue<java.util.List<Nested>> values) {}

    @Test void omittedFieldRemainsAbsent() throws Exception {
        Input decoded = mapper.readValue("{\"name\":\"legacy\"}", Input.class);
        assertTrue(decoded.flag().isAbsent());
        assertEquals("{\"name\":\"legacy\"}", mapper.writeValueAsString(decoded));
    }
    @Test void explicitNullRemainsNull() throws Exception {
        Input decoded = mapper.readValue("{\"name\":\"x\",\"flag\":null}", Input.class);
        assertTrue(decoded.flag().isNull());
        assertEquals(decoded, mapper.readValue(mapper.writeValueAsString(decoded), Input.class));
    }
    @Test void falseAndTrueRemainPresent() throws Exception {
        for (boolean value : new boolean[]{false, true}) {
            Input input = new Input("x", CanonicalFieldValue.of(value));
            String json = mapper.writeValueAsString(input);
            assertEquals(value, mapper.readTree(json).get("flag").booleanValue());
            assertEquals(input, mapper.readValue(json, Input.class));
        }
    }
    @Test void nestedValueKeepsItsDeclaredType() throws Exception {
        Reference input = new Reference(CanonicalFieldValue.of(new Nested("object-key")));
        assertEquals(input, mapper.readValue(mapper.writeValueAsString(input), Reference.class));
    }
    @Test void collectionValueKeepsGenericTypeAndEmptyPresence() throws Exception {
        for (var items : java.util.List.of(java.util.List.<Nested>of(), java.util.List.of(new Nested("k")))) {
            Values input = new Values(CanonicalFieldValue.of(items));
            String json = mapper.writeValueAsString(input);
            assertTrue(mapper.readTree(json).has("values"));
            assertEquals(input, mapper.readValue(json, Values.class));
        }
    }
    @Test void absentRootCannotSilentlyBecomeExplicitNull() {
        assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,
            () -> mapper.writeValueAsString(CanonicalFieldValue.absent()));
    }
    @com.fasterxml.jackson.annotation.JsonTypeInfo(use = com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME,
        property = "kind")
    @com.fasterxml.jackson.annotation.JsonSubTypes({
        @com.fasterxml.jackson.annotation.JsonSubTypes.Type(value = Choice.class, name = "choice")})
    interface Base {}
    record Choice(String label) implements Base {}
    record Choices(CanonicalFieldValue<java.util.List<Base>> choices) {}

    @Test void polymorphicCollectionKeepsElementTypeIdentifiers() throws Exception {
        Choices input = new Choices(CanonicalFieldValue.of(java.util.List.of(new Choice("x"))));
        String json = mapper.writeValueAsString(input);
        assertEquals("choice", mapper.readTree(json).get("choices").get(0).get("kind").asText());
        assertEquals(input, mapper.readValue(json, Choices.class));
    }

    @Test void schemaStillRejectsExplicitNullForNonNullableField() {
        assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,
            () -> mapper.readValue("{\"value\":null}", NonNullInput.class));
    }

    record RequiredNonNull(Integer value) {
        RequiredNonNull { Objects.requireNonNull(value); }
    }
    record OptionalNonNull(CanonicalFieldValue<Integer> value) {
        OptionalNonNull {
            Objects.requireNonNull(value);
            if (value.isNull()) throw new IllegalArgumentException("null forbidden");
        }
    }
    record RequiredNullable(CanonicalFieldValue<Integer> value) {
        RequiredNullable {
            Objects.requireNonNull(value);
            if (value.isAbsent()) throw new IllegalArgumentException("absence forbidden");
        }
    }
    record OptionalNullable(CanonicalFieldValue<Integer> value) {
        OptionalNullable { Objects.requireNonNull(value); }
    }

    @Test void documentedPresenceMatrixPreservesZeroWithoutCreatingDefaults() throws Exception {
        for (Class<?> type : java.util.List.of(RequiredNonNull.class, OptionalNonNull.class,
                RequiredNullable.class, OptionalNullable.class)) {
            Object presentZero = mapper.readValue("{\"value\":0}", type);
            String encoded = mapper.writeValueAsString(presentZero);
            assertEquals(0, mapper.readTree(encoded).get("value").intValue());
            assertEquals(presentZero, mapper.readValue(encoded, type));
        }
        assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,
            () -> mapper.readValue("{}", RequiredNonNull.class));
        assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,
            () -> mapper.readValue("{}", RequiredNullable.class));
        assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,
            () -> mapper.readValue("{\"value\":null}", RequiredNonNull.class));
        assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,
            () -> mapper.readValue("{\"value\":null}", OptionalNonNull.class));
        assertTrue(mapper.readValue("{}", OptionalNonNull.class).value().isAbsent());
        assertTrue(mapper.readValue("{}", OptionalNullable.class).value().isAbsent());
        assertTrue(mapper.readValue("{\"value\":null}", RequiredNullable.class).value().isNull());
        assertTrue(mapper.readValue("{\"value\":null}", OptionalNullable.class).value().isNull());
    }

    record RestOptional(@com.fasterxml.jackson.databind.annotation.JsonDeserialize(
        using = CanonicalFieldJsonModule.NonNullOptionalDeserializer.class)
        java.util.Optional<Integer> value) { }

    @Test void annotatedRestOptionalRejectsNullButAcceptsOmissionAndZero() throws Exception {
        ObjectMapper rest = new ObjectMapper();
        assertEquals(java.util.Optional.empty(), rest.readValue("{}", RestOptional.class).value());
        assertEquals(java.util.Optional.of(0), rest.readValue("{\"value\":0}", RestOptional.class).value());
        assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,
            () -> rest.readValue("{\"value\":null}", RestOptional.class));
    }
}
