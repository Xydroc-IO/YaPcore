package com.yapcore.floodgate.paper;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Native Floodgate-class identity for Paper backends behind Velocity+Geyser.
 * No Floodgate jar required — copy {@code key.pem} from the proxy Floodgate plugin.
 */
public final class FloodgatePlugin extends JavaPlugin implements Listener {

    private FloodgateRuntime runtime;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Path key = getDataFolder().toPath().resolve(getConfig().getString("key-file", "key.pem"));
        runtime = new FloodgateRuntime(getLogger(), key);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("YaPFloodgate online — Velocity Bedrock identity without Floodgate jar");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(PlayerLoginEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();
        String host = event.getHostname();
        FloodgateRuntime.PlayerInfo info = runtime.remember(uuid, name, host);
        if (info != null) {
            getLogger().info("Recognized Bedrock via "
                    + (host != null && host.contains(FloodgateRuntime.IDENTIFIER) ? "hostname+key" : "UUID heuristic")
                    + ": " + info.name() + " xuid=" + Long.toUnsignedString(info.xuid()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        runtime.forget(event.getPlayer().getUniqueId());
    }

    /** API for other YaP plugins. */
    public boolean isBedrock(Player player) {
        return player != null && runtime.isBedrock(player.getUniqueId());
    }

    public boolean isBedrock(UUID uuid) {
        return runtime.isBedrock(uuid);
    }

    public String xuid(Player player) {
        return runtime.get(player.getUniqueId())
                .map(i -> Long.toUnsignedString(i.xuid()))
                .orElse(null);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("yapfloodgate")) {
            return false;
        }
        Player target;
        if (args.length >= 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("Player not found.");
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage("Usage: /yapfloodgate <player>");
            return true;
        }
        var info = runtime.get(target.getUniqueId());
        if (info.isEmpty() && !runtime.isBedrock(target.getUniqueId())) {
            sender.sendMessage(target.getName() + " is Java (not Floodgate Bedrock).");
            return true;
        }
        if (info.isPresent()) {
            var i = info.get();
            sender.sendMessage("Bedrock " + i.name()
                    + " xuid=" + Long.toUnsignedString(i.xuid())
                    + " linked=" + i.linked()
                    + " bedrockName=" + i.bedrockUsername());
        } else {
            sender.sendMessage(target.getName() + " looks Floodgate (UUID MSB=0) xuid="
                    + Long.toUnsignedString(target.getUniqueId().getLeastSignificantBits()));
        }
        return true;
    }
}
