package com.yapcore.perms.db;

import com.yapcore.perms.PermsConfig;
import com.yapcore.perms.engine.StoredNode;
import com.yapcore.perms.db.PermsRepository.UserRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class PermsUserStore {
    private final PermsDatabase database;
    private final PermsConfig config;

    PermsUserStore(PermsDatabase database, PermsConfig config) {
        this.database = database;
        this.config = config;
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
        List<StoredNode> nodes = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT node, value, world, server_ctx, expires_at FROM yap_perms_user_nodes WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    nodes.add(PermsSql.readNode(rs));
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
        try (PreparedStatement ps = c.prepareStatement(database.dialect().upsert(
                "yap_perms_users",
                List.of("uuid"),
                List.of("uuid", "name", "primary_group"),
                Map.of("name", "EXCLUDED.name")))) {
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
             PreparedStatement ps = c.prepareStatement(database.dialect().insertIgnore(
                     "yap_perms_user_parents", List.of("uuid", "group_name")))) {
            ps.setString(1, uuid.toString());
            ps.setString(2, group.toLowerCase());
            ps.executeUpdate();
        }
    }

    public void setUserNode(UUID uuid, String name, String node, boolean value) throws SQLException {
        setUserNode(uuid, name, node, value, "", "", null);
    }

    public void setUserNode(UUID uuid, String name, String node, boolean value,
                            String world, String server, Instant expires) throws SQLException {
        ensureUser(uuid, name, config.defaultGroup());
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(database.dialect().upsert(
                     "yap_perms_user_nodes",
                     List.of("uuid", "node", "world", "server_ctx"),
                     List.of("uuid", "node", "value", "world", "server_ctx", "expires_at"),
                     Map.of("value", "EXCLUDED.value", "expires_at", "EXCLUDED.expires_at")))) {
            ps.setString(1, uuid.toString());
            ps.setString(2, node);
            ps.setInt(3, value ? 1 : 0);
            ps.setString(4, PermsSql.empty(world));
            ps.setString(5, PermsSql.empty(server));
            PermsSql.bindExpiry(ps, 6, expires);
            ps.executeUpdate();
        }
    }

    public void unsetUserNode(UUID uuid, String node, String world, String server) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM yap_perms_user_nodes WHERE uuid=? AND node=? AND world=? AND server_ctx=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, node);
            ps.setString(3, PermsSql.empty(world));
            ps.setString(4, PermsSql.empty(server));
            ps.executeUpdate();
        }
    }

    public void addUserParent(UUID uuid, String name, String group) throws SQLException {
        ensureUser(uuid, name, config.defaultGroup());
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(database.dialect().insertIgnore(
                     "yap_perms_user_parents", List.of("uuid", "group_name")))) {
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
             PreparedStatement ps = c.prepareStatement(database.dialect().upsert(
                     "yap_perms_user_meta",
                     List.of("uuid"),
                     List.of("uuid", "prefix", "suffix"),
                     Map.of("prefix", "EXCLUDED.prefix", "suffix", "EXCLUDED.suffix")))) {
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

    public List<UserRow> listAllUsers() throws SQLException {
        List<UserRow> out = new ArrayList<>();
        try (Connection c = database.connection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT uuid, name FROM yap_perms_users")) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String name = rs.getString("name");
                out.add(loadUser(uuid, name, config.defaultGroup()));
            }
        }
        return out;
    }
}
