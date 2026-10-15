package org.pipelineframework.config.pipeline;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.pipelineframework.type.CanonicalFieldValue;

/**
 * JSON boundary for generated canonical records, without coupling the runtime model to Jackson.
 * Missing creator properties become ABSENT; mutable beans must initialize their own fields.
 */
final class CanonicalFieldJsonModule extends SimpleModule {
    CanonicalFieldJsonModule() {
        super("pipeline-canonical-field-json");
        addSerializer(new FieldSerializer());
        addDeserializer(CanonicalFieldValue.class, new FieldDeserializer(null));
    }

    private static final class FieldSerializer extends StdSerializer<CanonicalFieldValue<?>>
            implements ContextualSerializer {
        private final JsonSerializer<Object> valueSerializer;

        FieldSerializer() { this(null); }

        FieldSerializer(JsonSerializer<Object> valueSerializer) {
            super(CanonicalFieldValue.class, false);
            this.valueSerializer = valueSerializer;
        }

        @Override
        public JsonSerializer<?> createContextual(SerializerProvider provider, BeanProperty property)
                throws JsonMappingException {
            JavaType wrapper = property == null ? null
                : property.getType().findSuperType(CanonicalFieldValue.class);
            if (wrapper == null || wrapper.containedTypeCount() != 1) return this;
            return new FieldSerializer(provider.findTypedValueSerializer(
                wrapper.containedType(0), true, property));
        }

        @Override
        public boolean isEmpty(SerializerProvider provider, CanonicalFieldValue<?> value) {
            return value == null || value.isAbsent();
        }

        @Override
        public void serialize(CanonicalFieldValue<?> value, JsonGenerator generator,
                SerializerProvider provider) throws IOException {
            if (value.isAbsent()) {
                throw JsonMappingException.from(generator,
                    "An absent canonical field must be omitted from its containing object");
            }
            if (value.isNull()) {
                generator.writeNull();
            } else {
                Object present = value.asOptional().orElseThrow();
                if (valueSerializer == null) {
                    provider.defaultSerializeValue(present, generator);
                } else {
                    valueSerializer.serialize(present, generator, provider);
                }
            }
        }
    }

    private static final class FieldDeserializer extends StdDeserializer<CanonicalFieldValue<?>>
            implements ContextualDeserializer {
        private final JavaType valueType;

        FieldDeserializer(JavaType valueType) {
            super(CanonicalFieldValue.class);
            this.valueType = valueType;
        }

        @Override
        public JsonDeserializer<?> createContextual(DeserializationContext context, BeanProperty property)
                throws JsonMappingException {
            JavaType wrapper = context.getContextualType();
            if (wrapper == null && property != null) wrapper = property.getType();
            if (wrapper == null || wrapper.containedTypeCount() != 1) {
                throw JsonMappingException.from(context, "Canonical field requires a declared value type");
            }
            return new FieldDeserializer(wrapper.containedType(0));
        }

        @Override
        public CanonicalFieldValue<?> deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            return CanonicalFieldValue.of(context.readValue(parser, valueType));
        }

        @Override
        public CanonicalFieldValue<?> getNullValue(DeserializationContext context) {
            return CanonicalFieldValue.nullValue();
        }

        @Override
        public Object getAbsentValue(DeserializationContext context) {
            return CanonicalFieldValue.absent();
        }
    }
}
