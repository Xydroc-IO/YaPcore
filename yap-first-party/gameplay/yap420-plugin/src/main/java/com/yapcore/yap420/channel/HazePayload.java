package com.yapcore.yap420.channel;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * Server↔client haze payload: {@code HAZE|&lt;intensity&gt;|&lt;durationTicks&gt;}
 * or client {@code HELLO}.
 */
public final class HazePayload {

    private HazePayload() {
    }

    public static byte[] encodeHaze(double intensity, int durationTicks) {
        double i = Math.max(0.0, Math.min(1.0, intensity));
        int d = Math.max(1, durationTicks);
        String text = "HAZE|" + String.format(Locale.ROOT, "%.3f", i) + "|" + d;
        return text.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] encodeHelloAck() {
        return "OK".getBytes(StandardCharsets.UTF_8);
    }

    public static Optional<Haze> parse(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return Optional.empty();
        }
        String text = new String(raw, StandardCharsets.UTF_8).trim();
        if (text.regionMatches(true, 0, "HAZE|", 0, 5)) {
            String[] parts = text.split("\\|");
            if (parts.length < 3) {
                return Optional.empty();
            }
            try {
                double intensity = Double.parseDouble(parts[1]);
                int duration = Integer.parseInt(parts[2]);
                return Optional.of(new Haze(intensity, duration));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public static boolean isHello(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return false;
        }
        String text = new String(raw, StandardCharsets.UTF_8).trim();
        return text.equalsIgnoreCase("HELLO");
    }

    public record Haze(double intensity, int durationTicks) {
    }
}
