package com.yapcore.regions.db;

import com.yapcore.db.YapSqlDialect;
import com.yapcore.regions.FlagValue;
import com.yapcore.regions.RegionFlag;
import com.yapcore.regions.RegionMessageKind;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Named flag + message presets for admin regions. */
public final class RegionTemplateRepository {

    private final RegionSql database;
    private final YapSqlDialect dialect;

    public RegionTemplateRepository(RegionsDatabase database) {
        this((RegionSql) database);
    }

    public RegionTemplateRepository(RegionSql database) {
        this.database = database;
        this.dialect = database.dialect();
    }

    public List<String> listNames(String serverId) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT name FROM yap_admin_region_templates WHERE server_id = ? ORDER BY name
                     """)) {
            ps.setString(1, serverId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString("name"));
                }
            }
        }
        return out;
    }

    public void save(String serverId, String name,
                     Map<RegionFlag, FlagValue> flags,
                     Map<RegionMessageKind, String> messages) throws SQLException {
        String sql = dialect.upsert(
                "yap_admin_region_templates",
                List.of("server_id", "name"),
                List.of("server_id", "name", "flags_json", "messages_json"),
                Map.of(
                        "flags_json", "EXCLUDED.flags_json",
                        "messages_json", "EXCLUDED.messages_json"));
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, serverId);
            ps.setString(2, name);
            ps.setString(3, flagsToJson(flags));
            ps.setString(4, messagesToJson(messages));
            ps.executeUpdate();
        }
    }

    public Optional<Template> find(String serverId, String name) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT flags_json, messages_json FROM yap_admin_region_templates
                     WHERE server_id = ? AND name = ?
                     """)) {
            ps.setString(1, serverId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Template(
                        parseFlags(rs.getString("flags_json")),
                        parseMessages(rs.getString("messages_json"))));
            }
        }
    }

    public void delete(String serverId, String name) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM yap_admin_region_templates WHERE server_id = ? AND name = ?")) {
            ps.setString(1, serverId);
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    public record Template(Map<RegionFlag, FlagValue> flags, Map<RegionMessageKind, String> messages) {
    }

    private static String flagsToJson(Map<RegionFlag, FlagValue> flags) {
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (var e : flags.entrySet()) {
            if (i++ > 0) {
                sb.append(',');
            }
            String key = e.getKey().name().toLowerCase(Locale.ROOT).replace('_', '-');
            sb.append('"').append(key).append("\":\"")
                    .append(e.getValue().name().toLowerCase(Locale.ROOT)).append('"');
        }
        return sb.append('}').toString();
    }

    private static String messagesToJson(Map<RegionMessageKind, String> messages) {
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (var e : messages.entrySet()) {
            if (i++ > 0) {
                sb.append(',');
            }
            sb.append('"').append(e.getKey().name().toLowerCase(Locale.ROOT)).append("\":")
                    .append(quote(e.getValue()));
        }
        return sb.append('}').toString();
    }

    private static String quote(String s) {
        if (s == null) {
            return "null";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static Map<RegionFlag, FlagValue> parseFlags(String json) {
        Map<RegionFlag, FlagValue> out = new EnumMap<>(RegionFlag.class);
        for (var e : parseObject(json).entrySet()) {
            RegionFlag.parse(e.getKey()).ifPresent(f -> out.put(f, FlagValue.parse(e.getValue())));
        }
        return out;
    }

    private static Map<RegionMessageKind, String> parseMessages(String json) {
        Map<RegionMessageKind, String> out = new EnumMap<>(RegionMessageKind.class);
        for (var e : parseObject(json).entrySet()) {
            RegionMessageKind.parse(e.getKey()).ifPresent(k -> out.put(k, e.getValue()));
        }
        return out;
    }

    /** Minimal flat JSON object parser: {"k":"v",...} — values are strings only. */
    private static Map<String, String> parseObject(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        if (json == null || json.isBlank() || json.trim().equals("{}")) {
            return out;
        }
        String body = json.trim();
        if (body.startsWith("{")) {
            body = body.substring(1);
        }
        if (body.endsWith("}")) {
            body = body.substring(0, body.length() - 1);
        }
        int i = 0;
        while (i < body.length()) {
            while (i < body.length() && (body.charAt(i) == ',' || Character.isWhitespace(body.charAt(i)))) {
                i++;
            }
            if (i >= body.length()) {
                break;
            }
            if (body.charAt(i) != '"') {
                break;
            }
            int keyEnd = body.indexOf('"', i + 1);
            if (keyEnd < 0) {
                break;
            }
            String key = body.substring(i + 1, keyEnd);
            i = keyEnd + 1;
            while (i < body.length() && (body.charAt(i) == ':' || Character.isWhitespace(body.charAt(i)))) {
                i++;
            }
            if (i >= body.length() || body.charAt(i) != '"') {
                break;
            }
            StringBuilder val = new StringBuilder();
            i++;
            while (i < body.length()) {
                char c = body.charAt(i++);
                if (c == '\\' && i < body.length()) {
                    val.append(body.charAt(i++));
                } else if (c == '"') {
                    break;
                } else {
                    val.append(c);
                }
            }
            out.put(key, val.toString());
        }
        return out;
    }
}
