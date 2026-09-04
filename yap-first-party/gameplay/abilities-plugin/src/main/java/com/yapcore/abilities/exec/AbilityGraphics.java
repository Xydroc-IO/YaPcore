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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cast icons + flying spell visuals via ItemDisplay + CustomModelData.
 * Folia-safe: no sync teleport, cast icons pulse in place, projectile bodies ride
 * the projectile as passengers (no per-tick cross-region teleports).
 */
public final class AbilityGraphics {

    public static final int CMD_BASE = 78200;
    /** Dedicated UI token — never a sword/rod/tool players need in gameplay. */
    public static final Material ICON_MATERIAL = Material.CLAY_BALL;

    private static final AtomicInteger LIVE_ICONS = new AtomicInteger();
    private static final Map<UUID, AtomicInteger> ICONS_BY_PLAYER = new ConcurrentHashMap<>();

    private AbilityGraphics() {
    }

    public static int cmdFor(String abilityId) {
        if (abilityId == null || abilityId.isBlank()) {
            return CMD_BASE;
        }
        // Stay below skill CMD band (79000+) and above showcase (78010-78015).
        return CMD_BASE + (Math.floorMod(abilityId.hashCode(), 700));
    }

    /** Pulsing cast icon in front of the caster (wind-up flash). No follow-teleport. */
    public static void spawnCastIcon(JavaPlugin plugin, Player caster, AbilityDefinition ability) {
        int maxGlobal = plugin.getConfig().getInt("folia-safe.max-cast-icons-global", 48);
        int maxPer = plugin.getConfig().getInt("folia-safe.max-cast-icons-per-player", 4);
        if (LIVE_ICONS.get() >= maxGlobal) {
            return;
        }
        AtomicInteger per = ICONS_BY_PLAYER.computeIfAbsent(caster.getUniqueId(), u -> new AtomicInteger());
        if (per.get() >= maxPer) {
            return;
        }
        int cmd = resolveCmd(ability);
        YapSched.entity(plugin, caster, () -> {
            if (LIVE_ICONS.get() >= maxGlobal || per.get() >= maxPer) {
                return;
            }
            ItemStack icon = iconStack(cmd, ability.displayName());
            Location spawn = caster.getEyeLocation().add(caster.getLocation().getDirection().multiply(0.75));
            ItemDisplay display;
            try {
                display = caster.getWorld().spawn(spawn, ItemDisplay.class, d -> {
                    d.setItemStack(icon);
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
                    d.setBillboard(ItemDisplay.Billboard.CENTER);
                    d.setInterpolationDuration(2);
                    d.setTeleportDuration(1);
                    d.setPersistent(false);
                    d.setGravity(false);
                    applyScale(d, 0.55f);
                });
            } catch (RuntimeException ex) {
                plugin.getLogger().fine("cast icon spawn skipped: " + ex.getMessage());
                return;
            }
            LIVE_ICONS.incrementAndGet();
            per.incrementAndGet();
            pulseInPlaceAndRemove(plugin, display, caster.getUniqueId(), 16);
        });
    }

    /**
     * Attach a spinning ItemDisplay “spell body” as a passenger of the projectile.
     * Riding avoids per-tick teleportAsync across region boundaries under spam.
     */
    public static ItemDisplay attachProjectileBody(
            JavaPlugin plugin,
            Entity projectile,
            AbilityDefinition ability,
            float scale) {
        int cmd = resolveCmd(ability);
        ItemStack icon = iconStack(cmd, ability.displayName());
        float s = scale <= 0 ? 1.15f : scale;
        ItemDisplay display = projectile.getWorld().spawn(projectile.getLocation(), ItemDisplay.class, d -> {
            d.setItemStack(icon);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(ItemDisplay.Billboard.CENTER);
            d.setInterpolationDuration(1);
            d.setTeleportDuration(1);
            d.setPersistent(false);
            d.setGravity(false);
            applyScale(d, s);
        });
        boolean mounted = false;
        try {
            mounted = projectile.addPassenger(display);
        } catch (RuntimeException ignored) {
            mounted = false;
        }
        if (!mounted) {
            // Fallback: one-shot body at spawn — no follow loop (safer than spam teleports).
            YapSched.entityLater(plugin, display, () -> removeDisplay(plugin, display), 8L);
            return display;
        }
        // Spin only — position is owned by the vehicle.
        scheduleSpin(plugin, display, 100);
        // Hard failsafe — never leave bodies forever if the tracker dies.
        YapSched.entityLater(plugin, display, () -> removeDisplay(plugin, display), 120L);
        return display;
    }

