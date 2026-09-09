package com.yapcore.leveledmobs.cmd;

import com.yapcore.leveledmobs.LeveledMobsConfig;
import com.yapcore.leveledmobs.LeveledMobsPlugin;
import com.yapcore.messages.YapMessages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LeveledMobsCommand implements CommandExecutor, TabCompleter {

    private final LeveledMobsPlugin plugin;

    public LeveledMobsCommand(LeveledMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yapleveledmobs.admin")) {
            YapMessages.noPermission(sender, "yapleveledmobs.admin");
            return true;
        }
        if (args.length == 0) {
            status(sender);
            YapMessages.send(sender, "&e/yaplevel &7toggle | strategy | min|max|blocks <n> | nametag | reload | info | set <level>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        LeveledMobsConfig cfg = plugin.leveledConfig();
        return switch (sub) {
            case "reload" -> {
                plugin.reloadLeveled();
                YapMessages.reloaded(sender, "YaPLeveledMobs");
                yield true;
            }
            case "status" -> {
                status(sender);
                yield true;
            }
            case "toggle", "enable", "disable", "on", "off" -> {
                boolean next = switch (sub) {
                    case "enable", "on" -> true;
                    case "disable", "off" -> false;
                    default -> !cfg.enabled();
                };
                cfg.setEnabled(next);
                plugin.reloadLeveled();
                YapMessages.send(sender, next ? "&aLeveled mobs ON." : "&eLeveled mobs OFF.");
                yield true;
            }
            case "strategy" -> {
                if (args.length >= 2) {
                    String mode = args[1].toLowerCase(Locale.ROOT);
                    if (mode.startsWith("dist") || mode.equals("spawn")) {
                        cfg.setStrategy(LeveledMobsConfig.Strategy.DISTANCE_FROM_SPAWN);
                    } else if (mode.startsWith("rand")) {
                        cfg.setStrategy(LeveledMobsConfig.Strategy.RANDOM);
                    } else {
                        YapMessages.send(sender, "&cUse distance or random.");
                        yield true;
                    }
                } else {
                    cfg.cycleStrategy();
                }
                plugin.reloadLeveled();
                YapMessages.send(sender, "&aStrategy: &f{s}", "s", cfg.strategy().name());
                yield true;
            }
            case "min" -> {
                if (args.length < 2) {
                    YapMessages.send(sender, "&cUsage: /yaplevel min <n>");
                    yield true;
                }
                try {
                    cfg.setMinLevel(Integer.parseInt(args[1]));
                    plugin.reloadLeveled();
                    YapMessages.send(sender, "&aMin level: &f{n}", "n", Integer.toString(cfg.minLevel()));
                } catch (NumberFormatException e) {
                    YapMessages.send(sender, "&cInvalid number.");
                }
                yield true;
            }
            case "max" -> {
                if (args.length < 2) {
                    YapMessages.send(sender, "&cUsage: /yaplevel max <n>");
                    yield true;
                }
                try {
                    cfg.setMaxLevel(Integer.parseInt(args[1]));
                    plugin.reloadLeveled();
                    YapMessages.send(sender, "&aMax level: &f{n}", "n", Integer.toString(cfg.maxLevel()));
                } catch (NumberFormatException e) {
                    YapMessages.send(sender, "&cInvalid number.");
                }
                yield true;
            }
            case "blocks", "bpl" -> {
                if (args.length < 2) {
                    YapMessages.send(sender, "&cUsage: /yaplevel blocks <n>");
                    yield true;
                }
                try {
                    cfg.setBlocksPerLevel(Double.parseDouble(args[1]));
                    plugin.reloadLeveled();
                    YapMessages.send(sender, "&aBlocks/level: &f{n}",
                            "n", String.format(Locale.ROOT, "%.0f", cfg.blocksPerLevel()));
                } catch (NumberFormatException e) {
                    YapMessages.send(sender, "&cInvalid number.");
                }
                yield true;
            }
            case "nametag" -> {
                cfg.setNametagEnabled(!cfg.nametagEnabled());
                plugin.reloadLeveled();
                YapMessages.send(sender, cfg.nametagEnabled()
                        ? "&aNametags ON."
                        : "&eNametags OFF.");
                yield true;
            }
            case "info" -> {
                LivingEntity target = resolveTarget(sender);
                if (target == null) {
                    YapMessages.send(sender, "&cLook at a mob (or stand near one).");
                    yield true;
                }
                if (!plugin.store().hasLevel(target)) {
                    YapMessages.send(sender, "&7{type} &chas no level.",
                            "type", target.getType().name());
                    yield true;
                }
                YapMessages.send(sender, "&a{type} &7level &f{level}",
                        "type", target.getType().name(),
                        "level", Integer.toString(plugin.store().getLevel(target)));
                yield true;
            }
            case "set" -> {
                if (args.length < 2) {
                    YapMessages.send(sender, "&cUsage: /yaplevel set <level>");
                    yield true;
                }
                int level;
                try {
                    level = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    YapMessages.send(sender, "&cInvalid level.");
                    yield true;
                }
                LivingEntity target = resolveTarget(sender);
                if (target == null) {
                    YapMessages.send(sender, "&cLook at a mob (or stand near one).");
                    yield true;
                }
                if (target instanceof Player) {
                    YapMessages.send(sender, "&cCannot level players.");
                    yield true;
                }
                plugin.applier().setLevel(target, level);
                YapMessages.send(sender, "&aSet &f{type} &ato level &f{level}",
                        "type", target.getType().name(),
                        "level", Integer.toString(plugin.store().getLevel(target)));
                yield true;
            }
            default -> {
                YapMessages.send(sender, "&e/yaplevel &7toggle | strategy | min|max|blocks <n> | nametag | reload | info | set <level>");
                yield true;
            }
        };
    }

    private void status(CommandSender sender) {
        LeveledMobsConfig cfg = plugin.leveledConfig();
        YapMessages.send(sender, "&6YaPLeveledMobs &7enabled=&f{on} &7strategy=&f{s} &7levels=&f{min}-{max} &7blocks=&f{b} &7nametag=&f{n}",
                "on", cfg.enabled() ? "true" : "false",
                "s", cfg.strategy().name(),
                "min", Integer.toString(cfg.minLevel()),
                "max", Integer.toString(cfg.maxLevel()),
                "b", String.format(Locale.ROOT, "%.0f", cfg.blocksPerLevel()),
                "n", cfg.nametagEnabled() ? "on" : "off");
    }

    private LivingEntity resolveTarget(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return null;
        }
        RayTraceResult hit = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                8.0,
                0.4,
                e -> e instanceof LivingEntity && e != player);
        if (hit != null && hit.getHitEntity() instanceof LivingEntity living) {
            return living;
        }
        LivingEntity nearest = null;
        double best = 6.0 * 6.0;
        for (Entity e : player.getNearbyEntities(6, 6, 6)) {
            if (!(e instanceof LivingEntity living) || living instanceof Player) {
                continue;
            }
            double d = living.getLocation().distanceSquared(player.getLocation());
            if (d < best) {
                best = d;
                nearest = living;
            }
        }
        return nearest;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("yapleveledmobs.admin")) {
            return out;
        }
        if (args.length == 1) {
            for (String s : List.of("toggle", "strategy", "min", "max", "blocks", "nametag",
                    "reload", "status", "info", "set", "enable", "disable")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String p = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("set") || sub.equals("min") || sub.equals("max") || sub.equals("blocks")) {
                for (String s : List.of("1", "5", "10", "25", "50", "80", "100")) {
                    if (s.startsWith(p)) {
                        out.add(s);
                    }
                }
            } else if (sub.equals("strategy")) {
                for (String s : List.of("distance", "random")) {
                    if (s.startsWith(p)) {
                        out.add(s);
                    }
                }
            }
        }
        return out;
    }
}
