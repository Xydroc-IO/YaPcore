package com.yapcore.games.kit;

import com.yapcore.sched.YapSched;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class KitSnapshot {

    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final ItemStack offhand;
    private final Location location;
    private final double health;
    private final int foodLevel;
    private final float saturation;
    private final float exp;
    private final int level;
    private final int totalExperience;
    private final GameMode gameMode;
    private final boolean allowFlight;
    private final boolean flying;

    private KitSnapshot(
            ItemStack[] contents,
            ItemStack[] armor,
            ItemStack offhand,
            Location location,
            double health,
            int foodLevel,
            float saturation,
            float exp,
            int level,
            int totalExperience,
            GameMode gameMode,
            boolean allowFlight,
            boolean flying) {
        this.contents = cloneArray(contents);
        this.armor = cloneArray(armor);
        this.offhand = offhand == null ? null : offhand.clone();
        this.location = location == null ? null : location.clone();
        this.health = health;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.exp = exp;
        this.level = level;
        this.totalExperience = totalExperience;
        this.gameMode = gameMode;
        this.allowFlight = allowFlight;
        this.flying = flying;
    }

    public static KitSnapshot capture(Player player) {
        return new KitSnapshot(
                player.getInventory().getContents(),
                player.getInventory().getArmorContents(),
                player.getInventory().getItemInOffHand(),
                player.getLocation(),
                player.getHealth(),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExp(),
                player.getLevel(),
                player.getTotalExperience(),
                player.getGameMode(),
                player.getAllowFlight(),
                player.isFlying());
    }

    public void restore(JavaPlugin plugin, Player player) {
        YapSched.entity(plugin, player, () -> {
            player.getInventory().clear();
            player.getInventory().setContents(cloneArray(contents));
            player.getInventory().setArmorContents(cloneArray(armor));
            if (offhand != null) {
                player.getInventory().setItemInOffHand(offhand.clone());
            }
            if (location != null && location.getWorld() != null) {
                player.teleport(location);
            }
            player.setHealth(Math.min(health, player.getMaxHealth()));
            player.setFoodLevel(foodLevel);
            player.setSaturation(saturation);
            player.setExp(exp);
            player.setLevel(level);
            player.setTotalExperience(totalExperience);
            player.setGameMode(gameMode);
            player.setAllowFlight(allowFlight);
            player.setFlying(flying);
        });
    }

    public void applyMatchKit(JavaPlugin plugin, Player player, KitDefinition kit) {
        YapSched.entity(plugin, player, () -> applyKitSync(player, kit));
    }

    public static void applyKitSync(Player player, KitDefinition kit) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(cloneArray(kit.armor()));
        player.getInventory().setContents(kit.buildInventory());
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(5f);
        player.setFireTicks(0);
        player.setGameMode(GameMode.SURVIVAL);
    }

    private static ItemStack[] cloneArray(ItemStack[] source) {
        if (source == null) {
            return new ItemStack[0];
        }
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }
}
