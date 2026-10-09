package org.pipelineframework.orchestrator;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionAdmissionIntentTest {
    @Test
    void portableAdmissionValuesAreAvailable() {
        assertDoesNotThrow(() -> Class.forName("org.pipelineframework.orchestrator.ExecutionAdmissionIntent"));
        assertDoesNotThrow(() -> Class.forName("org.pipelineframework.orchestrator.ExecutionAdmissionReceipt"));
    }

    @Test
    void inputBytesAreImmutableAndUseStructuralEquality() {
        byte[] original = {0, 1, -1};
        ExecutionAdmissionIntent first = intent(original);
        ExecutionAdmissionIntent equal = intent(original.clone());
        String digest = first.fingerprint();
        original[0] = 99;
        byte[] returned = first.inputBytes();
        returned[1] = 98;
        assertArrayEquals(new byte[] {0, 1, -1}, first.inputBytes());
        assertEquals(equal, first);
        assertEquals(equal.hashCode(), first.hashCode());
        assertEquals(digest, first.fingerprint());
        assertNotEquals(first, intent(original));
        assertFalse(first.toString().contains("tenant"));
    }

    @Test
    void independentFramingOracleBindsEveryRequestFieldAndExactBytes() throws Exception {
        ExecutionAdmissionIntent original = intent("{\"v\":7}".getBytes(StandardCharsets.UTF_8));
        assertEquals("f42f1bcfeba4b2213f52ff81aec98edf5cc9cd907c061d0bbb7ce9c3550fdf1f", original.fingerprint());
        assertEquals(oracle(original), original.fingerprint());
        List<ExecutionAdmissionIntent> changed = List.of(
            change(original, "other", "pipeline", "key", "contract", "release", "UNI", "type", "json", original.inputBytes(), false),
            change(original, "tenant", "other", "key", "contract", "release", "UNI", "type", "json", original.inputBytes(), false),
            change(original, "tenant", "pipeline", "other", "contract", "release", "UNI", "type", "json", original.inputBytes(), false),
            change(original, "tenant", "pipeline", "key", "other", "release", "UNI", "type", "json", original.inputBytes(), false),
            change(original, "tenant", "pipeline", "key", "contract", "other", "UNI", "type", "json", original.inputBytes(), false),
            change(original, "tenant", "pipeline", "key", "contract", "release", "MULTI", "type", "json", original.inputBytes(), false),
            change(original, "tenant", "pipeline", "key", "contract", "release", "UNI", "other", "json", original.inputBytes(), false),
            change(original, "tenant", "pipeline", "key", "contract", "release", "UNI", "type", "other", original.inputBytes(), false),
            intent("{ \"v\":7}".getBytes(StandardCharsets.UTF_8)),
            change(original, "tenant", "pipeline", "key", "contract", "release", "UNI", "type", "json", original.inputBytes(), true));
        for (ExecutionAdmissionIntent value : changed) {
            assertNotEquals(original, value);
            assertNotEquals(original.fingerprint(), value.fingerprint());
            assertEquals(oracle(value), value.fingerprint());
        }
        assertTrue(original.sameClientKey(changed.get(3)));
        assertTrue(original.sameClientKey(changed.get(4)));
        assertFalse(original.sameClientKey(changed.get(0)));
        assertFalse(original.sameClientKey(changed.get(1)));
        assertFalse(original.sameClientKey(changed.get(2)));
    }

    @Test
    void framedFieldReconstructionPreservesValuesAndRejectsUnknownVersions() throws Exception {
        ExecutionAdmissionIntent original = intent(new byte[] {0, 1, -1});
        byte[] serialized = frame(original);
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(serialized))) {
            ExecutionAdmissionIntent restored = new ExecutionAdmissionIntent(input.readInt(), readText(input),
                readText(input), readText(input), readText(input), readText(input), readText(input),
                readText(input), readText(input), readBytes(input), input.readBoolean());
            assertEquals(-1, input.read());
            assertEquals(original, restored);
            assertEquals(original.hashCode(), restored.hashCode());
            assertEquals(original.fingerprint(), restored.fingerprint());
        }
        assertThrows(IllegalArgumentException.class, () -> new ExecutionAdmissionIntent(2, "tenant", "pipeline",
            "key", "contract", "release", "UNI", "type", "json", new byte[0], false));
        assertThrows(IllegalArgumentException.class, () -> change(original, "tenant", "pipeline", " ", "contract",
            "release", "UNI", "type", "json", new byte[0], false));
        assertThrows(NullPointerException.class, () -> intent(null));
    }

    @Test
    void lengthFramingDoesNotConfuseAdjacentValues() throws Exception {
        ExecutionAdmissionIntent original = intent(new byte[0]);
        ExecutionAdmissionIntent left = change(original, "ab", "c", "key", "contract", "release", "UNI", "type", "json", new byte[0], false);
        ExecutionAdmissionIntent right = change(original, "a", "bc", "key", "contract", "release", "UNI", "type", "json", new byte[0], false);
        assertNotEquals(left.fingerprint(), right.fingerprint());
        assertEquals(oracle(left), left.fingerprint());
        assertEquals(oracle(right), right.fingerprint());
    }

    @Test
    void malformedSurrogatesCannotCollapseDistinctIntentFingerprints() {
        ExecutionAdmissionIntent original = intent(new byte[] {7});
        ExecutionAdmissionIntent first = change(original, "tenant", "pipeline", "key", "contract", "release",
            "\uD800", "type", "json", original.inputBytes(), false);
        ExecutionAdmissionIntent second = change(original, "tenant", "pipeline", "key", "contract", "release",
            "\uD801", "type", "json", original.inputBytes(), false);
        assertNotEquals(first, second);
        assertThrows(IllegalArgumentException.class, first::fingerprint);
        assertThrows(IllegalArgumentException.class, second::fingerprint);
        for (String malformed : List.of("\uD800", "\uDC00", "before\uD800after", "\uD800\uD800")) {
            for (int field = 0; field < 8; field++) {
                String[] values = {"tenant", "pipeline", "key", "contract", "release", "UNI", "type", "json"};
                values[field] = malformed;
                ExecutionAdmissionIntent invalid = change(original, values[0], values[1], values[2], values[3],
                    values[4], values[5], values[6], values[7], original.inputBytes(), false);
                assertThrows(IllegalArgumentException.class, invalid::fingerprint);
            }
        }
    }

    @Test
    void validUnicodeMetadataUsesExactUtf8WithoutNormalization() throws Exception {
        ExecutionAdmissionIntent original = intent(new byte[] {0, -1});
        ExecutionAdmissionIntent unicode = change(original, "ténant", "pipeline-\uD83D\uDE00", "clé", "contract",
            "release", "UNI", "týpe", "json", original.inputBytes(), false);
        assertEquals(oracle(unicode), unicode.fingerprint());
        assertNotEquals(unicode.fingerprint(), change(original, "te\u0301nant", "pipeline-\uD83D\uDE00", "clé", "contract",
            "release", "UNI", "týpe", "json", original.inputBytes(), false).fingerprint());
    }

    @Test
    void receiptIsImmutableCorrelationNotAnotherReleaseIdentityOrDispatchPermission() {
        ExecutionAdmissionIntent original = intent(new byte[] {7});
        ExecutionAdmissionReceipt receipt = receipt(original);
        assertTrue(receipt.matches(original));
        assertFalse(receipt.matches(intent(new byte[] {8})));
        assertFalse(receipt.matches(change(original, "tenant", "pipeline", "key", "contract", "release-b",
            "UNI", "type", "json", original.inputBytes(), false)));
        assertEquals("artifact", receipt.primaryArtifactId());
        assertEquals("sha256:artifact", receipt.primaryArtifactDigest());
        assertEquals(receipt, receipt(original));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionAdmissionReceipt(2, "tenant", "pipeline",
            "key", "contract", "release", "execution", "a".repeat(64), "artifact", "sha256:artifact", original.fingerprint(), 1));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionAdmissionReceipt(1, "tenant", "pipeline",
            "key", "contract", "release", "execution", "invalid", "artifact", "sha256:artifact", original.fingerprint(), 1));
    }

    static ExecutionAdmissionIntent intent(byte[] bytes) {
        return new ExecutionAdmissionIntent(1, "tenant", "pipeline", "key", "contract", "release", "UNI", "type", "json", bytes, false);
    }

    private static ExecutionAdmissionIntent change(ExecutionAdmissionIntent ignored, String tenant, String pipeline,
        String key, String contract, String release, String shape, String type, String encoding, byte[] bytes, boolean streaming) {
        return new ExecutionAdmissionIntent(1, tenant, pipeline, key, contract, release, shape, type, encoding, bytes, streaming);
    }

    private static ExecutionAdmissionReceipt receipt(ExecutionAdmissionIntent intent) {
        return new ExecutionAdmissionReceipt(1, intent.tenantId(), intent.pipelineId(), intent.clientKey(),
            intent.contractVersion(), intent.releaseVersion(), "execution", "a".repeat(64), "artifact", "sha256:artifact", intent.fingerprint(), 1000);
    }

    private static String oracle(ExecutionAdmissionIntent intent) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(frame(intent)));
    }

    private static byte[] frame(ExecutionAdmissionIntent intent) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeInt(intent.schemaVersion());
            for (String text : List.of(intent.tenantId(), intent.pipelineId(), intent.clientKey(), intent.contractVersion(),
                intent.releaseVersion(), intent.inputShape(), intent.payloadTypeId(), intent.payloadEncoding())) {
                byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
                output.writeInt(utf8.length);
                output.write(utf8);
            }
            output.writeInt(intent.inputBytes().length);
            output.write(intent.inputBytes());
            output.writeBoolean(intent.outputStreaming());
        }
        return buffer.toByteArray();
    }

    private static byte[] readBytes(DataInputStream input) throws Exception {
        return input.readNBytes(input.readInt());
    }

    private static String readText(DataInputStream input) throws Exception {
        return new String(readBytes(input), StandardCharsets.UTF_8);
    }
}
