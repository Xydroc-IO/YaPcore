package com.yapcore.world.cmd;

import com.yapcore.world.WorldPlugin;
import com.yapcore.world.edit.BrushService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * Brush binding and navigation (thru / jumpto / ascend / …) operations.
 */
final class WorldEditBrushNavOps {

    private final WorldPlugin plugin;
    private final BrushService brush;

    WorldEditBrushNavOps(WorldPlugin plugin, BrushService brush) {
        this.plugin = plugin;
        this.brush = brush;
    }

    int clampRadius(int r) {
        return Math.max(1, Math.min(r, plugin.worldConfig().maxRadius()));
    }

    boolean brushCmd(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage("§e//brush sphere|cyl|smooth|gravity|clipboard|butcher|erode|raise|lower|melt|fill|forest <radius> [pattern]");
            return true;
        }
        String type = args[0].toLowerCase(Locale.ROOT);
        int radius = clampRadius(args.length >= 2 ? WorldEditOpsSupport.parseInt(args[1], 3) : 3);
        String pattern = args.length >= 3 ? args[2] : "stone";
        BrushService.BrushType bt = switch (type) {
            case "cyl", "cylinder" -> BrushService.BrushType.CYL;
            case "smooth" -> BrushService.BrushType.SMOOTH;
            case "gravity", "grav" -> BrushService.BrushType.GRAVITY;
            case "clipboard", "schem", "paste" -> BrushService.BrushType.CLIPBOARD;
            case "butcher", "kill" -> BrushService.BrushType.BUTCHER;
            case "erode" -> BrushService.BrushType.ERODE;
            case "raise" -> BrushService.BrushType.RAISE;
            case "lower" -> BrushService.BrushType.LOWER;
            case "melt" -> BrushService.BrushType.MELT;
            case "fill" -> BrushService.BrushType.FILL;
            case "forest", "tree" -> BrushService.BrushType.FOREST;
            default -> BrushService.BrushType.SPHERE;
        };
        brush.setBrushFull(player.getUniqueId(), bt, radius, pattern);
        player.getInventory().addItem(new ItemStack(BrushService.BRUSH_TOOL));
        player.sendMessage("§aBrush §f" + type + " r=" + radius + " → " + pattern);
        return true;
    }

    void navThru(Player player) {
        Location eye = player.getEyeLocation();
        var dir = eye.getDirection().normalize();
        Location dest = null;
        boolean wasSolid = false;
        for (int i = 1; i <= 64; i++) {
            Location at = eye.clone().add(dir.clone().multiply(i));
            boolean solid = !at.getBlock().getType().isAir();
            if (wasSolid && !solid) {
                dest = at;
                break;
            }
            wasSolid = solid;
        }
        if (dest == null) {
            player.sendMessage("§cNothing to pass through.");
            return;
        }
        dest.setYaw(player.getLocation().getYaw());
        dest.setPitch(player.getLocation().getPitch());
        player.teleportAsync(dest);
        player.sendMessage("§aThru.");
    }

    void nav(Player player, String cmd) {
        Location loc = player.getLocation();
        switch (cmd) {
            case "jumpto", "j" -> {
                var target = player.getTargetBlockExact(120);
                if (target == null) {
                    player.sendMessage("§cNo block in sight.");
                    return;
                }
                Location dest = target.getLocation().add(0.5, 1, 0.5);
                dest.setYaw(loc.getYaw());
                dest.setPitch(loc.getPitch());
                player.teleportAsync(dest);
            }
            case "up" -> {
                Location dest = loc.clone().add(0, 1, 0);
                dest.getBlock().setType(Material.GLASS, false);
                dest.setYaw(loc.getYaw());
                dest.setPitch(loc.getPitch());
                player.teleportAsync(dest.add(0, 1, 0));
            }
            case "ceil" -> {
                for (int y = loc.getBlockY() + 1; y < loc.getWorld().getMaxHeight(); y++) {
                    if (!loc.getWorld().getBlockAt(loc.getBlockX(), y, loc.getBlockZ()).getType().isAir()) {
                        Location dest = loc.clone();
                        dest.setY(y - 1);
                        dest.setYaw(loc.getYaw());
                        dest.setPitch(loc.getPitch());
                        player.teleportAsync(dest);
                        return;
                    }
                }
                player.sendMessage("§cNo ceiling.");
            }
            case "ascend" -> {
                for (int y = loc.getBlockY() + 1; y < loc.getWorld().getMaxHeight() - 1; y++) {
                    if (loc.getWorld().getBlockAt(loc.getBlockX(), y, loc.getBlockZ()).getType().isAir()
                            && loc.getWorld().getBlockAt(loc.getBlockX(), y + 1, loc.getBlockZ()).getType().isAir()
                            && !loc.getWorld().getBlockAt(loc.getBlockX(), y - 1, loc.getBlockZ()).getType().isAir()) {
                        Location dest = loc.clone();
                        dest.setY(y);
                        player.teleportAsync(dest);
                        return;
                    }
                }
                player.sendMessage("§cNo free space above.");
            }
            case "descend" -> {
                for (int y = loc.getBlockY() - 1; y > loc.getWorld().getMinHeight(); y--) {
                    if (loc.getWorld().getBlockAt(loc.getBlockX(), y, loc.getBlockZ()).getType().isAir()
                            && loc.getWorld().getBlockAt(loc.getBlockX(), y + 1, loc.getBlockZ()).getType().isAir()
                            && !loc.getWorld().getBlockAt(loc.getBlockX(), y - 1, loc.getBlockZ()).getType().isAir()) {
                        Location dest = loc.clone();
                        dest.setY(y);
                        player.teleportAsync(dest);
                        return;
                    }
                }
                player.sendMessage("§cNo free space below.");
            }
            default -> {
            }
        }
    }
}
