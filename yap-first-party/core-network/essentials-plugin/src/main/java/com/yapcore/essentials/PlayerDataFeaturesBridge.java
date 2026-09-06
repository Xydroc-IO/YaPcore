package com.yapcore.essentials;

import com.yapcore.playerdata.PlayerFeatures;
import com.yapcore.playerdata.PlayerFeaturesProvider;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Binds YaPPlayerData-backed QoL commands (bag, homes, economy, shops, …) onto Essentials.
 */
final class PlayerDataFeaturesBridge {

    private PlayerDataFeaturesBridge() {
    }

    static Optional<PlayerFeatures> attach(JavaPlugin host) {
        Logger log = host.getLogger();
        Optional<PlayerFeatures> found = PlayerFeaturesProvider.find();
        if (found.isEmpty()) {
            log.warning("YaPPlayerData not present — bag/homes/kits/mail/eco/shops/AH/claims/menu disabled.");
            bindMissing(host);
            return Optional.empty();
        }
        PlayerFeatures features = found.get();
        int bound = 0;
        for (String name : features.commandNames()) {
            PluginCommand cmd = host.getCommand(name);
            if (cmd == null) {
                log.warning("Missing QoL command in YaPEssentials plugin.yml: " + name);
                continue;
            }
            CommandExecutor exec = features.executor(name);
            TabCompleter tabs = features.tabCompleter(name);
            if (exec == null) {
                continue;
            }
            cmd.setExecutor(exec);
            if (tabs != null) {
                cmd.setTabCompleter(tabs);
            }
            bound++;
        }
        for (Listener listener : features.listeners()) {
            host.getServer().getPluginManager().registerEvents(listener, host);
        }
        features.onHostEnable(host);
        log.info("Bound " + bound + " PlayerData QoL command(s) + "
                + features.listeners().size() + " listener(s).");
        return Optional.of(features);
    }

    private static void bindMissing(JavaPlugin host) {
        String[] names = {
                "menu", "bal", "pay", "eco",
                "sethome", "home", "delhome", "homes",
                "setwarp", "delwarp", "warp", "warps",
                "kit", "kits", "createkit", "delkit", "showkit", "kitreset", "kitresetcooldown",
                "mail", "shop", "jobs", "ah", "claim", "bag"
        };
        CommandExecutor missing = (sender, command, label, args) -> {
            tellMissing(sender);
            return true;
        };
        TabCompleter empty = (sender, command, alias, args) -> List.of();
        for (String name : names) {
            PluginCommand cmd = host.getCommand(name);
            if (cmd == null) {
                continue;
            }
            cmd.setExecutor(missing);
            cmd.setTabCompleter(empty);
        }
    }

    private static void tellMissing(CommandSender sender) {
        sender.sendMessage("§cRequires YaPPlayerData (§fyap-playerdata.jar§c).");
    }
}
