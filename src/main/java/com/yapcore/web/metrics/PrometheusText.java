package com.yapcore.web.metrics;

import java.util.Map;
import java.util.StringJoiner;

/** Prometheus text format helper (chassis). */
public final class PrometheusText {

    private PrometheusText() {
    }

    public static String counters(Map<String, Long> counters) {
        StringJoiner out = new StringJoiner("\n", "", "\n");
        counters.forEach((name, value) -> {
            String metric = sanitize(name);
            if (!metric.endsWith("_total")) {
                metric = metric + "_total";
            }
            out.add("# TYPE " + metric + " counter");
            out.add(metric + " " + value);
        });
        return out.toString();
    }

    public static String gauges(Map<String, Long> gauges) {
        StringJoiner out = new StringJoiner("\n", "", "\n");
        gauges.forEach((name, value) -> {
            String metric = sanitize(name);
            out.add("# TYPE " + metric + " gauge");
            out.add(metric + " " + value);
        });
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
