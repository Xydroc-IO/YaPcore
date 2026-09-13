package com.yapcore.tailor;

/** Player skin arm model. */
public enum SkinModel {
    SLIM,
    WIDE;

    public static SkinModel fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return WIDE;
        }
        String v = raw.trim().toUpperCase();
        return switch (v) {
            case "SLIM", "ALEX", "3PX" -> SLIM;
            case "WIDE", "CLASSIC", "STEVE", "4PX" -> WIDE;
            default -> {
                try {
                    yield SkinModel.valueOf(v);
                } catch (IllegalArgumentException e) {
                    yield WIDE;
                }
            }
        };
    }
}
