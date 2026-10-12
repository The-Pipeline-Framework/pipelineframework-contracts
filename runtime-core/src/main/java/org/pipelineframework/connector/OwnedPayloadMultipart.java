package org.pipelineframework.connector;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

/** Reads one file part from a live multipart request without staging its content. */
public final class OwnedPayloadMultipart extends InputStream {
    private static final int MAX_BOUNDARY_BYTES = 70;
    private static final int MAX_HEADER_BYTES = 8192;
    private static final int READ_CHUNK_BYTES = 64 * 1024;
    private final PushbackInputStream input;
    private final byte[] delimiter;
    private final String mediaType;
    private boolean finished;

    public OwnedPayloadMultipart(InputStream request, String contentType) throws IOException {
        Objects.requireNonNull(request, "request stream must not be null");
        String boundary = boundary(contentType);
        delimiter = ("\r\n--" + boundary).getBytes(StandardCharsets.US_ASCII);
        input = new PushbackInputStream(new BufferedInputStream(request, READ_CHUNK_BYTES),
            READ_CHUNK_BYTES + delimiter.length + 2);
        expect(("--" + boundary + "\r\n").getBytes(StandardCharsets.US_ASCII));
        String disposition = null;
        String partType = null;
        int headerBytes = 0;
        int headers = 0;
        while (true) {
            String line = line(MAX_HEADER_BYTES - headerBytes);
            headerBytes += line.length() + 2;
            if (line.isEmpty()) {
                break;
            }
            if (++headers > 16) {
                throw malformed("too many multipart headers");
            }
            int separator = line.indexOf(':');
            if (separator <= 0) {
                throw malformed("invalid multipart header");
            }
            String name = line.substring(0, separator).toLowerCase(Locale.ROOT);
            String value = line.substring(separator + 1).trim();
            if (name.equals("content-disposition")) {
                if (disposition != null) throw malformed("duplicate content disposition");
                disposition = value;
            } else if (name.equals("content-type")) {
                if (partType != null) throw malformed("duplicate content type");
                partType = value;
            } else if (name.equals("content-transfer-encoding") && !value.equalsIgnoreCase("binary")) {
                throw malformed("encoded multipart parts are unsupported");
            }
        }
        if (!fileDisposition(disposition)) {
            throw malformed("expected one file part");
        }
        if (partType == null || partType.isBlank()) {
            throw malformed("file part requires a content type");
        }
        mediaType = partType;
    }

    public String mediaType() {
        return mediaType;
    }

    @Override
    public int read() throws IOException {
        if (finished) return -1;
        int next = input.read();
        if (next < 0) throw malformed("multipart body ended before its closing boundary");
        if (next != '\r') return next;
        byte[] lookahead = input.readNBytes(delimiter.length + 1);
        if (lookahead.length == delimiter.length + 1 && matchesDelimiter(lookahead)
            && lookahead[delimiter.length - 1] == '-' && lookahead[delimiter.length] == '-') {
            int trailing = input.read();
            if (trailing == '\r') {
                expect(new byte[] {'\n'});
                trailing = input.read();
            }
            if (trailing != -1) throw malformed("multipart request contains another part or trailing data");
            finished = true;
            return -1;
        }
        if (lookahead.length == delimiter.length + 1 && matchesDelimiter(lookahead)
            && lookahead[delimiter.length - 1] == '\r' && lookahead[delimiter.length] == '\n') {
            throw malformed("multipart request contains another part");
        }
        input.unread(lookahead);
        return next;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, bytes.length);
        if (length == 0) return 0;
        if (finished) return -1;
        int count = input.read(bytes, offset, Math.min(length, READ_CHUNK_BYTES));
        if (count < 0) throw malformed("multipart body ended before its closing boundary");
        if (count == 0) {
            int next = read();
            if (next < 0) return -1;
            bytes[offset] = (byte) next;
            return 1;
        }
        for (int index = 0; index < count; index++) {
            if (bytes[offset + index] == '\r') {
                input.unread(bytes, offset + index, count - index);
                if (index > 0) return index;
                int next = read();
                if (next < 0) return -1;
                bytes[offset] = (byte) next;
                return 1;
            }
        }
        return count;
    }

    @Override
    public void close() throws IOException {
        input.close();
    }

    private boolean matchesDelimiter(byte[] lookahead) {
        for (int i = 1; i < delimiter.length; i++) {
            if (lookahead[i - 1] != delimiter[i]) return false;
        }
        return true;
    }

    private static boolean fileDisposition(String value) {
        if (value == null) return false;
        java.util.List<String> parameters = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < value.length(); i++) {
            char next = value.charAt(i);
            if (next == '"' && (i == 0 || value.charAt(i - 1) != '\\')) quoted = !quoted;
            if (next == ';' && !quoted) {
                parameters.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(next);
            }
        }
        if (quoted) return false;
        parameters.add(current.toString().trim());
        if (!parameters.getFirst().equalsIgnoreCase("form-data")) return false;
        int names = 0;
        for (int i = 1; i < parameters.size(); i++) {
            String parameter = parameters.get(i);
            int equals = parameter.indexOf('=');
            if (equals < 1) return false;
            if (parameter.substring(0, equals).trim().equalsIgnoreCase("name")) {
                names++;
                String name = parameter.substring(equals + 1).trim();
                if (name.startsWith("\"") && name.endsWith("\"") && name.length() >= 2) {
                    name = name.substring(1, name.length() - 1);
                }
                if (!name.equals("file")) return false;
            }
        }
        return names == 1;
    }

    private void expect(byte[] expected) throws IOException {
        for (byte value : expected) {
            if (input.read() != Byte.toUnsignedInt(value)) throw malformed("invalid multipart framing");
        }
    }

    private String line(int remaining) throws IOException {
        if (remaining < 2) throw malformed("multipart headers exceed limit");
        byte[] bytes = new byte[remaining];
        int count = 0;
        while (true) {
            int next = input.read();
            if (next < 0 || count == remaining) throw malformed("unterminated multipart header");
            if (next == '\r') {
                if (input.read() != '\n') throw malformed("invalid multipart header line ending");
                return new String(bytes, 0, count, StandardCharsets.US_ASCII);
            }
            if (next == '\n' || next > 127) throw malformed("invalid multipart header");
            bytes[count++] = (byte) next;
        }
    }

    private static String boundary(String contentType) {
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data;")) {
            throw malformed("multipart/form-data content type is required");
        }
        String found = null;
        for (String parameter : contentType.substring(contentType.indexOf(';') + 1).split(";")) {
            String[] pair = parameter.trim().split("=", 2);
            if (pair.length == 2 && pair[0].trim().equalsIgnoreCase("boundary")) {
                if (found != null) throw malformed("duplicate multipart boundary");
                found = pair[1].trim();
                if (found.startsWith("\"") && found.endsWith("\"") && found.length() >= 2) {
                    found = found.substring(1, found.length() - 1);
                }
            }
        }
        if (found == null || found.isEmpty() || found.length() > MAX_BOUNDARY_BYTES
            || !found.matches("[A-Za-z0-9'()+_,./:=?-]+")) {
            throw malformed("invalid multipart boundary");
        }
        return found;
    }

    private static IllegalArgumentException malformed(String reason) {
        return new IllegalArgumentException(reason);
    }
}
