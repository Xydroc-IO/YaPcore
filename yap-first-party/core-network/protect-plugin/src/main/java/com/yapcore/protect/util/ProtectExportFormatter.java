package com.yapcore.protect.util;

import com.yapcore.protect.model.ProtectChange;

import java.util.List;
import java.util.UUID;

/**
 * Pure CSV / JSON formatters for Protect export (no Bukkit).
 */
public final class ProtectExportFormatter {

    private ProtectExportFormatter() {
    }

    public static String toCsv(List<ProtectChange> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("id,server_id,change_type,actor_uuid,actor_name,world,x,y,z,block_before,block_after,epoch_ms,rolled_back\n");
        for (ProtectChange row : rows) {
            sb.append(row.id()).append(',')
                    .append(csv(nullToEmpty(row.serverId()))).append(',')
                    .append(csv(row.changeType() == null ? "" : row.changeType().name())).append(',')
                    .append(csv(uuid(row.actorUuid()))).append(',')
                    .append(csv(row.actorName())).append(',')
                    .append(csv(row.world())).append(',')
                    .append(row.x()).append(',')
                    .append(row.y()).append(',')
                    .append(row.z()).append(',')
                    .append(csv(row.blockBefore())).append(',')
                    .append(csv(row.blockAfter())).append(',')
                    .append(row.epochMs()).append(',')
                    .append(row.rolledBack())
                    .append('\n');
        }
        return sb.toString();
    }

    public static String toJson(List<ProtectChange> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < rows.size(); i++) {
            ProtectChange row = rows.get(i);
            if (i > 0) {
                sb.append(",\n");
            }
            sb.append("  {")
                    .append("\"id\":").append(row.id()).append(',')
                    .append("\"serverId\":").append(jsonString(nullToEmpty(row.serverId()))).append(',')
                    .append("\"changeType\":").append(jsonString(
                            row.changeType() == null ? "" : row.changeType().name())).append(',')
                    .append("\"actorUuid\":").append(jsonString(uuid(row.actorUuid()))).append(',')
                    .append("\"actorName\":").append(jsonString(nullToEmpty(row.actorName()))).append(',')
                    .append("\"world\":").append(jsonString(nullToEmpty(row.world()))).append(',')
                    .append("\"x\":").append(row.x()).append(',')
                    .append("\"y\":").append(row.y()).append(',')
                    .append("\"z\":").append(row.z()).append(',')
                    .append("\"blockBefore\":").append(jsonString(nullToEmpty(row.blockBefore()))).append(',')
                    .append("\"blockAfter\":").append(jsonString(nullToEmpty(row.blockAfter()))).append(',')
                    .append("\"epochMs\":").append(row.epochMs()).append(',')
                    .append("\"rolledBack\":").append(row.rolledBack())
                    .append('}');
        }
        if (!rows.isEmpty()) {
            sb.append('\n');
        }
        sb.append("]\n");
        return sb.toString();
    }

    static String csv(String value) {
        String v = value == null ? "" : value;
        boolean quote = v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0
                || v.indexOf('\r') >= 0;
        if (!quote) {
            return v;
        }
        return '"' + v.replace("\"", "\"\"") + '"';
    }

    static String jsonString(String value) {
        String v = value == null ? "" : value;
        StringBuilder sb = new StringBuilder(v.length() + 2);
        sb.append('"');
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static String uuid(UUID uuid) {
        return uuid == null ? "" : uuid.toString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
