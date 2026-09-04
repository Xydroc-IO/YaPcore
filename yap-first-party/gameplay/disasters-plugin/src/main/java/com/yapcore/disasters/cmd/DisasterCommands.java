package com.yapcore.disasters.cmd;

import com.yapcore.disasters.DisasterType;
import com.yapcore.disasters.DisastersPlugin;
import com.yapcore.disasters.RandomEventScheduler;
import com.yapcore.disasters.SkyWeather;
import com.yapcore.disasters.VolcanoSite;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public final class DisasterCommands implements CommandExecutor, TabCompleter {

    private final DisastersPlugin plugin;

    public DisasterCommands(DisastersPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yapdisasters.use")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (!plugin.config().enabled()) {
            sender.sendMessage("§cYaPDisasters is disabled in config.");
            return true;
        }
        if (args.length == 0 || "gui".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§e/yapdisaster <type|stop|random|site|reload> …");
                return true;
            }
            plugin.gui().open(player);
            return true;
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        if ("reload".equals(first)) {
            if (!sender.hasPermission("yapdisasters.admin")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            plugin.reloadDisasters();
            sender.sendMessage("§aYaPDisasters reloaded. " + plugin.randomEvents().statusLine()
                    + " · sites=" + plugin.volcanoSites().all().size());
            return true;
        }
        if ("stop".equals(first)) {
            World world = resolveWorld(sender, args.length >= 2 ? args[1] : null);
            if (world == null) {
                return true;
            }
            plugin.warnings().cancel(world);
            plugin.manager().stop(world);
            sender.sendMessage("§eStopped disaster FX in §f" + world.getName() + "§e.");
            return true;
        }
        if ("status".equals(first)) {
            sender.sendMessage("§6YaPDisasters §7— §f" + plugin.manager().statusReport());
            return true;
        }
        if ("lock".equals(first) || "unlock".equals(first)) {
            World world = resolveWorld(sender, args.length >= 2 ? args[1] : null);
            if (world == null) {
                return true;
            }
            boolean enable = "unlock".equals(first);
            SkyWeather.setCycle(plugin, world, enable);
            sender.sendMessage(enable
                    ? "§aWeather cycle unlocked in §f" + world.getName()
                    : "§eWeather cycle locked in §f" + world.getName());
            return true;
        }
        if ("random".equals(first)) {
            return handleRandom(sender, args);
        }
        if ("site".equals(first) || "sites".equals(first)) {
            return handleSite(sender, args);
        }

        DisasterType type = DisasterType.parse(first);
        if (type == null) {
            sender.sendMessage("§e/yapdisaster <gui|type|stop|random|site|reload>");
            return true;
        }

        int duration = plugin.config().defaultDurationSeconds();
        String worldArg = null;
        if (args.length >= 2) {
            try {
                duration = Integer.parseInt(args[1]);
                if (args.length >= 3) {
                    worldArg = args[2];
                }
            } catch (NumberFormatException e) {
                worldArg = args[1];
                if (args.length >= 3) {
                    try {
                        duration = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ignored) {
                        sender.sendMessage("§cInvalid duration.");
                        return true;
                    }
                }
            }
        }
        duration = Math.max(5, Math.min(duration, 7 * 24 * 3600));

        World world = resolveWorld(sender, worldArg);
        if (world == null) {
            return true;
        }
        Location focus = sender instanceof Player player ? player.getLocation() : null;
        boolean ok = plugin.manager().start(world, type, duration, focus);
        if (!ok) {
            sender.sendMessage("§cCould not start — check config (enabled / world allow-list).");
            return true;
        }
        sender.sendMessage("§aStarted §f" + type.configKey() + " §ain §f" + world.getName()
                + " §afor §f" + duration + "s§a.");
        return true;
    }

    private boolean handleRandom(CommandSender sender, String[] args) {
        if (args.length < 2 || "status".equalsIgnoreCase(args[1])) {
            sender.sendMessage("§eRandom: §f" + plugin.randomEvents().statusLine()
                    + " §7(config enabled=" + plugin.config().randomEnabled()
                    + ", warn=" + plugin.config().warningSeconds() + "s)");
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if ("on".equals(sub) || "enable".equals(sub)) {
            if (!sender.hasPermission("yapdisasters.admin")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            plugin.randomEvents().setRuntimeEnabled(true);
            sender.sendMessage("§aRandom disasters enabled. " + plugin.randomEvents().statusLine());
            return true;
        }
        if ("off".equals(sub) || "disable".equals(sub)) {
            if (!sender.hasPermission("yapdisasters.admin")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            plugin.randomEvents().setRuntimeEnabled(false);
            sender.sendMessage("§eRandom disasters disabled.");
            return true;
        }
        if ("now".equals(sub)) {
            World world = resolveWorld(sender, args.length >= 4 ? args[3] : (args.length >= 3
                    && DisasterType.parse(args[2]) == null ? args[2] : null));
            DisasterType forced = args.length >= 3 ? RandomEventScheduler.parseForceType(args[2]) : null;
            if (world == null) {
                return true;
            }
            // If arg2 was a world name, forced stays null.
            if (args.length >= 3 && forced == null && Bukkit.getWorld(args[2]) != null) {
                world = Bukkit.getWorld(args[2]);
            }
            boolean ok = plugin.randomEvents().triggerNow(world, forced);
            if (!ok) {
                sender.sendMessage("§cCould not trigger — active disaster, pending warning, or empty pool.");
                return true;
            }
            sender.sendMessage("§aRandom event queued" + (forced != null ? " (" + forced.configKey() + ")" : "")
                    + " §ain §f" + world.getName()
                    + " §a(warn " + plugin.config().warningSeconds() + "s).");
            return true;
        }
        sender.sendMessage("§e/yapdisaster random <status|on|off|now [type] [world]>");
        return true;
    }

    private boolean handleSite(CommandSender sender, String[] args) {
        if (args.length < 2 || "list".equalsIgnoreCase(args[1])) {
            List<VolcanoSite> all = plugin.volcanoSites().all();
            if (all.isEmpty()) {
                sender.sendMessage("§eNo volcano sites. §7/yapdisaster site add <id>");
                return true;
            }
            sender.sendMessage("§6Volcano sites §7(" + all.size() + "):");
            for (VolcanoSite site : all) {
                sender.sendMessage(" §8- §f" + site.describe());
            }
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if ("add".equals(sub)) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage("§e/yapdisaster site add <id>");
                return true;
            }
            boolean ok = plugin.volcanoSites().add(args[2], player.getLocation(), false);
            sender.sendMessage(ok
                    ? "§aAdded volcano site §f" + args[2].toLowerCase(Locale.ROOT)
                    : "§cCould not add site.");
            return true;
        }
        if ("remove".equals(sub) || "del".equals(sub) || "delete".equals(sub)) {
            if (args.length < 3) {
                sender.sendMessage("§e/yapdisaster site remove <id>");
                return true;
            }
            boolean ok = plugin.volcanoSites().remove(args[2]);
            sender.sendMessage(ok ? "§eRemoved site §f" + args[2] : "§cUnknown site.");
            return true;
        }
        if ("erupt".equals(sub)) {
            if (args.length < 3) {
                sender.sendMessage("§e/yapdisaster site erupt <id> [seconds]");
                return true;
            }
            Optional<VolcanoSite> opt = plugin.volcanoSites().get(args[2]);
            if (opt.isEmpty()) {
                sender.sendMessage("§cUnknown site.");
                return true;
            }
            VolcanoSite site = opt.get();
            Location loc = site.toLocation();
            if (loc == null) {
                sender.sendMessage("§cSite world not loaded: §f" + site.worldName());
                return true;
            }
            int duration = plugin.config().defaultDurationSeconds();
            if (args.length >= 4) {
                try {
                    duration = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid duration.");
                    return true;
                }
            }
            duration = Math.max(5, Math.min(duration, 7 * 24 * 3600));
            boolean ok = plugin.manager().start(loc.getWorld(), DisasterType.VOLCANO, duration, loc);
            sender.sendMessage(ok
                    ? "§aErupting §f" + site.id() + " §afor §f" + duration + "s"
                    : "§cCould not erupt site.");
            return true;
        }
        sender.sendMessage("§e/yapdisaster site <list|add|remove|erupt> …");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            return List.of("gui", "clear", "rain", "thunder", "hurricane", "tornado",
                            "earthquake", "volcano", "blizzard", "drought", "meteor", "tsunami",
                            "stop", "status", "lock", "unlock", "random", "site", "reload").stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && "random".equalsIgnoreCase(args[0])) {
            return List.of("status", "on", "off", "now").stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && ("site".equalsIgnoreCase(args[0]) || "sites".equalsIgnoreCase(args[0]))) {
            return List.of("list", "add", "remove", "erupt").stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && "random".equalsIgnoreCase(args[0]) && "now".equalsIgnoreCase(args[1])) {
            List<String> out = new ArrayList<>();
            for (String t : List.of("thunder", "hurricane", "tornado", "earthquake", "volcano",
                    "blizzard", "drought", "meteor", "tsunami", "random")) {
                if (t.startsWith(prefix)) {
                    out.add(t);
                }
            }
            out.addAll(Bukkit.getWorlds().stream()
                    .map(World::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList());
            return out;
        }
        if (args.length == 3 && ("site".equalsIgnoreCase(args[0]) || "sites".equalsIgnoreCase(args[0]))
                && ("remove".equalsIgnoreCase(args[1]) || "erupt".equalsIgnoreCase(args[1]))) {
            return plugin.volcanoSites().all().stream()
                    .map(VolcanoSite::id)
                    .filter(id -> id.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            List<String> out = new ArrayList<>();
            for (String qty : List.of("60", "120", "300", "900")) {
                if (qty.startsWith(prefix)) {
                    out.add(qty);
                }
            }
            out.addAll(Bukkit.getWorlds().stream()
                    .map(World::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList());
            return out;
        }
        if (args.length == 3) {
            return Bukkit.getWorlds().stream()
                    .map(World::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private World resolveWorld(CommandSender sender, String worldName) {
        if (worldName != null && !worldName.isBlank()) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                sender.sendMessage("§cUnknown world: §f" + worldName);
                return null;
            }
            return world;
        }
        if (sender instanceof Player player) {
            return player.getWorld();
        }
        if (Bukkit.getWorlds().isEmpty()) {
            sender.sendMessage("§cNo worlds loaded.");
            return null;
        }
        return Bukkit.getWorlds().get(0);
    }
}
