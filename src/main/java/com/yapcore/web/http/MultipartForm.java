package com.yapcore.web.http;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Minimal multipart/form-data reader for dashboard uploads (file + text fields). */
public final class MultipartForm {

    private MultipartForm() {
    }

    public record Part(String filename, byte[] bytes) {
    }

    public record Result(Map<String, String> fields, Map<String, Part> files) {
    }

    public static Result parse(InputStream in, String contentType, int maxBytes) throws IOException {
        if (contentType == null || !contentType.toLowerCase().contains("multipart/form-data")) {
            throw new IOException("expected multipart/form-data");
        }
        String boundary = null;
        for (String part : contentType.split(";")) {
            String p = part.trim();
            if (p.toLowerCase().startsWith("boundary=")) {
                boundary = p.substring("boundary=".length()).trim();
                if (boundary.startsWith("\"") && boundary.endsWith("\"") && boundary.length() >= 2) {
                    boundary = boundary.substring(1, boundary.length() - 1);
                }
            }
        }
        if (boundary == null || boundary.isBlank()) {
            throw new IOException("missing multipart boundary");
        }
        byte[] raw = readLimited(in, maxBytes);
        byte[] delim = ("--" + boundary).getBytes(StandardCharsets.UTF_8);
        Map<String, String> fields = new HashMap<>();
        Map<String, Part> files = new HashMap<>();
        int idx = indexOf(raw, delim, 0);
        while (idx >= 0) {
            int start = idx + delim.length;
            if (start + 1 < raw.length && raw[start] == '-' && raw[start + 1] == '-') {
                break;
            }
            if (start < raw.length && raw[start] == '\r') {
                start++;
            }
            if (start < raw.length && raw[start] == '\n') {
                start++;
            }
            int next = indexOf(raw, delim, start);
            int end = next < 0 ? raw.length : next;
            // trim trailing CRLF before boundary
            if (end >= 2 && raw[end - 2] == '\r' && raw[end - 1] == '\n') {
                end -= 2;
            }
            parsePart(raw, start, end, fields, files);
            idx = next;
        }
        return new Result(fields, files);
    }

    private static void parsePart(
            byte[] raw, int start, int end, Map<String, String> fields, Map<String, Part> files)
            throws IOException {
        int headerEnd = indexOf(raw, "\r\n\r\n".getBytes(StandardCharsets.UTF_8), start);
        if (headerEnd < 0 || headerEnd > end) {
            return;
        }
        String headers = new String(raw, start, headerEnd - start, StandardCharsets.UTF_8);
        int bodyStart = headerEnd + 4;
        int bodyEnd = end;
        String name = null;
        String filename = null;
        for (String line : headers.split("\r\n")) {
            if (!line.toLowerCase().startsWith("content-disposition:")) {
                continue;
            }
            for (String token : line.split(";")) {
                String t = token.trim();
                if (t.toLowerCase().startsWith("name=")) {
                    name = unquote(t.substring(5));
                } else if (t.toLowerCase().startsWith("filename=")) {
                    filename = unquote(t.substring(9));
                }
            }
        }
        if (name == null || name.isBlank()) {
            return;
        }
        byte[] body = new byte[Math.max(0, bodyEnd - bodyStart)];
        if (body.length > 0) {
            System.arraycopy(raw, bodyStart, body, 0, body.length);
        }
        if (filename != null && !filename.isBlank()) {
            files.put(name, new Part(filename, body));
        } else {
            fields.put(name, new String(body, StandardCharsets.UTF_8).trim());
        }
    }

    private static String unquote(String s) {
        String v = s.trim();
        if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    private static byte[] readLimited(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            total += n;
            if (total > maxBytes) {
                throw new IOException("upload too large (max " + maxBytes + " bytes)");
            }
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private static int indexOf(byte[] data, byte[] pattern, int from) {
        outer:
        for (int i = from; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    public static InputStream bytes(byte[] data) {
        return new ByteArrayInputStream(data == null ? new byte[0] : data);
    }
}
