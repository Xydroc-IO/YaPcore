package com.yapcore.essentials;

import com.yapcore.playerdata.PlayerFeatures;
import com.yapcore.playerdata.PlayerFeaturesProvider;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Binds YaPPlayerData QoL commands and YaPClaims {@code /claim} onto Essentials.
 * Claims types are loaded reflectively so a missing yap-claims.jar cannot crash enable.
 */
final class PlayerDataFeaturesBridge {

    private PlayerDataFeaturesBridge() {
    }

    static Optional<PlayerFeatures> attach(JavaPlugin host) {
        Logger log = host.getLogger();
        Optional<PlayerFeatures> found = PlayerFeaturesProvider.find();
        Optional<Object> claims = findClaimFeatures();

        if (found.isEmpty() && claims.isEmpty()) {
            log.warning("YaPPlayerData not present — bag/homes/kits/mail/eco/shops/AH/menu disabled.");
            log.warning("YaPClaims not present — /claim disabled.");
            bindMissing(host, true, true);
            return Optional.empty();
        }

        if (found.isEmpty()) {
            log.warning("YaPPlayerData not present — bag/homes/kits/mail/eco/shops/AH/menu disabled.");
            bindMissing(host, true, false);
        } else {
            PlayerFeatures features = found.get();
            int bound = 0;
            for (String name : features.commandNames()) {
                if ("claim".equals(name) && claims.isPresent()) {
                    continue;
                }
                if (bindOne(host, log, name, features.executor(name), features.tabCompleter(name))) {
                    bound++;
                }
            }
            for (Listener listener : features.listeners()) {
                host.getServer().getPluginManager().registerEvents(listener, host);
            }
            features.onHostEnable(host);
            log.info("Bound " + bound + " PlayerData QoL command(s) + "
                    + features.listeners().size() + " listener(s).");
        }

        if (claims.isPresent()) {
            bindClaimFeatures(host, log, claims.get());
        } else {
            bindMissingClaim(host);
            log.warning("YaPClaims not present — /claim requires yap-claims.jar.");
        }

        return found;
    }

    private static Optional<Object> findClaimFeatures() {
        try {
            Class<?> provider = Class.forName("com.yapcore.claims.ClaimFeaturesProvider");
            Object opt = provider.getMethod("find").invoke(null);
            if (opt instanceof Optional<?> optional && optional.isPresent()) {
                return Optional.of(optional.get());
            }
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            // YaPClaims soft-dep
        } catch (ReflectiveOperationException ignored) {
            // API drift
        }
        return Optional.empty();
    }

    private static void bindClaimFeatures(JavaPlugin host, Logger log, Object cf) {
        try {
            Object rawNames = cf.getClass().getMethod("commandNames").invoke(cf);
            java.util.Collection<?> names;
            if (rawNames instanceof java.util.Collection<?> c) {
                names = c;
            } else {
                log.warning("YaPClaims commandNames() returned unexpected type");
                bindMissingClaim(host);
                return;
            }
            int bound = 0;
            for (Object nameObj : names) {
                if (!(nameObj instanceof String name)) {
                    continue;
                }
                CommandExecutor exec = (CommandExecutor) cf.getClass()
                        .getMethod("executor", String.class).invoke(cf, name);
                TabCompleter tabs = (TabCompleter) cf.getClass()
                        .getMethod("tabCompleter", String.class).invoke(cf, name);
                if (bindOne(host, log, name, exec, tabs)) {
                    bound++;
                }
            }
            Object rawListeners = cf.getClass().getMethod("listeners").invoke(cf);
            int listenerCount = 0;
            if (rawListeners instanceof java.util.Collection<?> listeners) {
                for (Object listener : listeners) {
                    if (listener instanceof Listener l) {
                        host.getServer().getPluginManager().registerEvents(l, host);
                        listenerCount++;
                    }
                }
            }
            cf.getClass().getMethod("onHostEnable", JavaPlugin.class).invoke(cf, host);
            log.info("Bound " + bound + " Claims command(s) + " + listenerCount + " listener(s).");
        } catch (ReflectiveOperationException e) {
            log.warning("YaPClaims bridge failed: " + e.getMessage());
            bindMissingClaim(host);
        }
    }

    private static boolean bindOne(JavaPlugin host, Logger log, String name,
                                   CommandExecutor exec, TabCompleter tabs) {
        PluginCommand cmd = host.getCommand(name);
        if (cmd == null) {
            log.warning("Missing QoL command in YaPEssentials plugin.yml: " + name);
            return false;
        }
        if (exec == null) {
            return false;
        }
        cmd.setExecutor(exec);
        if (tabs != null) {
            cmd.setTabCompleter(tabs);
        }
        return true;
    }

    private static void bindMissing(JavaPlugin host, boolean playerDataMissing, boolean claimsMissing) {
        Set<String> names = new LinkedHashSet<>(List.of(
                "menu", "bal", "pay", "eco",
                "sethome", "home", "delhome", "homes",
                "setwarp", "delwarp", "warp", "warps",
                "kit", "kits", "createkit", "delkit", "showkit", "kitreset", "kitresetcooldown",
                "mail", "shop", "jobs", "ah", "bag"
        ));
        if (claimsMissing) {
            names.add("claim");
        }
        CommandExecutor missingPd = (sender, command, label, args) -> {
            tellMissingPlayerData(sender);
            return true;
        };
        CommandExecutor missingClaim = (sender, command, label, args) -> {
            tellMissingClaims(sender);
            return true;
        };
        TabCompleter empty = (sender, command, alias, args) -> List.of();
        for (String name : names) {
            PluginCommand cmd = host.getCommand(name);
            if (cmd == null) {
                continue;
            }
            if ("claim".equals(name)) {
                cmd.setExecutor(missingClaim);
            } else if (playerDataMissing) {
                cmd.setExecutor(missingPd);
            } else {
                continue;
            }
            cmd.setTabCompleter(empty);
        }
    }

    private static void bindMissingClaim(JavaPlugin host) {
        PluginCommand cmd = host.getCommand("claim");
        if (cmd == null) {
            return;
        }
        cmd.setExecutor((sender, command, label, args) -> {
            tellMissingClaims(sender);
            return true;
        });
        cmd.setTabCompleter((sender, command, alias, args) -> List.of());
    }

    private static void tellMissingPlayerData(CommandSender sender) {
        sender.sendMessage("§cRequires YaPPlayerData (§fyap-playerdata.jar§c).");
    }

    private static void tellMissingClaims(CommandSender sender) {
        sender.sendMessage("§cRequires YaPClaims (§fyap-claims.jar§c).");
    }
}
