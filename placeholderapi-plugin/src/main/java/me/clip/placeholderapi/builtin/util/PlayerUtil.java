package me.clip.placeholderapi.builtin.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/** Helpers for built-in player placeholders (clean-room). */
public final class PlayerUtil {

    private PlayerUtil() {
    }

    public static ItemStack itemInHand(Player player) {
        return player.getInventory().getItemInMainHand();
    }

    public static int durability(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof Damageable damageable) {
            return damageable.getDamage();
        }
        return 0;
    }

    public static int getEmptySlots(Player player) {
        int empty = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType() == Material.AIR) {
                empty++;
            }
        }
        return empty;
    }

    public static int getTotalExperience(Player player) {
        return player.getTotalExperience();
    }

    public static String getBiome(Player player) {
        Location loc = player.getLocation();
        return loc.getBlock().getBiome().getKey().getKey();
    }

    public static String getCapitalizedBiome(Player player) {
        String biome = getBiome(player);
        if (biome.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(biome.charAt(0)) + biome.substring(1).replace('_', ' ');
    }

    public enum Cardinal {
        NORTH, NORTH_EAST, EAST, SOUTH_EAST, SOUTH, SOUTH_WEST, WEST, NORTH_WEST
    }

    public static Cardinal getDirection(Player player) {
        float yaw = player.getLocation().getYaw();
        yaw = (yaw % 360 + 360) % 360;
        if (yaw >= 337.5 || yaw < 22.5) {
            return Cardinal.SOUTH;
        }
        if (yaw < 67.5) {
            return Cardinal.SOUTH_WEST;
        }
        if (yaw < 112.5) {
            return Cardinal.WEST;
        }
        if (yaw < 157.5) {
            return Cardinal.NORTH_WEST;
        }
        if (yaw < 202.5) {
            return Cardinal.NORTH;
        }
        if (yaw < 247.5) {
            return Cardinal.NORTH_EAST;
        }
        if (yaw < 292.5) {
            return Cardinal.EAST;
        }
        return Cardinal.SOUTH_EAST;
    }

    public static String getXZDirection(Player player) {
        float yaw = player.getLocation().getYaw();
        yaw = (yaw % 360 + 360) % 360;
        if (yaw >= 315 || yaw < 45) {
            return "+Z";
        }
        if (yaw < 135) {
            return "-X";
        }
        if (yaw < 225) {
            return "-Z";
        }
        return "+X";
    }

    public static String format12(long ticks) {
        long hours = (ticks / 1000L + 6L) % 24L;
        long minutes = (ticks % 1000L) * 60L / 1000L;
        String ampm = hours >= 12 ? "PM" : "AM";
        hours = hours % 12;
        if (hours == 0) {
            hours = 12;
        }
        return String.format("%d:%02d %s", hours, minutes, ampm);
    }

    public static String format24(long ticks) {
        long hours = (ticks / 1000L + 6L) % 24L;
        long minutes = (ticks % 1000L) * 60L / 1000L;
        return String.format("%02d:%02d", hours, minutes);
    }

    public static String getLocale(Player player) {
        try {
            return player.getLocale();
        } catch (Throwable t) {
            return "en_us";
        }
    }

    public static int getPing(Player player) {
        try {
            return player.getPing();
        } catch (Throwable t) {
            return 0;
        }
    }

    public static String itemName(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "";
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return "";
    }
}
