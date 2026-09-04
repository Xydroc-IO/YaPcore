package com.yapcore.world.cmd;

import com.yapcore.sched.YapSched;
import com.yapcore.world.WorldCreateOptions;
import com.yapcore.world.WorldPlugin;
import com.yapcore.world.pregen.PregenBridge;
import com.yapcore.world.service.SelectionServiceImpl;
import com.yapcore.world.service.WorldManagerServiceImpl;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/** World load/create/unload/tp/pregen command handlers for {@link WorldCommands}. */
final class WorldCommandsWorldOps {

    private final WorldPlugin plugin;
    private final WorldManagerServiceImpl worlds;
    private final SelectionServiceImpl selection;

    WorldCommandsWorldOps(WorldPlugin plugin, WorldManagerServiceImpl worlds, SelectionServiceImpl selection) {
        this.plugin = plugin;
        this.worlds = worlds;
        this.selection = selection;
    }

    boolean reload(CommandSender sender) {
        if (!sender.hasPermission("yapworld.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        plugin.reloadWorld();
        sender.sendMessage("§aYaPWorld reloaded.");
        return true;
    }

    boolean status(CommandSender sender) {
        if (!sender.hasPermission("yapworld.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        sender.sendMessage("§aYaPWorld §7— worlds: §f" + String.join(", ", worlds.loadedWorlds())
                + " §7pregen: §f" + (PregenBridge.available() ? "ready" : "missing"));
        return true;
    }

    boolean load(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapworld.load") && !sender.hasPermission("yapworld.create")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§e/yapworld load <world>");
            return true;
        }
        worlds.loadWorld(args[1]).thenAccept(ok ->
                YapSched.global(plugin, () -> sender.sendMessage(ok ? "§aWorld loaded." : "§cLoad failed.")));
        return true;
    }

    boolean create(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapworld.create") && !sender.hasPermission("yapworld.load")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§e/yapworld create <name> [--type flat|normal|large_biomes|amplified]");
            sender.sendMessage("§7  [--env overworld|nether|end] [--seed <long>] [--generator <id>] [--no-structures]");
            return true;
        }
        String name = args[1];
        if (WorldManagerServiceImpl.sanitizeName(name) == null) {
            sender.sendMessage("§cInvalid world name (use letters, digits, _ or -).");
            return true;
        }
        WorldCreateOptions.Builder b = WorldCreateOptions.builder();
        for (int i = 2; i < args.length; i++) {
            String a = args[i];
            if (a.equalsIgnoreCase("--no-structures") || a.equalsIgnoreCase("-S")) {
                b.generateStructures(false);
                continue;
            }
            String key;
            String val;
            if (a.startsWith("--") && a.contains("=")) {
                int eq = a.indexOf('=');
                key = a.substring(2, eq).toLowerCase(Locale.ROOT);
                val = a.substring(eq + 1);
            } else if (a.startsWith("--") && i + 1 < args.length) {
                key = a.substring(2).toLowerCase(Locale.ROOT);
                val = args[++i];
            } else if (a.startsWith("-") && a.length() == 2 && i + 1 < args.length) {
                key = switch (a.charAt(1)) {
                    case 't' -> "type";
                    case 'e' -> "env";
                    case 's' -> "seed";
                    case 'g' -> "generator";
                    default -> "";
                };
                val = args[++i];
            } else {
                sender.sendMessage("§cUnknown option: §f" + a);
                return true;
            }
            switch (key) {
                case "type", "t", "worldtype" -> b.type(val);
                case "env", "environment", "dim", "dimension" -> b.environment(val);
                case "seed" -> {
                    try {
                        b.seed(Long.parseLong(val));
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cInvalid seed: §f" + val);
                        return true;
                    }
                }
                case "generator", "gen", "g" -> b.generator(val);
                case "structures" -> b.generateStructures(!"false".equalsIgnoreCase(val)
                        && !"no".equalsIgnoreCase(val) && !"0".equals(val));
                default -> {
                    sender.sendMessage("§cUnknown option: §f--" + key);
                    return true;
                }
            }
        }
        WorldCreateOptions opts = b.build();
        worlds.createWorld(name, opts).thenAccept(ok ->
                YapSched.global(plugin, () -> {
                    if (ok) {
                        sender.sendMessage("§aWorld §f" + name + " §aready §7(" + opts.type()
                                + " / " + opts.environment()
                                + (opts.seed() != null ? " seed=" + opts.seed() : "")
                                + (opts.generator() != null ? " gen=" + opts.generator() : "")
                                + ").");
                    } else {
                        sender.sendMessage("§cCreate/load failed. Check console (name taken? generator missing?).");
                    }
                }));
        return true;
    }

    boolean unload(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapworld.unload")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§e/yapworld unload <world>");
            return true;
        }
        worlds.unloadWorld(args[1]).thenAccept(ok ->
                YapSched.global(plugin, () -> sender.sendMessage(ok ? "§aWorld unloaded." : "§cUnload failed.")));
        return true;
    }

    boolean teleport(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapworld.teleport")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§e/yapworld tp <world> [player]");
            return true;
        }
        Player target;
        String worldName;
        if (args.length >= 3) {
            target = Bukkit.getPlayer(args[2]);
            worldName = args[1];
        } else if (sender instanceof Player player) {
            target = player;
            worldName = args[1];
        } else {
            sender.sendMessage("Console must specify a player.");
            return true;
        }
        if (target == null) {
            sender.sendMessage("§cPlayer not online.");
            return true;
        }
        worlds.teleportToWorldSpawn(target.getUniqueId(), worldName).thenAccept(ok ->
                YapSched.global(plugin, () -> {
                    if (sender != target) {
                        sender.sendMessage(ok ? "§aTeleported." : "§cTeleport failed.");
                    }
                }));
        return true;
    }

    boolean pregen(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapworld.pregen")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§e/yapworld pregen start [radius] | status | pause | resume | cancel");
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if ("start".equals(sub)) {
            return pregenStart(sender, args);
        }
        String target = args.length >= 3 ? args[2] : "all";
        String msg = switch (sub) {
            case "status" -> PregenBridge.status(target);
            case "pause" -> PregenBridge.pause(target);
            case "resume" -> PregenBridge.resume(target);
            case "cancel" -> PregenBridge.cancel(target);
            default -> "Unknown: " + sub;
        };
        sender.sendMessage("§7" + msg);
        return true;
    }

    private boolean pregenStart(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only for pregen start.");
            return true;
        }
        if (!PregenBridge.available()) {
            sender.sendMessage("§cYaPPregen is not loaded.");
            return true;
        }
        World world = player.getWorld();
        var selOpt = selection.selection(player.getUniqueId());
        String msg;
        if (selOpt.isPresent()) {
            msg = PregenBridge.startSelection(world, selOpt.get());
        } else {
            int radius = 128;
            if (args.length >= 3) {
                try {
                    radius = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid radius.");
                    return true;
                }
            }
            var loc = player.getLocation();
            msg = PregenBridge.startRadius(world, loc.getBlockX(), loc.getBlockZ(), radius);
        }
        sender.sendMessage("§a" + msg);
        return true;
    }
}
