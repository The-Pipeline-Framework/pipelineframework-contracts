package org.pipelineframework.serialization;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;

/**
 * Experimental inner JSON encoding for one required, non-null signed int32 field named {@code value}.
 * The caller owns nominal type identity and the selection of this encoding.
 */
public final class PolyglotInt32JsonCodec {

    public static final String ENCODING = "tpf-polyglot-int32-json/1";

    private static final JsonFactory JSON = JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .build();

    public String encode(int value) {
        return "{\"value\":" + value + "}";
    }

    public int decode(String payload) {
        if (payload == null) {
            throw new IllegalArgumentException("INVALID_PAYLOAD_VALUE");
        }
        try (JsonParser parser = JSON.createParser(payload)) {
            if (parser.nextToken() != JsonToken.START_OBJECT
                || !"value".equals(parser.nextFieldName())
                || parser.nextToken() != JsonToken.VALUE_NUMBER_INT) {
                throw new IllegalArgumentException("INVALID_PAYLOAD_VALUE");
            }
            int value = parser.getIntValue();
            if (parser.nextToken() != JsonToken.END_OBJECT || parser.nextToken() != null) {
                throw new IllegalArgumentException("INVALID_PAYLOAD_VALUE");
            }
            return value;
        } catch (IOException invalidJson) {
            throw new IllegalArgumentException("INVALID_PAYLOAD_VALUE");
        }
    }
}
