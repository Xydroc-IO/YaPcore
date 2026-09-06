package com.yapcore.factions.integration;

import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.logging.Level;

/** Soft hooks for YaPPerms prefix meta and YaPDiscord role grant/revoke. */
public final class FactionPerkIntegration {

    private FactionPerkIntegration() {
    }

    public static void applyPrefix(JavaPlugin plugin, UUID playerId, String tag, String format) {
        String name = resolveName(playerId);
        if (name == null) {
            return;
        }
        String prefix = format.replace("%tag%", tag == null ? "" : tag);
        setMetaDirect(plugin, playerId, name, prefix, "");
        refreshTab(plugin, playerId);
    }

    public static void clearPrefix(JavaPlugin plugin, UUID playerId) {
        String name = resolveName(playerId);
        if (name == null) {
            return;
        }
        clearMetaDirect(plugin, playerId, name);
        refreshTab(plugin, playerId);
    }

    public static void syncDiscordRole(JavaPlugin plugin, UUID playerId, String roleSnowflake, boolean grant) {
        if (roleSnowflake == null || roleSnowflake.isBlank()) {
            return;
        }
        Plugin discord = Bukkit.getPluginManager().getPlugin("YaPDiscord");
        if (discord == null || !discord.isEnabled()) {
            return;
        }
        try {
            Object linkService = discord.getClass().getMethod("linkService").invoke(discord);
            if (linkService == null) {
                return;
            }
            @SuppressWarnings("unchecked")
            var linkOpt = (java.util.Optional<?>) linkService.getClass()
                    .getMethod("findByMcUuid", UUID.class)
                    .invoke(linkService, playerId);
            if (linkOpt == null || linkOpt.isEmpty()) {
                return;
            }
            Object link = linkOpt.get();
            String discordId = (String) link.getClass().getMethod("discordId").invoke(link);
            Object bot = discord.getClass().getMethod("bot").invoke(discord);
            if (bot == null) {
                return;
            }
            if (grant) {
                bot.getClass().getMethod("grantRole", String.class, String.class)
                        .invoke(bot, discordId, roleSnowflake);
            } else {
                bot.getClass().getMethod("revokeRole", String.class, String.class)
                        .invoke(bot, discordId, roleSnowflake);
            }
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().log(Level.FINE, "faction discord role sync skipped", e);
        }
    }

    /**
     * Called after Discord account link / resync so existing faction members get their role.
     */
    public static void syncDiscordRoleForLinkedPlayer(UUID playerId) {
        Plugin factionsPlugin = Bukkit.getPluginManager().getPlugin("YaPFactions");
        if (!(factionsPlugin instanceof JavaPlugin jp) || !factionsPlugin.isEnabled()) {
            return;
        }
        try {
            Object config = factionsPlugin.getClass().getMethod("factionsConfig").invoke(factionsPlugin);
            if (config == null) {
                return;
            }
            boolean enabled = (Boolean) config.getClass().getMethod("enabled").invoke(config);
            boolean roleSync = (Boolean) config.getClass().getMethod("discordRoleSync").invoke(config);
            if (!enabled || !roleSync) {
                return;
            }
            @SuppressWarnings("unchecked")
            var roles = (java.util.Map<String, String>) config.getClass().getMethod("discordRoles").invoke(config);
            Object service = factionsPlugin.getClass().getMethod("factionService").invoke(factionsPlugin);
            if (service == null || roles == null || roles.isEmpty()) {
                return;
            }
            @SuppressWarnings("unchecked")
            var factionOpt = (java.util.Optional<?>) service.getClass()
                    .getMethod("findByPlayer", UUID.class)
                    .invoke(service, playerId);
            if (factionOpt == null || factionOpt.isEmpty()) {
                return;
            }
            Object faction = factionOpt.get();
            String tag = (String) faction.getClass().getMethod("tag").invoke(faction);
            String roleId = roles.get(tag);
            syncDiscordRole(jp, playerId, roleId, true);
        } catch (ReflectiveOperationException e) {
            factionsPlugin.getLogger().log(Level.FINE, "link-time faction discord sync skipped", e);
        }
    }

    private static String resolveName(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            return player.getName();
        }
        String name = Bukkit.getOfflinePlayer(playerId).getName();
        return name == null || name.isBlank() ? null : name;
    }

    private static void setMetaDirect(JavaPlugin plugin, UUID uuid, String name, String prefix, String suffix) {
        Plugin perms = Bukkit.getPluginManager().getPlugin("YaPPerms");
        if (perms == null || !perms.isEnabled()) {
            return;
        }
        YapSched.async(plugin, () -> {
            try {
                Object repo = perms.getClass().getMethod("repository").invoke(perms);
                repo.getClass().getMethod("setUserMeta", UUID.class, String.class, String.class, String.class)
                        .invoke(repo, uuid, name, prefix, suffix);
                Player online = Bukkit.getPlayer(uuid);
                if (online != null && online.isOnline()) {
                    YapSched.global(plugin, () -> {
                        try {
                            perms.getClass().getMethod("refresh", Player.class).invoke(perms, online);
                        } catch (ReflectiveOperationException ignored) {
                            // optional
                        }
                    });
                }
            } catch (ReflectiveOperationException e) {
                plugin.getLogger().log(Level.FINE, "setUserMeta via YaPPerms failed", e);
            }
        });
    }

    private static void clearMetaDirect(JavaPlugin plugin, UUID uuid, String name) {
        Plugin perms = Bukkit.getPluginManager().getPlugin("YaPPerms");
        if (perms == null || !perms.isEnabled()) {
            return;
        }
        YapSched.async(plugin, () -> {
            try {
                Object repo = perms.getClass().getMethod("repository").invoke(perms);
                repo.getClass().getMethod("clearUserMeta", UUID.class).invoke(repo, uuid);
                Player online = Bukkit.getPlayer(uuid);
                if (online != null && online.isOnline()) {
                    YapSched.global(plugin, () -> {
                        try {
                            perms.getClass().getMethod("refresh", Player.class).invoke(perms, online);
                        } catch (ReflectiveOperationException ignored) {
                            // optional
                        }
                    });
                }
            } catch (ReflectiveOperationException e) {
                plugin.getLogger().log(Level.FINE, "clearUserMeta via YaPPerms failed", e);
            }
        });
    }

    private static void refreshTab(JavaPlugin plugin, UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        try {
            Class<?> tabServiceClass = Class.forName("com.yapcore.tab.TabService");
            var registration = Bukkit.getServicesManager().getRegistration(tabServiceClass);
            if (registration == null) {
                return;
            }
            Object svc = registration.getProvider();
            YapSched.entity(plugin, player, () -> {
                try {
                    svc.getClass().getMethod("refresh", Player.class).invoke(svc, player);
                } catch (ReflectiveOperationException ignored) {
                    // optional
                }
            });
        } catch (ClassNotFoundException ignored) {
            // optional
        }
    }
}
