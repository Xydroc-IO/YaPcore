package com.yapcore.perms.db;

import com.yapcore.perms.PermsConfig;
import com.yapcore.perms.engine.StoredNode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static java.util.Map.entry;

public final class PermsRepository {

    public record GroupRow(String name, int weight, String prefix, String suffix,
                           String nameColor, String chatColor, List<String> parents,
                           List<StoredNode> nodes) {
        public GroupRow {
            nameColor = nameColor == null ? "" : nameColor;
            chatColor = chatColor == null ? "" : chatColor;
        }
    }

    public record UserRow(UUID uuid, String name, String primaryGroup, Set<String> extraGroups,
                          List<StoredNode> nodes, String metaPrefix, String metaSuffix) {
    }

    private final PermsDatabase database;
    private final PermsConfig config;
    private final PermsUserStore users;
    private final PermsStarterPack starter;

    public PermsRepository(PermsDatabase database, PermsConfig config) {
        this.database = database;
        this.config = config;
        this.users = new PermsUserStore(database, config);
        this.starter = new PermsStarterPack(this, database, config);
    }

    public boolean starterPackApplied() throws SQLException {
        return starter.starterPackApplied();
    }

    public void markStarterPackApplied() throws SQLException {
        starter.markStarterPackApplied();
    }

    public void backfillEmptyColorsFromConfig() throws SQLException {
        starter.backfillEmptyColorsFromConfig();
    }

    public void applyStarterPackFromConfig() throws SQLException {
        starter.applyStarterPackFromConfig();
    }

    public UserRow loadUser(UUID uuid, String name, String defaultGroup) throws SQLException {
        return users.loadUser(uuid, name, defaultGroup);
    }

    public void ensureUser(UUID uuid, String name, String defaultGroup) throws SQLException {
        users.ensureUser(uuid, name, defaultGroup);
    }

    public void setPrimaryGroup(UUID uuid, String name, String group) throws SQLException {
        users.setPrimaryGroup(uuid, name, group);
    }

    public void addUserGroup(UUID uuid, String name, String group) throws SQLException {
        users.addUserGroup(uuid, name, group);
    }

    public void setUserNode(UUID uuid, String name, String node, boolean value) throws SQLException {
        users.setUserNode(uuid, name, node, value);
    }

    public void setUserNode(UUID uuid, String name, String node, boolean value,
                            String world, String server, Instant expires) throws SQLException {
        users.setUserNode(uuid, name, node, value, world, server, expires);
    }

    public void unsetUserNode(UUID uuid, String node, String world, String server) throws SQLException {
        users.unsetUserNode(uuid, node, world, server);
    }

    public void addUserParent(UUID uuid, String name, String group) throws SQLException {
        users.addUserParent(uuid, name, group);
    }

    public void removeUserParent(UUID uuid, String name, String group) throws SQLException {
        users.removeUserParent(uuid, name, group);
    }

    public void setUserMeta(UUID uuid, String name, String prefix, String suffix) throws SQLException {
        users.setUserMeta(uuid, name, prefix, suffix);
    }

    public void clearUserMeta(UUID uuid) throws SQLException {
        users.clearUserMeta(uuid);
    }

    public Set<String> listEffectiveGroupNames(UUID uuid, String name) throws SQLException {
        return users.listEffectiveGroupNames(uuid, name);
    }

    public List<UserRow> listAllUsers() throws SQLException {
        return users.listAllUsers();
    }

    public Map<String, GroupRow> loadAllGroups() throws SQLException {
        Map<String, GroupRow> out = new LinkedHashMap<>();
        try (Connection c = database.connection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT name, weight, prefix, suffix, name_color, chat_color FROM yap_perms_groups")) {
            while (rs.next()) {
                String name = rs.getString("name").toLowerCase();
                out.put(name, new GroupRow(
                        name,
                        rs.getInt("weight"),
                        rs.getString("prefix"),
                        rs.getString("suffix"),
                        rs.getString("name_color"),
                        rs.getString("chat_color"),
                        new ArrayList<>(),
                        new ArrayList<>()));
            }
        }
        if (out.isEmpty()) {
            return out;
        }
        try (Connection c = database.connection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT group_name, parent_name FROM yap_perms_group_parents")) {
            while (rs.next()) {
                GroupRow row = out.get(rs.getString("group_name").toLowerCase());
                if (row != null) {
                    row.parents().add(rs.getString("parent_name").toLowerCase());
                }
            }
        }
        try (Connection c = database.connection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT group_name, node, value, world, server_ctx, expires_at FROM yap_perms_group_nodes")) {
            while (rs.next()) {
                GroupRow row = out.get(rs.getString("group_name").toLowerCase());
                if (row != null) {
                    row.nodes().add(PermsSql.readNode(rs));
                }
            }
        }
        return out;
    }

