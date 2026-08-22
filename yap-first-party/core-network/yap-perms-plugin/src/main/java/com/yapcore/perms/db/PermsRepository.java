package com.yapcore.perms.db;

import com.yapcore.perms.PermsConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PermsRepository {

    public record GroupRow(String name, int weight, String prefix, String suffix, List<String> parents,
                           Map<String, Boolean> nodes) {
    }

    public record UserRow(UUID uuid, String name, String primaryGroup, Set<String> extraGroups,
                          Map<String, Boolean> nodes, String metaPrefix, String metaSuffix) {
    }

    private final PermsDatabase database;
    private final PermsConfig config;

    public PermsRepository(PermsDatabase database, PermsConfig config) {
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
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO yap_perms_meta (meta_key, meta_value) VALUES ('starter_pack_applied','true') "
                             + "ON DUPLICATE KEY UPDATE meta_value='true'")) {
            ps.executeUpdate();
        }
    }

    public void applyStarterPackFromConfig() throws SQLException {
        try (Connection c = database.connection()) {
            c.setAutoCommit(false);
            try {
                for (PermsConfig.GroupDef def : config.groups().values()) {
                    upsertGroup(c, def.name(), def.weight(), def.prefix(), def.suffix());
                    replaceParents(c, def.name(), def.parents());
                }
                for (Map.Entry<String, List<String>> track : config.tracks().entrySet()) {
                    replaceTrack(c, track.getKey(), track.getValue());
                }
                for (Map.Entry<String, List<String>> grant : config.starterGrants().entrySet()) {
                    for (String node : grant.getValue()) {
                        setGroupNode(c, grant.getKey(), node, true);
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
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO yap_perms_meta (meta_key, meta_value) VALUES ('starter_pack_applied','true') "
                        + "ON DUPLICATE KEY UPDATE meta_value='true'")) {
            ps.executeUpdate();
        }
    }

    public Map<String, GroupRow> loadAllGroups() throws SQLException {
        Map<String, GroupRow> out = new LinkedHashMap<>();
        try (Connection c = database.connection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT name, weight, prefix, suffix FROM yap_perms_groups")) {
            while (rs.next()) {
                String name = rs.getString("name").toLowerCase();
                out.put(name, new GroupRow(
                        name,
                        rs.getInt("weight"),
                        rs.getString("prefix"),
                        rs.getString("suffix"),
                        new ArrayList<>(),
                        new HashMap<>()));
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
             ResultSet rs = st.executeQuery("SELECT group_name, node, value FROM yap_perms_group_nodes")) {
            while (rs.next()) {
                GroupRow row = out.get(rs.getString("group_name").toLowerCase());
                if (row != null) {
                    row.nodes().put(rs.getString("node"), rs.getInt("value") == 1);
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

    public UserRow loadUser(UUID uuid, String name, String defaultGroup) throws SQLException {
        try (Connection c = database.connection()) {
            ensureUser(c, uuid, name, defaultGroup);
        }
        String primary = defaultGroup;
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT name, primary_group FROM yap_perms_users WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    primary = rs.getString("primary_group").toLowerCase();
                }
            }
        }
        Set<String> extraGroups = new LinkedHashSet<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT group_name FROM yap_perms_user_parents WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    extraGroups.add(rs.getString("group_name").toLowerCase());
                }
            }
        }
        Map<String, Boolean> nodes = new HashMap<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT node, value FROM yap_perms_user_nodes WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    nodes.put(rs.getString("node"), rs.getInt("value") == 1);
                }
            }
        }
        String metaPrefix = null;
        String metaSuffix = null;
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT prefix, suffix FROM yap_perms_user_meta WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    metaPrefix = rs.getString("prefix");
                    metaSuffix = rs.getString("suffix");
                }
            }
        }
        return new UserRow(uuid, name, primary, extraGroups, nodes, metaPrefix, metaSuffix);
    }

    public void ensureUser(UUID uuid, String name, String defaultGroup) throws SQLException {
        try (Connection c = database.connection()) {
            ensureUser(c, uuid, name, defaultGroup);
        }
    }

    private void ensureUser(Connection c, UUID uuid, String name, String defaultGroup) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO yap_perms_users (uuid, name, primary_group) VALUES (?,?,?) "
                        + "ON DUPLICATE KEY UPDATE name=VALUES(name)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, defaultGroup);
            ps.executeUpdate();
        }
    }

    public void setPrimaryGroup(UUID uuid, String name, String group) throws SQLException {
        ensureUser(uuid, name, config.defaultGroup());
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE yap_perms_users SET primary_group=? WHERE uuid=?")) {
            ps.setString(1, group.toLowerCase());
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
    }

    public void addUserGroup(UUID uuid, String name, String group) throws SQLException {
        ensureUser(uuid, name, config.defaultGroup());
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT IGNORE INTO yap_perms_user_parents (uuid, group_name) VALUES (?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, group.toLowerCase());
            ps.executeUpdate();
        }
    }

    public void setUserNode(UUID uuid, String name, String node, boolean value) throws SQLException {
        ensureUser(uuid, name, config.defaultGroup());
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO yap_perms_user_nodes (uuid, node, value) VALUES (?,?,?) "
                             + "ON DUPLICATE KEY UPDATE value=VALUES(value)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, node);
            ps.setInt(3, value ? 1 : 0);
            ps.executeUpdate();
        }
    }

    public void setGroupNode(String group, String node, boolean value) throws SQLException {
        try (Connection c = database.connection()) {
            setGroupNode(c, group, node, value);
        }
    }

    private void setGroupNode(Connection c, String group, String node, boolean value) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO yap_perms_group_nodes (group_name, node, value) VALUES (?,?,?) "
                        + "ON DUPLICATE KEY UPDATE value=VALUES(value)")) {
            ps.setString(1, group.toLowerCase());
            ps.setString(2, node);
            ps.setInt(3, value ? 1 : 0);
            ps.executeUpdate();
        }
    }

    public void upsertGroup(String name, int weight, String prefix, String suffix) throws SQLException {
        try (Connection c = database.connection()) {
            upsertGroup(c, name, weight, prefix, suffix);
        }
    }

    private void upsertGroup(Connection c, String name, int weight, String prefix, String suffix) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO yap_perms_groups (name, weight, prefix, suffix) VALUES (?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE weight=VALUES(weight), prefix=VALUES(prefix), suffix=VALUES(suffix)")) {
            ps.setString(1, name.toLowerCase());
            ps.setInt(2, weight);
            ps.setString(3, prefix);
            ps.setString(4, suffix);
            ps.executeUpdate();
        }
    }

    public void replaceParents(String group, List<String> parents) throws SQLException {
        try (Connection c = database.connection()) {
            replaceParents(c, group, parents);
        }
    }

    private void replaceParents(Connection c, String group, List<String> parents) throws SQLException {
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

    private void replaceTrack(Connection c, String track, List<String> groups) throws SQLException {
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

    public void addUserParent(UUID uuid, String name, String group) throws SQLException {
        ensureUser(uuid, name, config.defaultGroup());
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT IGNORE INTO yap_perms_user_parents (uuid, group_name) VALUES (?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, group.toLowerCase());
            ps.executeUpdate();
        }
    }

    public void removeUserParent(UUID uuid, String name, String group) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM yap_perms_user_parents WHERE uuid=? AND group_name=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, group.toLowerCase());
            ps.executeUpdate();
        }
    }

    public void setUserMeta(UUID uuid, String name, String prefix, String suffix) throws SQLException {
        ensureUser(uuid, name, config.defaultGroup());
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO yap_perms_user_meta (uuid, prefix, suffix) VALUES (?,?,?) "
                             + "ON DUPLICATE KEY UPDATE prefix=VALUES(prefix), suffix=VALUES(suffix)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, prefix);
            ps.setString(3, suffix);
            ps.executeUpdate();
        }
    }

    public void clearUserMeta(UUID uuid) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM yap_perms_user_meta WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    public Set<String> listEffectiveGroupNames(UUID uuid, String name) throws SQLException {
        UserRow user = loadUser(uuid, name, config.defaultGroup());
        Set<String> names = new LinkedHashSet<>();
        names.add(user.primaryGroup().toLowerCase());
        names.addAll(user.extraGroups());
        return names;
    }
}
