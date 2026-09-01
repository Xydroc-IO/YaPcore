package com.yapcore.abilities.exec;

import com.yapcore.abilities.AbilityDefinition;
import com.yapcore.sched.YapSched;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Cast icons + flying spell visuals via ItemDisplay + CustomModelData.
 */
public final class AbilityGraphics {

    public static final int CMD_BASE = 78200;
    /** Dedicated UI token — never a sword/rod/tool players need in gameplay. */
    public static final Material ICON_MATERIAL = Material.CLAY_BALL;

    private AbilityGraphics() {
    }

    public static int cmdFor(String abilityId) {
        if (abilityId == null || abilityId.isBlank()) {
            return CMD_BASE;
        }
        // Stay below skill CMD band (79000+) and above showcase (78010-78015).
        return CMD_BASE + (Math.floorMod(abilityId.hashCode(), 700));
    }

    /** Pulsing cast icon in front of the caster (wind-up flash). */
    public static void spawnCastIcon(JavaPlugin plugin, Player caster, AbilityDefinition ability) {
        int cmd = resolveCmd(ability);
        YapSched.entity(plugin, caster, () -> {
            ItemStack icon = iconStack(cmd, ability.displayName());
            Location spawn = caster.getEyeLocation().add(caster.getLocation().getDirection().multiply(0.75));
            ItemDisplay display = caster.getWorld().spawn(spawn, ItemDisplay.class, d -> {
                d.setItemStack(icon);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
                d.setBillboard(ItemDisplay.Billboard.CENTER);
                d.setInterpolationDuration(2);
                d.setTeleportDuration(1);
                applyScale(d, 0.35f);
            });
            pulseAndRemove(plugin, display, caster, 14);
        });
    }

    /**
     * Attach a spinning ItemDisplay “spell body” to a projectile entity.
     * Returns the display so the tracker can teleport/remove it each tick.
     */
    public static ItemDisplay attachProjectileBody(
            JavaPlugin plugin,
            Entity projectile,
            AbilityDefinition ability,
            float scale) {
        int cmd = resolveCmd(ability);
        ItemStack icon = iconStack(cmd, ability.displayName());
        float s = scale <= 0 ? 0.85f : scale;
        ItemDisplay display = projectile.getWorld().spawn(projectile.getLocation(), ItemDisplay.class, d -> {
            d.setItemStack(icon);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(ItemDisplay.Billboard.CENTER);
            d.setInterpolationDuration(1);
            d.setTeleportDuration(1);
            applyScale(d, s);
        });
        return display;
    }

    public static void tickProjectileBody(ItemDisplay display, Entity projectile, int ticks) {
        if (display == null || !display.isValid() || projectile == null || !projectile.isValid()) {
            return;
        }
        Location at = projectile.getLocation().clone().add(0, 0.15, 0);
        display.teleport(at);
        // Slow spin for readability
        float angle = (ticks * 18f) % 360f;
        float scale = display.getTransformation().getScale().x;
        display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f((float) Math.toRadians(angle), 0, 1, 0),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 1, 0)));
        display.setInterpolationDuration(1);
    }

    public static void removeDisplay(ItemDisplay display) {
        if (display != null && display.isValid()) {
            display.remove();
        }
    }

    public static ItemStack iconItem(AbilityDefinition ability) {
        return iconStack(resolveCmd(ability), ability.displayName());
    }

    private static int resolveCmd(AbilityDefinition ability) {
        return ability.resolvedIconCmd() > 0 ? ability.resolvedIconCmd() : cmdFor(ability.id());
    }

    private static void pulseAndRemove(JavaPlugin plugin, ItemDisplay display, Player caster, int lifeTicks) {
        for (int t = 1; t <= lifeTicks; t++) {
            final int tick = t;
            YapSched.globalLater(plugin, () -> {
                if (!display.isValid()) {
                    return;
                }
                if (caster.isOnline()) {
                    Location follow = caster.getEyeLocation()
                            .add(caster.getLocation().getDirection().multiply(0.75 + tick * 0.02));
                    follow.add(0, Math.sin(tick * 0.45) * 0.08, 0);
                    display.teleport(follow);
                }
                float scale = 0.35f + (tick / (float) lifeTicks) * 0.55f;
                if (tick > lifeTicks - 4) {
                    scale *= (lifeTicks - tick + 1) / 4f;
                }
                applyScale(display, Math.max(0.05f, scale));
                if (tick >= lifeTicks) {
                    display.remove();
                }
            }, tick);
        }
    }

    private static void applyScale(ItemDisplay display, float scale) {
        display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(0, 0, 1, 0),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 1, 0)));
    }

    private static ItemStack iconStack(int cmd, String name) {
        ItemStack stack = new ItemStack(ICON_MATERIAL);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(cmd);
            meta.setDisplayName("§d" + name);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
