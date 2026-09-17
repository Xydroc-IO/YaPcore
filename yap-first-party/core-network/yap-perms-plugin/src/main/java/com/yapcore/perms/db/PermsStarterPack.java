package com.yapcore.perms.db;

import com.yapcore.perms.PermsConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

final class PermsStarterPack {
    private final PermsRepository repo;
    private final PermsDatabase database;
    private final PermsConfig config;

    PermsStarterPack(PermsRepository repo, PermsDatabase database, PermsConfig config) {
        this.repo = repo;
        this.database = database;
        this.config = config;
    }

    public boolean starterPackApplied() throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT meta_value FROM yap_perms_meta WHERE meta_key='starter_pack_applied'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && "true".equalsIgnoreCase(rs.getString(1));
            }
        }
    }

    public void markStarterPackApplied() throws SQLException {
        try (Connection c = database.connection()) {
            markStarterPackAppliedInTx(c);
        }
    }

    /** Fill blank name/chat colors from YAML without rewriting prefixes or nodes. */
    public void backfillEmptyColorsFromConfig() throws SQLException {
        try (Connection c = database.connection()) {
            for (PermsConfig.GroupDef def : config.groups().values()) {
                String nameColor = def.nameColor() == null ? "" : def.nameColor();
                String chatColor = def.chatColor() == null ? "" : def.chatColor();
                if (nameColor.isBlank() && chatColor.isBlank()) {
                    continue;
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE yap_perms_groups SET "
                                + "name_color=CASE WHEN name_color='' THEN ? ELSE name_color END, "
                                + "chat_color=CASE WHEN chat_color='' THEN ? ELSE chat_color END "
                                + "WHERE name=?")) {
                    ps.setString(1, nameColor);
                    ps.setString(2, chatColor);
                    ps.setString(3, def.name());
                    ps.executeUpdate();
                }
            }
        }
    }

    /**
     * Add missing {@code starter-grants} allow nodes for groups that already exist.
     * Does not overwrite an existing allow/deny row (operator edits stay put).
     */
    public int backfillMissingStarterGrants() throws SQLException {
        int added = 0;
        try (Connection c = database.connection()) {
            for (Map.Entry<String, List<String>> grant : config.starterGrants().entrySet()) {
                String group = grant.getKey() == null ? "" : grant.getKey().trim().toLowerCase();
                if (group.isEmpty() || !groupExists(c, group)) {
                    continue;
                }
                for (String node : grant.getValue()) {
                    if (node == null || node.isBlank()) {
                        continue;
                    }
                    String n = node.trim();
                    if (hasGlobalGroupNode(c, group, n)) {
                        continue;
                    }
                    repo.setGroupNode(c, group, n, true);
                    added++;
                }
            }
        }
        return added;
    }

    private static boolean groupExists(Connection c, String group) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM yap_perms_groups WHERE name=? LIMIT 1")) {
            ps.setString(1, group);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean hasGlobalGroupNode(Connection c, String group, String node) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM yap_perms_group_nodes WHERE group_name=? AND node=? "
                        + "AND world='' AND server_ctx='' LIMIT 1")) {
            ps.setString(1, group);
            ps.setString(2, node);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void applyStarterPackFromConfig() throws SQLException {
        try (Connection c = database.connection()) {
            c.setAutoCommit(false);
            try {
                for (PermsConfig.GroupDef def : config.groups().values()) {
                    repo.upsertGroup(c, def.name(), def.weight(), def.prefix(), def.suffix(),
                            def.nameColor(), def.chatColor());
                    repo.replaceParents(c, def.name(), def.parents());
                }
                for (Map.Entry<String, List<String>> track : config.tracks().entrySet()) {
                    repo.replaceTrack(c, track.getKey(), track.getValue());
                }
                for (Map.Entry<String, List<String>> grant : config.starterGrants().entrySet()) {
                    for (String node : grant.getValue()) {
                        repo.setGroupNode(c, grant.getKey(), node, true);
                    }
                }
                for (Map.Entry<String, Map<String, Boolean>> editor : config.editorNodes().entrySet()) {
                    for (Map.Entry<String, Boolean> node : editor.getValue().entrySet()) {
                        repo.setGroupNode(c, editor.getKey(), node.getKey(), Boolean.TRUE.equals(node.getValue()));
                    }
                }
                markStarterPackAppliedInTx(c);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private void markStarterPackAppliedInTx(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(database.dialect().upsert(
                "yap_perms_meta",
                List.of("meta_key"),
                List.of("meta_key", "meta_value"),
                Map.of("meta_value", "EXCLUDED.meta_value")))) {
            ps.setString(1, "starter_pack_applied");
            ps.setString(2, "true");
            ps.executeUpdate();
        }
    }
}
