package com.yapcore.sched.agent;

/** Agent args: {@code warn=true|false}, {@code metrics=true|false}. */
public record SchedCompatOptions(boolean warnGlobal, boolean metrics) {

    public static SchedCompatOptions defaults() {
        return new SchedCompatOptions(true, true);
    }

    public static SchedCompatOptions parse(String agentArgs) {
        boolean warn = true;
        boolean metrics = true;
        if (agentArgs == null || agentArgs.isBlank()) {
            return new SchedCompatOptions(warn, metrics);
        }
        for (String part : agentArgs.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = kv[0].trim().toLowerCase();
            String val = kv[1].trim().toLowerCase();
            boolean on = "true".equals(val) || "1".equals(val) || "yes".equals(val);
            boolean off = "false".equals(val) || "0".equals(val) || "no".equals(val);
            switch (key) {
                case "warn", "warn-global", "warnglobal" -> {
                    if (off) {
                        warn = false;
                    } else if (on) {
                        warn = true;
                    }
                }
                case "metrics", "stats" -> {
                    if (off) {
                        metrics = false;
                    } else if (on) {
                        metrics = true;
                    }
                }
                default -> {
                }
            }
        }
        return new SchedCompatOptions(warn, metrics);
    }
}
