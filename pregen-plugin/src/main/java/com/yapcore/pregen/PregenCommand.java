package com.yapcore.pregen;

import com.yapcore.pregen.shape.ChunkPos;
import com.yapcore.pregen.shape.ChunkShape;
import com.yapcore.pregen.shape.CircleShape;
import com.yapcore.pregen.shape.PolygonShape;
import com.yapcore.pregen.shape.RectShape;
import com.yapcore.pregen.shape.SpiralShape;
import com.yapcore.pregen.shape.WorldBorderShape;
import com.yapcore.pregen.shape.WorldEditShape;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class PregenCommand implements CommandExecutor, TabCompleter {

    private final PregenPlugin plugin;
    private final PregenService service;

    public PregenCommand(PregenPlugin plugin, PregenService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /yappregen <start|pause|resume|cancel|status|reload>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "start" -> start(sender, args);
            case "pause" -> {
                sender.sendMessage(service.pause(arg(args, 1, "all")));
                yield true;
            }
            case "resume" -> {
                sender.sendMessage(service.resume(arg(args, 1, "all")));
                yield true;
            }
            case "cancel" -> {
                sender.sendMessage(service.cancel(arg(args, 1, "all")));
                yield true;
            }
            case "status" -> {
                sender.sendMessage(service.status(arg(args, 1, "all")));
                yield true;
            }
            case "reload" -> {
                plugin.reloadPregenConfig();
                sender.sendMessage("YaPPregen config reloaded");
                yield true;
            }
            default -> {
                sender.sendMessage("Unknown subcommand: " + sub);
                yield true;
            }
        };
    }

    private boolean start(CommandSender sender, String[] args) {
        // start <world> <shape> ...
        if (args.length < 3) {
            sender.sendMessage("Usage: /yappregen start <world> <radius|circle|corners|polygon|worldborder|selection> ...");
            return true;
        }
        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            sender.sendMessage("Unknown world: " + args[1]);
            return true;
        }
        String shape = args[2].toLowerCase(Locale.ROOT);
        try {
            ChunkShape s = switch (shape) {
                case "radius", "spiral" -> {
                    if (args.length < 4) {
                        throw new IllegalArgumentException("radius <chunks> [x z]");
                    }
                    int r = Integer.parseInt(args[3]);
                    int[] c = center(sender, world, args, 4);
                    ChunkPos cc = ChunkPos.fromBlock(c[0], c[1]);
                    yield new SpiralShape(cc.x(), cc.z(), r);
                }
                case "circle" -> {
                    if (args.length < 4) {
                        throw new IllegalArgumentException("circle <blockRadius> [x z]");
                    }
                    int r = Integer.parseInt(args[3]);
                    int[] c = center(sender, world, args, 4);
                    yield new CircleShape(c[0], c[1], r);
                }
                case "corners", "rect", "region" -> {
                    if (args.length < 7) {
                        throw new IllegalArgumentException("corners <x1> <z1> <x2> <z2>");
                    }
                    yield new RectShape(
                            Integer.parseInt(args[3]), Integer.parseInt(args[4]),
                            Integer.parseInt(args[5]), Integer.parseInt(args[6]));
                }
                case "polygon" -> {
                    if (args.length < 9) {
                        throw new IllegalArgumentException("polygon <x1> <z1> <x2> <z2> <x3> <z3> ...");
                    }
                    int[] xz = new int[args.length - 3];
                    for (int i = 3; i < args.length; i++) {
                        xz[i - 3] = Integer.parseInt(args[i]);
                    }
                    yield new PolygonShape(xz);
                }
                case "worldborder", "border" -> new WorldBorderShape(world);
                case "selection", "sel", "we" -> {
                    if (!(sender instanceof Player player)) {
                        throw new IllegalArgumentException("selection requires a player");
                    }
                    yield WorldEditShape.fromPlayer(player, plugin.getLogger());
                }
                default -> throw new IllegalArgumentException("Unknown shape: " + shape);
            };
            sender.sendMessage(service.startJob(world, s));
        } catch (Exception e) {
            sender.sendMessage("Failed: " + e.getMessage());
        }
        return true;
    }

    private static int[] center(CommandSender sender, World world, String[] args, int idx) {
        if (args.length >= idx + 2) {
            return new int[]{Integer.parseInt(args[idx]), Integer.parseInt(args[idx + 1])};
        }
        if (sender instanceof Player p && p.getWorld().equals(world)) {
            return new int[]{p.getLocation().getBlockX(), p.getLocation().getBlockZ()};
        }
        return new int[]{world.getSpawnLocation().getBlockX(), world.getSpawnLocation().getBlockZ()};
    }

    private static String arg(String[] args, int i, String def) {
        return args.length > i ? args[i] : def;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("start", "pause", "resume", "cancel", "status", "reload"), args[0]);
        }
        if (args.length == 2 && List.of("pause", "resume", "cancel", "status").contains(args[0].toLowerCase())) {
            List<String> worlds = Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toCollection(ArrayList::new));
            worlds.add(0, "all");
            return filter(worlds, args[1]);
        }
        if (args.length == 2 && "start".equalsIgnoreCase(args[0])) {
            return filter(Bukkit.getWorlds().stream().map(World::getName).toList(), args[1]);
        }
        if (args.length == 3 && "start".equalsIgnoreCase(args[0])) {
            return filter(List.of("radius", "circle", "corners", "polygon", "worldborder", "selection"), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> opts, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return opts.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
