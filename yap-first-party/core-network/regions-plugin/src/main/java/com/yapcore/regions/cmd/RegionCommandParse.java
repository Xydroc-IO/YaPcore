package com.yapcore.regions.cmd;

import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;

/** Shared parse / format helpers for region commands. */
final class RegionCommandParse {

    private RegionCommandParse() {
    }

    static long volumeOf(com.yapcore.regions.AdminRegion region) {
        return region.volume();
    }

    static String toJson(List<com.yapcore.regions.AdminRegion> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            var r = list.get(i);
            sb.append('{')
                    .append("\"name\":").append(q(r.name())).append(',')
                    .append("\"id\":").append(r.id()).append(',')
                    .append("\"world\":").append(q(r.world())).append(',')
                    .append("\"shape\":").append(q(r.shape().name().toLowerCase(Locale.ROOT))).append(',')
                    .append("\"minX\":").append(r.minX()).append(',')
                    .append("\"minY\":").append(r.minY()).append(',')
                    .append("\"minZ\":").append(r.minZ()).append(',')
                    .append("\"maxX\":").append(r.maxX()).append(',')
                    .append("\"maxY\":").append(r.maxY()).append(',')
                    .append("\"maxZ\":").append(r.maxZ()).append(',')
                    .append("\"priority\":").append(r.priority()).append(',')
                    .append("\"flagCount\":").append(r.flags().size()).append(',')
                    .append("\"vertexCount\":").append(r.vertices().size()).append(',')
                    .append("\"flags\":{");
            int fi = 0;
            for (var entry : r.flags().entrySet()) {
                if (fi++ > 0) {
                    sb.append(',');
                }
                String key = entry.getKey().name().toLowerCase(Locale.ROOT).replace('_', '-');
                sb.append(q(key)).append(':')
                        .append(q(entry.getValue().name().toLowerCase(Locale.ROOT)));
            }
            sb.append("}}");
        }
        return sb.append(']').toString();
    }

    static String q(String s) {
        if (s == null) {
            return "null";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    static int indexOf(String[] args, String needle, int from) {
        for (int i = from; i < args.length; i++) {
            if (needle.equalsIgnoreCase(args[i])) {
                return i;
            }
        }
        return -1;
    }

    static int parseInt(String raw, CommandSender sender) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid integer: " + raw);
            return Integer.MIN_VALUE;
        }
    }

    static List<String> prefix(List<String> options, String partial) {
        String p = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
