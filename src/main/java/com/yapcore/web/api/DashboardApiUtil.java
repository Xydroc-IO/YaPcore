package com.yapcore.web.api;

import java.util.ArrayList;
import java.util.List;

final class DashboardApiUtil {

    private DashboardApiUtil() {
    }

    static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }
}
