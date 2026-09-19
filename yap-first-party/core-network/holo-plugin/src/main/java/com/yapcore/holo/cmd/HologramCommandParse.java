package com.yapcore.holo.cmd;

import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parse / JSON helpers for `/yapholo` (console + dashboard). */
final class HologramCommandParse {

    static final String JSON_PREFIX = "YAPHOLO_JSON:";

    private HologramCommandParse() {
    }

    record Row(String id, String world, double x, double y, double z, double view, String lines,
               String attach, String clicks, String perm, int pages) {
    }

    static String toJson(List<Row> rows) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            Row row = rows.get(i);
            sb.append('{')
                    .append("\"id\":").append(q(row.id())).append(',')
                    .append("\"world\":").append(q(row.world())).append(',')
                    .append("\"x\":").append(row.x()).append(',')
                    .append("\"y\":").append(row.y()).append(',')
                    .append("\"z\":").append(row.z()).append(',')
                    .append("\"view\":").append(row.view()).append(',')
                    .append("\"lines\":").append(q(row.lines() == null ? "" : row.lines())).append(',')
                    .append("\"attach\":").append(q(row.attach() == null ? "" : row.attach())).append(',')
                    .append("\"clicks\":").append(q(row.clicks() == null ? "" : row.clicks())).append(',')
                    .append("\"perm\":").append(q(row.perm() == null ? "" : row.perm())).append(',')
                    .append("\"pages\":").append(row.pages())
                    .append('}');
        }
        return sb.append(']').toString();
    }

    static String q(String s) {
        if (s == null) {
            return "null";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    static int indexOf(String[] args, String needle, int from) {
        for (int i = from; i < args.length; i++) {
            if (needle.equalsIgnoreCase(args[i])) {
                return i;
            }
        }
        return -1;
    }

    static String join(String[] args, int from) {
        StringBuilder out = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) {
                out.append(' ');
            }
            out.append(args[i]);
        }
        return out.toString();
    }

    static List<String> splitLines(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of("&f");
        }
        String[] parts = raw.split("\\|", -1);
        List<String> out = new ArrayList<>(parts.length);
        for (String part : parts) {
            out.add(part);
        }
        return out;
    }

    static double parseDouble(String raw, CommandSender sender) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid number: " + raw);
            return Double.NaN;
        }
    }

    static boolean validId(String id) {
        return id != null && id.matches("[A-Za-z0-9_\\-]{1,32}");
    }

    static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