    public Map<String, List<String>> loadTracks() throws SQLException {
        Map<String, List<String>> out = new LinkedHashMap<>();
        try (Connection c = database.connection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT name, position, group_name FROM yap_perms_tracks ORDER BY name, position")) {
            while (rs.next()) {
                String track = rs.getString("name").toLowerCase();
                out.computeIfAbsent(track, k -> new ArrayList<>()).add(rs.getString("group_name").toLowerCase());
            }
        }
        return out;
    }
    public void setGroupNode(String group, String node, boolean value) throws SQLException {
        setGroupNode(group, node, value, "", "", null);
    }

    public void setGroupNode(String group, String node, boolean value,
                             String world, String server, Instant expires) throws SQLException {
        try (Connection c = database.connection()) {
            setGroupNode(c, group, node, value, world, server, expires);
        }
    }

    void setGroupNode(Connection c, String group, String node, boolean value) throws SQLException {
        setGroupNode(c, group, node, value, "", "", null);
    }

    void setGroupNode(Connection c, String group, String node, boolean value,
                              String world, String server, Instant expires) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(database.dialect().upsert(
                "yap_perms_group_nodes",
                List.of("group_name", "node", "world", "server_ctx"),
                List.of("group_name", "node", "value", "world", "server_ctx", "expires_at"),
                Map.of("value", "EXCLUDED.value", "expires_at", "EXCLUDED.expires_at")))) {
            ps.setString(1, group.toLowerCase());
            ps.setString(2, node);
            ps.setInt(3, value ? 1 : 0);
            ps.setString(4, PermsSql.empty(world));
            ps.setString(5, PermsSql.empty(server));
            PermsSql.bindExpiry(ps, 6, expires);
            ps.executeUpdate();
        }
    }

    public void unsetGroupNode(String group, String node, String world, String server) throws SQLException {
        try (Connection c = database.connection()) {
            unsetGroupNode(c, group, node, world, server);
        }
    }

    void unsetGroupNode(Connection c, String group, String node, String world, String server)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM yap_perms_group_nodes WHERE group_name=? AND node=? AND world=? AND server_ctx=?")) {
            ps.setString(1, group.toLowerCase());
            ps.setString(2, node);
            ps.setString(3, PermsSql.empty(world));
            ps.setString(4, PermsSql.empty(server));
            ps.executeUpdate();
        }
    }

    /** Apply a web-editor batch: unset first, then allow/deny. Global context only. */
    public void applyEditorBatch(String group, List<String> allow, List<String> deny, List<String> unset)
            throws SQLException {
        String key = group.toLowerCase();
        try (Connection c = database.connection()) {
            c.setAutoCommit(false);
            try {
                if (unset != null) {
                    for (String node : unset) {
                        if (node != null && !node.isBlank()) {
                            unsetGroupNode(c, key, node.trim(), "", "");
                        }
                    }
                }
                if (allow != null) {
                    for (String node : allow) {
                        if (node != null && !node.isBlank()) {
                            setGroupNode(c, key, node.trim(), true);
                        }
                    }
                }
                if (deny != null) {
                    for (String node : deny) {
                        if (node != null && !node.isBlank()) {
                            setGroupNode(c, key, node.trim(), false);
                        }
                    }
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public void upsertGroup(String name, int weight, String prefix, String suffix) throws SQLException {
        upsertGroup(name, weight, prefix, suffix, "", "");
    }

    public void upsertGroup(String name, int weight, String prefix, String suffix,
                            String nameColor, String chatColor) throws SQLException {
        try (Connection c = database.connection()) {
            upsertGroup(c, name, weight, prefix, suffix, nameColor, chatColor);
        }
    }

    void upsertGroup(Connection c, String name, int weight, String prefix, String suffix,
                             String nameColor, String chatColor) throws SQLException {
        String sql = database.dialect().upsert(
                "yap_perms_groups",
                List.of("name"),
                List.of("name", "weight", "prefix", "suffix", "name_color", "chat_color"),
                Map.ofEntries(
                        entry("weight", "EXCLUDED.weight"),
                        entry("prefix", "EXCLUDED.prefix"),
                        entry("suffix", "EXCLUDED.suffix"),
                        entry("name_color", "EXCLUDED.name_color"),
                        entry("chat_color", "EXCLUDED.chat_color")));
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name.toLowerCase());
            ps.setInt(2, weight);
            ps.setString(3, prefix == null ? "" : prefix);
            ps.setString(4, suffix == null ? "" : suffix);
            ps.setString(5, nameColor == null ? "" : nameColor);
            ps.setString(6, chatColor == null ? "" : chatColor);
            ps.executeUpdate();
        }
    }

    public void replaceParents(String group, List<String> parents) throws SQLException {
        try (Connection c = database.connection()) {
            replaceParents(c, group, parents);
        }
    }

    void replaceParents(Connection c, String group, List<String> parents) throws SQLException {
        try (PreparedStatement del = c.prepareStatement("DELETE FROM yap_perms_group_parents WHERE group_name=?")) {
            del.setString(1, group.toLowerCase());
            del.executeUpdate();
        }
        try (PreparedStatement ins = c.prepareStatement(
                "INSERT INTO yap_perms_group_parents (group_name, parent_name) VALUES (?,?)")) {
            for (String parent : parents) {
                ins.setString(1, group.toLowerCase());
                ins.setString(2, parent.toLowerCase());
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    public void replaceTrack(String track, List<String> groups) throws SQLException {
        try (Connection c = database.connection()) {
            replaceTrack(c, track, groups);
        }
    }

    void replaceTrack(Connection c, String track, List<String> groups) throws SQLException {
        try (PreparedStatement del = c.prepareStatement("DELETE FROM yap_perms_tracks WHERE name=?")) {
            del.setString(1, track.toLowerCase());
            del.executeUpdate();
        }
        try (PreparedStatement ins = c.prepareStatement(
                "INSERT INTO yap_perms_tracks (name, position, group_name) VALUES (?,?,?)")) {
            int pos = 0;
            for (String group : groups) {
                ins.setString(1, track.toLowerCase());
                ins.setInt(2, pos++);
                ins.setString(3, group.toLowerCase());
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    public Optional<String> trackStep(UUID uuid, String name, String track, int delta) throws SQLException {
        Map<String, List<String>> tracks = loadTracks();
        List<String> groups = tracks.get(track.toLowerCase());
        if (groups == null || groups.isEmpty()) {
            return Optional.empty();
        }
        UserRow user = loadUser(uuid, name, config.defaultGroup());
        String current = user.primaryGroup();
        int idx = groups.indexOf(current);
        if (idx < 0) {
            idx = 0;
        }
        int next = idx + delta;
        if (next < 0 || next >= groups.size()) {
            return Optional.empty();
        }
        String target = groups.get(next);
        setPrimaryGroup(uuid, name, target);
        return Optional.of(target);
    }

    public void deleteGroup(String group) throws SQLException {
        String g = group.toLowerCase();
        try (Connection c = database.connection()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM yap_perms_group_nodes WHERE group_name=?")) {
                ps.setString(1, g);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM yap_perms_group_parents WHERE group_name=? OR parent_name=?")) {
                ps.setString(1, g);
                ps.setString(2, g);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM yap_perms_groups WHERE name=?")) {
                ps.setString(1, g);
                ps.executeUpdate();
            }
        }
    }
    public void addGroupParent(String group, String parent) throws SQLException {
        Map<String, GroupRow> all = loadAllGroups();
        GroupRow row = all.get(group.toLowerCase());
        List<String> parents = row == null ? new ArrayList<>() : new ArrayList<>(row.parents());
        String p = parent.toLowerCase();
        if (!parents.contains(p)) {
            parents.add(p);
        }
        replaceParents(group, parents);
    }

    public void removeGroupParent(String group, String parent) throws SQLException {
        Map<String, GroupRow> all = loadAllGroups();
        GroupRow row = all.get(group.toLowerCase());
        if (row == null) {
            return;
        }
        List<String> parents = new ArrayList<>(row.parents());
        parents.remove(parent.toLowerCase());
        replaceParents(group, parents);
    }

    public void deleteTrack(String track) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM yap_perms_tracks WHERE name=?")) {
            ps.setString(1, track.toLowerCase());
            ps.executeUpdate();
        }
    }

    public int purgeExpired() throws SQLException {
        int n = 0;
        String now = database.dialect().nowFn();
        try (Connection c = database.connection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM yap_perms_user_nodes WHERE expires_at IS NOT NULL AND expires_at <= " + now)) {
                n += ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM yap_perms_group_nodes WHERE expires_at IS NOT NULL AND expires_at <= " + now)) {
                n += ps.executeUpdate();
            }
        }
        return n;
    }
}
