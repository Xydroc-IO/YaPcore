package com.yapcore.perms.io;

import com.yapcore.perms.db.PermsRepository;
import com.yapcore.perms.engine.StoredNode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** YAML dump of groups / tracks / users — LuckPerms-class backup, not LP file format. */
public final class PermsDump {

    private PermsDump() {
    }

    public static void exportTo(File file, PermsRepository repo) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        Map<String, PermsRepository.GroupRow> groups = repo.loadAllGroups();
        for (PermsRepository.GroupRow group : groups.values()) {
            String base = "groups." + group.name();
            yaml.set(base + ".weight", group.weight());
            yaml.set(base + ".prefix", group.prefix());
            yaml.set(base + ".suffix", group.suffix());
            yaml.set(base + ".name-color", group.nameColor());
            yaml.set(base + ".chat-color", group.chatColor());
            yaml.set(base + ".parents", group.parents());
            writeNodes(yaml, base + ".nodes", group.nodes());
        }
        for (Map.Entry<String, List<String>> track : repo.loadTracks().entrySet()) {
            yaml.set("tracks." + track.getKey(), track.getValue());
        }
        int users = 0;
        for (PermsRepository.UserRow user : repo.listAllUsers()) {
            String base = "users." + user.uuid();
            yaml.set(base + ".name", user.name());
            yaml.set(base + ".primary", user.primaryGroup());
            yaml.set(base + ".parents", new ArrayList<>(user.extraGroups()));
            if (user.metaPrefix() != null) {
                yaml.set(base + ".meta.prefix", user.metaPrefix());
            }
            if (user.metaSuffix() != null) {
                yaml.set(base + ".meta.suffix", user.metaSuffix());
            }
            writeNodes(yaml, base + ".nodes", user.nodes());
            users++;
        }
        yaml.set("_meta.exported-at", Instant.now().toString());
        yaml.set("_meta.users", users);
        yaml.set("_meta.groups", groups.size());
        yaml.save(file);
    }

    /**
     * Flat per-group node map for the web rank editor.
     * Only global, non-expired nodes — world/temp rows stay in the full export.
     */
    public static void exportEditorSnapshot(File file, PermsRepository repo) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        Instant now = Instant.now();
        Map<String, PermsRepository.GroupRow> groups = repo.loadAllGroups();
        for (PermsRepository.GroupRow group : groups.values()) {
            int i = 0;
            for (StoredNode node : group.nodes()) {
                if (node.expired(now) || !node.world().isBlank() || !node.server().isBlank()) {
                    continue;
                }
                String path = "groups." + group.name() + "." + i++;
                yaml.set(path + ".node", node.node());
                yaml.set(path + ".value", node.value());
            }
        }
        yaml.set("exported-at", now.toString());
        yaml.set("source", "live");
        yaml.save(file);
    }

    public static int importFrom(File file, PermsRepository repo) throws Exception {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int applied = 0;
        ConfigurationSection groups = yaml.getConfigurationSection("groups");
        if (groups != null) {
            for (String name : groups.getKeys(false)) {
                ConfigurationSection g = groups.getConfigurationSection(name);
                if (g == null) {
                    continue;
                }
                repo.upsertGroup(name, g.getInt("weight", 0),
                        g.getString("prefix", ""), g.getString("suffix", ""),
                        g.getString("name-color", ""), g.getString("chat-color", ""));
                repo.replaceParents(name, g.getStringList("parents"));
                applied += importNodes(g.getConfigurationSection("nodes"),
                        (node, value, world, server, exp) ->
                                repo.setGroupNode(name, node, value, world, server, exp));
            }
        }
        ConfigurationSection tracks = yaml.getConfigurationSection("tracks");
        if (tracks != null) {
            for (String name : tracks.getKeys(false)) {
                repo.replaceTrack(name, tracks.getStringList(name));
                applied++;
            }
        }
        ConfigurationSection users = yaml.getConfigurationSection("users");
        if (users != null) {
            for (String key : users.getKeys(false)) {
                ConfigurationSection u = users.getConfigurationSection(key);
                if (u == null) {
                    continue;
                }
                UUID uuid = UUID.fromString(key);
                String playerName = u.getString("name", "unknown");
                repo.setPrimaryGroup(uuid, playerName, u.getString("primary", "default"));
                for (String parent : u.getStringList("parents")) {
                    repo.addUserParent(uuid, playerName, parent);
                }
                String prefix = u.getString("meta.prefix");
                String suffix = u.getString("meta.suffix");
                if (prefix != null || suffix != null) {
                    repo.setUserMeta(uuid, playerName, prefix == null ? "" : prefix,
                            suffix == null ? "" : suffix);
                }
                applied += importNodes(u.getConfigurationSection("nodes"),
                        (node, value, world, server, exp) ->
                                repo.setUserNode(uuid, playerName, node, value, world, server, exp));
            }
        }
        return applied;
    }

    private static void writeNodes(YamlConfiguration yaml, String path, List<StoredNode> nodes) {
        int i = 0;
        for (StoredNode node : nodes) {
            String p = path + "." + i++;
            yaml.set(p + ".node", node.node());
            yaml.set(p + ".value", node.value());
            if (!node.world().isBlank()) {
                yaml.set(p + ".world", node.world());
            }
            if (!node.server().isBlank()) {
                yaml.set(p + ".server", node.server());
            }
            if (node.expiresAt() != null) {
                yaml.set(p + ".expires", node.expiresAt().toString());
            }
        }
    }

    @FunctionalInterface
    private interface NodeWriter {
        void write(String node, boolean value, String world, String server, Instant expires) throws SQLException;
    }

    private static int importNodes(ConfigurationSection section, NodeWriter writer) throws SQLException {
        if (section == null) {
            return 0;
        }
        int n = 0;
        for (String key : section.getKeys(false)) {
            ConfigurationSection row = section.getConfigurationSection(key);
            if (row == null) {
                continue;
            }
            String node = row.getString("node", "");
            if (node.isBlank()) {
                continue;
            }
            Instant exp = null;
            String raw = row.getString("expires");
            if (raw != null && !raw.isBlank()) {
                exp = Instant.parse(raw);
            }
            writer.write(node, row.getBoolean("value", true),
                    row.getString("world", ""), row.getString("server", ""), exp);
            n++;
        }
        return n;
    }
}
