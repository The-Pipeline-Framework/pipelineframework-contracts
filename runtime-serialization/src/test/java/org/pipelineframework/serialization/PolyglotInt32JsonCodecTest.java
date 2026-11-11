package org.pipelineframework.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class PolyglotInt32JsonCodecTest {

    private final PolyglotInt32JsonCodec codec = new PolyglotInt32JsonCodec();

    @Test
    void encodesCompactDecimalAndDecodesExactIntegerTokens() {
        assertEquals("tpf-polyglot-int32-json/1", PolyglotInt32JsonCodec.ENCODING);
        for (int value : List.of(Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE)) {
            assertEquals("{\"value\":" + value + "}", codec.encode(value));
            assertEquals(value, codec.decode(codec.encode(value)));
        }
        assertEquals(0, codec.decode(" \t{\r\"\\u0076a\\u006Cue\" : -0\n}\r\n"));
    }

    @Test
    void rejectsOtherJsonShapesAndNonInt32Tokens() {
        for (String invalid : List.of(
            "", "{}", "[]", "null", "{\"value\":null}", "{\"value\":true}", "{\"value\":\"1\"}",
            "{\"value\":{}}", "{\"value\":[]}", "{\"value\":1.0}", "{\"value\":1e0}",
            "{\"value\":+1}", "{\"value\":01}", "{\"value\":-00}", "{\"value\":2147483648}",
            "{\"value\":-2147483649}", "{\"value\":9999999999999999999999999}",
            "{\"other\":1}", "{\"Value\":1}", "{\"value\":1,\"value\":2}",
            "{\"value\":1,\"\\u0076alue\":2}", "{\"value\":1,\"other\":2}", "{\"value\":1,}",
            "{\"value\":1}{}", "{\"value\":1} null", "{\"value\":1}\u00a0", "{\"value\":1}\0",
            "\uFEFF{\"value\":1}", "/*comment*/{\"value\":1}")) {
            assertThrows(IllegalArgumentException.class, () -> codec.decode(invalid), invalid);
        }
    }
}
