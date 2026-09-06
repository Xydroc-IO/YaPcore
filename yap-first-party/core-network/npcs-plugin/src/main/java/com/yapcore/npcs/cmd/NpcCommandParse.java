package com.yapcore.npcs.cmd;

import com.yapcore.npcs.db.NpcRepository;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared parse / format helpers for NPC commands. */
final class NpcCommandParse {

    private NpcCommandParse() {
    }

    static String toJson(List<NpcRepository.NpcRecord> records) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < records.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            NpcRepository.NpcRecord n = records.get(i);
            sb.append('{')
                    .append("\"id\":").append(q(n.id())).append(',')
                    .append("\"displayName\":").append(q(n.displayName())).append(',')
                    .append("\"world\":").append(q(n.world())).append(',')
                    .append("\"x\":").append(n.x()).append(',')
                    .append("\"y\":").append(n.y()).append(',')
                    .append("\"z\":").append(n.z()).append(',')
                    .append("\"yaw\":").append(n.yaw()).append(',')
                    .append("\"questId\":").append(q(n.questId())).append(',')
                    .append("\"dialogue\":").append(q(n.dialogue())).append(',')
                    .append("\"action\":").append(q(n.action()))
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

    static String[] copyFrom(String[] args, int from) {
        String[] out = new String[args.length - from];
        System.arraycopy(args, from, out, 0, out.length);
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

    static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    static List<String> prefix(List<String> options, String partial) {
        String p = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }
}
