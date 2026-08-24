package com.yapcore.link.metrics;

import java.util.Map;
import java.util.StringJoiner;

/** Minimal Prometheus exposition format (no labels beyond optional prefix). */
public final class PrometheusText {

    private PrometheusText() {
    }

    public static String render(String prefix, Map<String, Long> counters, Map<String, Long> gauges) {
        StringJoiner out = new StringJoiner("\n", "", "\n");
        if (counters != null) {
            counters.forEach((name, value) -> {
                String metric = sanitize(prefix + name) + "_total";
                out.add("# TYPE " + metric + " counter");
                out.add(metric + " " + value);
            });
        }
        if (gauges != null) {
            gauges.forEach((name, value) -> {
                String metric = sanitize(prefix + name);
                out.add("# TYPE " + metric + " gauge");
                out.add(metric + " " + value);
            });
        }
        return out.toString();
    }

    public static String sanitize(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String s = sb.toString();
        if (s.isEmpty() || Character.isDigit(s.charAt(0))) {
            s = "m_" + s;
        }
        return s;
    }
}
