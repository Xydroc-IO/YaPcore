package com.yapcore.regions;

public enum FlagValue {
    ALLOW,
    DENY;

    public static FlagValue parse(String raw) {
        if (raw == null) {
            return DENY;
        }
        return "allow".equalsIgnoreCase(raw.trim()) ? ALLOW : DENY;
    }
}
