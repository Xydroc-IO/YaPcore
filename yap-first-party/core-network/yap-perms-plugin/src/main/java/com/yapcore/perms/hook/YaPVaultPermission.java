package com.yapcore.perms.hook;

import com.yapcore.perms.PermissionNodes;
import com.yapcore.perms.PermsPlugin;
import com.yapcore.perms.db.PermsRepository;
import com.yapcore.perms.engine.StoredNode;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Vault Permission provider backed by YaPPerms (LuckPerms-class hook). */
public final class YaPVaultPermission extends Permission {

    private final PermsPlugin plugin;

    public YaPVaultPermission(PermsPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getServicesManager().register(Permission.class, this, plugin, ServicePriority.Highest);
        plugin.getLogger().info("Registered Vault Permission provider (YaPPerms)");
    }

    public void unregister() {
        Bukkit.getServicesManager().unregister(Permission.class, this);
    }

    @Override
    public String getName() {
        return "YaPPerms";
    }

    @Override
    public boolean isEnabled() {
        return plugin.isEnabled();
    }

    @Override
    public boolean hasSuperPermsCompat() {
        return true;
    }

    @Override
    public boolean hasGroupSupport() {
        return true;
    }

    @Override
    public boolean playerHas(String world, String player, String permission) {
        return playerHas(world, Bukkit.getOfflinePlayer(player), permission);
    }

    @Override
    public boolean playerHas(String world, OfflinePlayer player, String permission) {
        if (player == null || permission == null) {
            return false;
        }
        Player online = player.getPlayer();
        if (online != null) {
            return online.hasPermission(permission);
        }
        String ctxWorld = world == null ? "" : world;
        return PermissionNodes.has(
                plugin.resolve(player.getUniqueId(), nameOf(player), ctxWorld, plugin.config().serverContext())
                        .permissions(),
                permission);
    }

    @Override
    public boolean playerAdd(String world, String player, String permission) {
        return playerAdd(world, Bukkit.getOfflinePlayer(player), permission);
    }

    @Override
    public boolean playerAdd(String world, OfflinePlayer player, String permission) {
        if (player == null || permission == null) {
            return false;
        }
        try {
            plugin.repository().setUserNode(player.getUniqueId(), nameOf(player), permission, true,
                    world == null ? "" : world, plugin.config().serverContext(), null);
            plugin.refreshOnline(player.getUniqueId());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean playerRemove(String world, String player, String permission) {
        return playerRemove(world, Bukkit.getOfflinePlayer(player), permission);
    }

    @Override
    public boolean playerRemove(String world, OfflinePlayer player, String permission) {
        if (player == null || permission == null) {
            return false;
        }
        try {
            plugin.repository().unsetUserNode(player.getUniqueId(), permission,
                    world == null ? "" : world, plugin.config().serverContext());
            plugin.refreshOnline(player.getUniqueId());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean groupHas(String world, String group, String permission) {
        PermsRepository.GroupRow row = plugin.resolver().groups()
                .get(group == null ? "" : group.toLowerCase(Locale.ROOT));
        if (row == null) {
            return false;
        }
        Instant now = Instant.now();
        String ctxWorld = world == null ? "" : world;
        Map<String, Boolean> nodes = new LinkedHashMap<>();
        for (StoredNode node : row.nodes()) {
            if (node.applies(now, ctxWorld, plugin.config().serverContext())) {
                nodes.put(node.node(), node.value());
            }
        }
        return PermissionNodes.has(nodes, permission);
    }

    @Override
    public boolean groupAdd(String world, String group, String permission) {
        try {
            plugin.repository().setGroupNode(group, permission, true,
                    world == null ? "" : world, plugin.config().serverContext(), null);
            plugin.reloadAll();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean groupRemove(String world, String group, String permission) {
        try {
            plugin.repository().unsetGroupNode(group, permission,
                    world == null ? "" : world, plugin.config().serverContext());
            plugin.reloadAll();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean playerInGroup(String world, String player, String group) {
        return playerInGroup(world, Bukkit.getOfflinePlayer(player), group);
    }

    @Override
    public boolean playerInGroup(String world, OfflinePlayer player, String group) {
        if (player == null || group == null) {
            return false;
        }
        return plugin.getGroups(player.getUniqueId()).stream()
                .anyMatch(g -> g.equalsIgnoreCase(group));
    }

    @Override
    public boolean playerAddGroup(String world, String player, String group) {
        return playerAddGroup(world, Bukkit.getOfflinePlayer(player), group);
    }

    @Override
    public boolean playerAddGroup(String world, OfflinePlayer player, String group) {
        if (player == null || group == null) {
            return false;
        }
        try {
            plugin.repository().addUserParent(player.getUniqueId(), nameOf(player), group);
            plugin.refreshOnline(player.getUniqueId());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean playerRemoveGroup(String world, String player, String group) {
        return playerRemoveGroup(world, Bukkit.getOfflinePlayer(player), group);
    }

    @Override
    public boolean playerRemoveGroup(String world, OfflinePlayer player, String group) {
        if (player == null || group == null) {
            return false;
        }
        try {
            plugin.repository().removeUserParent(player.getUniqueId(), nameOf(player), group);
            plugin.refreshOnline(player.getUniqueId());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String[] getPlayerGroups(String world, String player) {
        return getPlayerGroups(world, Bukkit.getOfflinePlayer(player));
    }

    @Override
    public String[] getPlayerGroups(String world, OfflinePlayer player) {
        if (player == null) {
            return new String[0];
        }
        return plugin.getGroups(player.getUniqueId()).toArray(String[]::new);
    }

    @Override
    public String getPrimaryGroup(String world, String player) {
        return getPrimaryGroup(world, Bukkit.getOfflinePlayer(player));
    }

    @Override
    public String getPrimaryGroup(String world, OfflinePlayer player) {
        if (player == null) {
            return plugin.config().defaultGroup();
        }
        return plugin.getPrimaryGroup(player.getUniqueId()).orElse(plugin.config().defaultGroup());
    }

    @Override
    public String[] getGroups() {
        return plugin.resolver().groups().keySet().toArray(String[]::new);
    }

    private static String nameOf(OfflinePlayer player) {
        return player.getName() != null ? player.getName() : "unknown";
    }
}
