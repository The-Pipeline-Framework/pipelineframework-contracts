package org.pipelineframework.connector;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class OwnedPayloadMultipartTest {
    @Test
    void streamsOneFilePartIncludingNearBoundaryBytes() throws Exception {
        byte[] file = "abc\r\n--not-the-boundary\r\n--test-boundaryXX\r\nxyz"
            .getBytes(StandardCharsets.US_ASCII);
        String request = "--test-boundary\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"a.txt\"\r\n"
            + "Content-Type: text/plain\r\n\r\n"
            + new String(file, StandardCharsets.US_ASCII)
            + "\r\n--test-boundary--\r\n";
        try (var multipart = new OwnedPayloadMultipart(
            new ByteArrayInputStream(request.getBytes(StandardCharsets.US_ASCII)),
            "multipart/form-data; boundary=test-boundary")) {
            assertEquals("text/plain", multipart.mediaType());
            assertArrayEquals(file, multipart.readAllBytes());
        }
    }

    @Test
    void rejectsAnotherPartAndTruncatedBody() throws Exception {
        String headers = "--b\r\nContent-Disposition: form-data; name=\"file\"\r\n"
            + "Content-Type: text/plain\r\n\r\ncontent";
        try (var multipart = open(headers + "\r\n--b\r\n")) {
            assertThrows(IllegalArgumentException.class, multipart::readAllBytes);
        }
        try (var multipart = open(headers)) {
            assertThrows(IllegalArgumentException.class, multipart::readAllBytes);
        }
    }

    @Test
    void rejectsWrongFieldAndOversizeHeaders() {
        assertThrows(IllegalArgumentException.class, () -> open(
            "--b\r\nContent-Disposition: form-data; name=\"other\"\r\n"
                + "Content-Type: text/plain\r\n\r\nx\r\n--b--\r\n"));
        assertThrows(IllegalArgumentException.class, () -> open(
            "--b\r\nContent-Disposition: form-data; name=\"other\"; name=\"file\"\r\n"
                + "Content-Type: text/plain\r\n\r\nx\r\n--b--\r\n"));
        assertThrows(IllegalArgumentException.class, () -> open(
            "--b\r\nX-Fill: " + "a".repeat(8192) + "\r\n\r\nx\r\n--b--\r\n"));
    }

    private static OwnedPayloadMultipart open(String request) throws Exception {
        return new OwnedPayloadMultipart(new ByteArrayInputStream(request.getBytes(StandardCharsets.US_ASCII)),
            "multipart/form-data; boundary=b");
    }
}
