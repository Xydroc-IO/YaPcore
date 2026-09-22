package com.yapcore.folia.bridge;

import com.yapcore.sched.YapSched;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * First-party Folia surface: status + apply server.properties view/simulation distance live.
 */
public final class FoliaBridgePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        YapSched.global(this, () -> {
            getLogger().info("YaP Folia bridge online (GlobalRegionScheduler)");
            applyDistancesFromServerProperties(null);
        });
        getLogger().info("Enabled — folia-supported first-party surface");
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabled");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName();
        if (FoliaBridgeMeta.STATUS_COMMAND.equalsIgnoreCase(name)) {
            if (args.length >= 1 && "viewdistance".equalsIgnoreCase(args[0])) {
                return handleViewDistance(sender, args);
            }
            sender.sendMessage("YaP Folia bridge OK | server=" + getServer().getName()
                    + " | players=" + getServer().getOnlinePlayers().size()
                    + " | view=" + describeDistances());
            return true;
        }
        if ("yapviewdistance".equalsIgnoreCase(name) || "viewdistance".equalsIgnoreCase(name)) {
            return handleViewDistance(sender, args);
        }
        return false;
    }

    private boolean handleViewDistance(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapbridge.viewdistance") && !sender.isOp()) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length >= 1 && "viewdistance".equalsIgnoreCase(args[0]) && args.length >= 2) {
            // /yapbridge viewdistance 32
            return applyExplicit(sender, args[1], args.length >= 3 ? args[2] : null);
        }
        if (args.length >= 1 && !"viewdistance".equalsIgnoreCase(args[0])) {
            // /yapviewdistance 32 [sim]
            return applyExplicit(sender, args[0], args.length >= 2 ? args[1] : null);
        }
        if (args.length == 0 || (args.length == 1 && "viewdistance".equalsIgnoreCase(args[0]))) {
            applyDistancesFromServerProperties(sender);
            return true;
        }
        sender.sendMessage("§eUsage: /yapviewdistance <view> [simulation] §7or /yapbridge viewdistance …");
        return true;
    }

    private boolean applyExplicit(CommandSender sender, String viewRaw, String simRaw) {
        int view;
        try {
            view = Integer.parseInt(viewRaw);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid view-distance.");
            return true;
        }
        Integer sim = null;
        if (simRaw != null) {
            try {
                sim = Integer.parseInt(simRaw);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid simulation-distance.");
                return true;
            }
        }
        applyDistances(view, sim != null ? sim : Math.min(view, 16), sender);
        return true;
    }

    private void applyDistancesFromServerProperties(CommandSender sender) {
        Path propsPath = getServer().getWorldContainer().toPath().resolve("server.properties");
        int view = 32;
        int sim = 16;
        if (Files.isRegularFile(propsPath)) {
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(propsPath)) {
                p.load(in);
            } catch (IOException e) {
                getLogger().warning("Could not read server.properties: " + e.getMessage());
            }
            view = parseBounded(p.getProperty("view-distance"), view, 2, 32);
            sim = parseBounded(p.getProperty("simulation-distance"), Math.min(view, 16), 2, 32);
        }
        applyDistances(view, sim, sender);
    }

    private void applyDistances(int viewChunks, int simChunks, CommandSender sender) {
        int view = Math.max(2, Math.min(32, viewChunks));
        int sim = Math.max(2, Math.min(view, simChunks));
        YapSched.global(this, () -> {
            for (World world : Bukkit.getWorlds()) {
                try {
                    world.setViewDistance(view);
                } catch (NoSuchMethodError | UnsupportedOperationException e) {
                    getLogger().fine("World.setViewDistance: " + e.getMessage());
                }
                try {
                    world.setSimulationDistance(sim);
                } catch (NoSuchMethodError | UnsupportedOperationException e) {
                    getLogger().fine("World.setSimulationDistance: " + e.getMessage());
                }
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    player.setViewDistance(view);
                } catch (NoSuchMethodError | UnsupportedOperationException e) {
                    getLogger().fine("Player.setViewDistance: " + e.getMessage());
                }
                try {
                    player.setSimulationDistance(sim);
                } catch (NoSuchMethodError | UnsupportedOperationException e) {
                    getLogger().fine("Player.setSimulationDistance: " + e.getMessage());
                }
            }
            String msg = "Applied view-distance=" + view + " simulation-distance=" + sim
                    + " worlds=" + Bukkit.getWorlds().size()
                    + " players=" + Bukkit.getOnlinePlayers().size();
            getLogger().info(msg);
            if (sender != null) {
                sender.sendMessage("§a" + msg);
            }
        });
    }

    private static int parseBounded(String raw, int fallback, int min, int max) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(raw.trim())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String describeDistances() {
        StringBuilder sb = new StringBuilder();
        for (World world : Bukkit.getWorlds()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            int v;
            int s;
            try {
                v = world.getViewDistance();
            } catch (NoSuchMethodError e) {
                v = -1;
            }
            try {
                s = world.getSimulationDistance();
            } catch (NoSuchMethodError e) {
                s = -1;
            }
            sb.append(world.getName()).append(" v=").append(v).append(" s=").append(s);
        }
        return sb.length() == 0 ? "n/a" : sb.toString();
    }
}