    /** Spin passenger body, or soft async follow if mount failed. */
    public static void tickProjectileBody(ItemDisplay display, Entity projectile, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        // If already riding, only spin — never teleport (Folia multi-region safe).
        if (display.isInsideVehicle()) {
            applySpin(display, ticks);
            return;
        }
        if (projectile == null || !projectile.isValid()) {
            return;
        }
        // Soft fallback: async teleport only, swallow failures.
        try {
            display.teleportAsync(projectile.getLocation().clone().add(0, 0.15, 0));
            applySpin(display, ticks);
        } catch (RuntimeException ignored) {
            // Prefer dropping the visual over crashing a region thread.
        }
    }

    public static void removeDisplay(ItemDisplay display) {
        if (display != null && display.isValid()) {
            try {
                display.leaveVehicle();
            } catch (RuntimeException ignored) {
            }
            display.remove();
        }
    }

    /** Folia-safe removal — schedules on the display's entity region. */
    public static void removeDisplay(JavaPlugin plugin, ItemDisplay display) {
        if (display == null || !display.isValid()) {
            return;
        }
        YapSched.entity(plugin, display, () -> {
            if (display.isValid()) {
                try {
                    display.leaveVehicle();
                } catch (RuntimeException ignored) {
                }
                display.remove();
            }
        });
    }

    public static ItemStack iconItem(AbilityDefinition ability) {
        return iconStack(resolveCmd(ability), ability.displayName());
    }

    private static int resolveCmd(AbilityDefinition ability) {
        return ability.resolvedIconCmd() > 0 ? ability.resolvedIconCmd() : cmdFor(ability.id());
    }

    /** Scale-only pulse — never teleports (safe under multi-region spam). */
    private static void pulseInPlaceAndRemove(
            JavaPlugin plugin, ItemDisplay display, UUID casterId, int lifeTicks) {
        for (int t = 1; t <= lifeTicks; t++) {
            final int tick = t;
            YapSched.entityLater(plugin, display, () -> {
                if (!display.isValid()) {
                    return;
                }
                float scale = 0.35f + (tick / (float) lifeTicks) * 0.55f;
                if (tick > lifeTicks - 4) {
                    scale *= (lifeTicks - tick + 1) / 4f;
                }
                applyScale(display, Math.max(0.05f, scale));
                if (tick >= lifeTicks) {
                    finishIcon(display, casterId);
                }
            }, tick);
        }
        YapSched.entityLater(plugin, display, () -> finishIcon(display, casterId), lifeTicks + 8L);
    }

    private static void finishIcon(ItemDisplay display, UUID casterId) {
        if (display != null && display.isValid()) {
            display.remove();
        }
        LIVE_ICONS.updateAndGet(v -> Math.max(0, v - 1));
        AtomicInteger per = ICONS_BY_PLAYER.get(casterId);
        if (per != null) {
            per.updateAndGet(v -> Math.max(0, v - 1));
        }
    }

    private static void scheduleSpin(JavaPlugin plugin, ItemDisplay display, int maxTicks) {
        for (int t = 2; t <= maxTicks; t += 2) {
            final int tick = t;
            YapSched.entityLater(plugin, display, () -> {
                if (display.isValid() && display.isInsideVehicle()) {
                    applySpin(display, tick);
                }
            }, tick);
        }
    }

    private static void applySpin(ItemDisplay display, int ticks) {
        float angle = (ticks * 18f) % 360f;
        float scale = display.getTransformation().getScale().x;
        display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f((float) Math.toRadians(angle), 0, 1, 0),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 1, 0)));
        display.setInterpolationDuration(1);
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
