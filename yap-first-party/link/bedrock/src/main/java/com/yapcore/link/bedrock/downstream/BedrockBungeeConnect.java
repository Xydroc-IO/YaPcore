package com.yapcore.link.bedrock.downstream;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/** Sniff BungeeCord {@code Connect} / {@code ConnectOther} from JE custom_payload bodies. */
public final class BedrockBungeeConnect {

    private BedrockBungeeConnect() {
    }

    public static boolean isBungeeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return false;
        }
        String id = channel.toLowerCase(Locale.ROOT);
        return id.equals("bungeecord:main")
                || id.equals("minecraft:bungeecord")
                || id.equals("bungeecord")
                || id.endsWith(":bungeecord");
    }

    public static Optional<String> sniffTarget(byte[] data) {
        if (data == null || data.length < 4) {
            return Optional.empty();
        }
        Optional<String> fromStream = readConnectUtf(data);
        if (fromStream.isPresent()) {
            return fromStream;
        }
        return sniffEmbedded(data);
    }

    private static Optional<String> readConnectUtf(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            String sub = in.readUTF();
            if (!"Connect".equalsIgnoreCase(sub) && !"ConnectOther".equalsIgnoreCase(sub)) {
                return Optional.empty();
            }
            if ("ConnectOther".equalsIgnoreCase(sub)) {
                in.readUTF(); // player name
            }
            String target = in.readUTF();
            return sanitize(target);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Optional<String> sniffEmbedded(byte[] hay) {
        byte[] needle = encodeJavaUtf("Connect");
        int idx = indexOf(hay, needle);
        if (idx < 0) {
            return Optional.empty();
        }
        int after = idx + needle.length;
        if (after + 2 > hay.length) {
            return Optional.empty();
        }
        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(hay, after, hay.length - after))) {
            return sanitize(in.readUTF());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Optional<String> sanitize(String target) {
        if (target == null || target.isBlank()) {
            return Optional.empty();
        }
        String t = target.trim();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-')) {
                return Optional.empty();
            }
        }
        return Optional.of(t);
    }

    private static byte[] encodeJavaUtf(String s) {
        byte[] chars = s.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[2 + chars.length];
        out[0] = (byte) ((chars.length >>> 8) & 0xFF);
        out[1] = (byte) (chars.length & 0xFF);
        System.arraycopy(chars, 0, out, 2, chars.length);
        return out;
    }

    private static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i <= hay.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
