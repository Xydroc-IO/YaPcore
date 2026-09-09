package com.yapcore.items.ability;

import com.yapcore.items.ItemsPlugin;
import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Particle / sound FX helpers for {@link AbilityEngine}. */
final class AbilityFx {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    static final Map<AbilityType, FxProfile> DEFAULT_FX = new EnumMap<>(AbilityType.class);

    static {
        DEFAULT_FX.put(AbilityType.MESSAGE, new FxProfile("ENTITY_EXPERIENCE_ORB_PICKUP", "CRIT", 10));
        DEFAULT_FX.put(AbilityType.EFFECT, new FxProfile("BLOCK_BEACON_ACTIVATE", "TOTEM_OF_UNDYING", 28));
        DEFAULT_FX.put(AbilityType.HEAL, new FxProfile("ENTITY_PLAYER_LEVELUP", "HEART", 18));
        DEFAULT_FX.put(AbilityType.FEED, new FxProfile("ENTITY_GENERIC_EAT", "HAPPY_VILLAGER", 12));
        DEFAULT_FX.put(AbilityType.LAUNCH, new FxProfile("ENTITY_FIREWORK_ROCKET_LAUNCH", "CLOUD", 22));
        DEFAULT_FX.put(AbilityType.DASH, new FxProfile("ENTITY_ENDER_DRAGON_FLAP", "CLOUD", 20));
        DEFAULT_FX.put(AbilityType.LIGHTNING, new FxProfile("ENTITY_LIGHTNING_BOLT_THUNDER", "ELECTRIC_SPARK", 32));
        DEFAULT_FX.put(AbilityType.LIGHTNING_DASH, new FxProfile("ENTITY_LIGHTNING_BOLT_THUNDER", "ELECTRIC_SPARK", 32));
        DEFAULT_FX.put(AbilityType.SMITE_TARGET, new FxProfile("ENTITY_LIGHTNING_BOLT_THUNDER", "ELECTRIC_SPARK", 32));
        DEFAULT_FX.put(AbilityType.EXPLODE, new FxProfile("ENTITY_GENERIC_EXPLODE", "EXPLOSION", 8));
        DEFAULT_FX.put(AbilityType.PROJECTILE, new FxProfile("ENTITY_SNOWBALL_THROW", "CRIT", 12));
        DEFAULT_FX.put(AbilityType.COMMAND_PLAYER, new FxProfile("BLOCK_NOTE_BLOCK_CHIME", "NOTE", 10));
        DEFAULT_FX.put(AbilityType.COMMAND_CONSOLE, new FxProfile("BLOCK_NOTE_BLOCK_CHIME", "NOTE", 10));
        DEFAULT_FX.put(AbilityType.SOUND, new FxProfile("ENTITY_EXPERIENCE_ORB_PICKUP", "CRIT", 8));
        DEFAULT_FX.put(AbilityType.PARTICLE, new FxProfile("ENTITY_EXPERIENCE_ORB_PICKUP", "END_ROD", 30));
        DEFAULT_FX.put(AbilityType.GIVE_ITEM, new FxProfile("ENTITY_ITEM_PICKUP", "HAPPY_VILLAGER", 14));
        DEFAULT_FX.put(AbilityType.BREAK_BLOCK, new FxProfile("BLOCK_ANVIL_LAND", "FLAME", 16));
        DEFAULT_FX.put(AbilityType.AREA_EFFECT, new FxProfile("BLOCK_SNOW_BREAK", "SNOWFLAKE", 36));
        DEFAULT_FX.put(AbilityType.CLEANSE, new FxProfile("BLOCK_BEACON_POWER_SELECT", "END_ROD", 24));
        DEFAULT_FX.put(AbilityType.ABSORB, new FxProfile("BLOCK_BEACON_ACTIVATE", "TOTEM_OF_UNDYING", 20));
        DEFAULT_FX.put(AbilityType.FIREBALL, new FxProfile("ENTITY_GHAST_SHOOT", "FLAME", 18));
        DEFAULT_FX.put(AbilityType.PULL, new FxProfile("ENTITY_ENDERMAN_TELEPORT", "PORTAL", 22));
        DEFAULT_FX.put(AbilityType.PUSH, new FxProfile("ENTITY_IRON_GOLEM_ATTACK", "CLOUD", 22));
        DEFAULT_FX.put(AbilityType.BLINK, new FxProfile("ENTITY_ENDERMAN_TELEPORT", "PORTAL", 16));
        DEFAULT_FX.put(AbilityType.GROUND_SLAM, new FxProfile("ENTITY_GENERIC_EXPLODE", "EXPLOSION", 20));
        DEFAULT_FX.put(AbilityType.REPAIR, new FxProfile("BLOCK_ANVIL_USE", "CRIT", 14));
    }

    private final ItemsPlugin plugin;

    AbilityFx(ItemsPlugin plugin) {
        this.plugin = plugin;
    }

    FxProfile defaults(AbilityDefinition ability) {
        FxProfile profile = DEFAULT_FX.get(ability.type());
        return profile != null ? profile : new FxProfile("ENTITY_EXPERIENCE_ORB_PICKUP", "CRIT", 12);
    }

    void burst(Player player, AbilityDefinition ability) {
        burstAt(player.getLocation().add(0, 1, 0), ability);
    }

    void burstAt(Location loc, AbilityDefinition ability) {
        FxProfile d = defaults(ability);
        playSound(loc, ability.paramString("sound", d.sound()), 1f, 1f);
        String particleName = ability.paramString("particle", d.particle());
        // RAINBOW used to spawn colored dust "smoke" — cosmetic rainbow is mesh/name only.
        if (particleName != null && "RAINBOW".equalsIgnoreCase(particleName.trim())) {
            return;
        }
        int count = ability.paramInt("count", d.count());
        particleAt(loc, particleOf(particleName), count, 0.35, 0.55, 0.35, 0.04);
    }

    Particle resolveParticle(AbilityDefinition ability) {
        String name = ability.paramString("particle", defaults(ability).particle());
        if (name != null && "RAINBOW".equalsIgnoreCase(name.trim())) {
            return Particle.CRIT; // unused when burst skips RAINBOW
        }
        return particleOf(name);
    }

    /** Dust rainbow FX removed — rainbow is name + client mesh tint only. */
    @Deprecated
    static void rainbowBurst(Location loc, int count) {
        // no-op
    }

    void playSound(Location loc, String name, float volume, float pitch) {
        if (name == null || name.isBlank() || loc.getWorld() == null) {
            return;
        }
        try {
            Sound sound = Sound.valueOf(name.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_'));
            loc.getWorld().playSound(loc, sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
        }
    }

    static Particle particleOf(String name) {
        if (name == null || name.isBlank()) {
            return Particle.CRIT;
        }
        String key = name.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
        if ("SPELL_WITCH".equals(key) || "WITCH".equals(key)) {
            key = "WITCH";
        } else if ("SPELL".equals(key) || "SPELL_MOB".equals(key)) {
            key = "ENTITY_EFFECT";
        } else if ("SMOKE_NORMAL".equals(key) || "SMOKE".equals(key)) {
            key = "SMOKE";
        } else if ("REDSTONE".equals(key) || "DUST".equals(key)) {
            key = "DUST";
        } else if ("VILLAGER_HAPPY".equals(key) || "HAPPY".equals(key)) {
            key = "HAPPY_VILLAGER";
        } else if ("TOTEM".equals(key)) {
            key = "TOTEM_OF_UNDYING";
        }
        try {
            return Particle.valueOf(key);
        } catch (IllegalArgumentException e) {
            return Particle.CRIT;
        }
    }

    static void particleAt(Location loc, Particle particle, int count,
                           double ox, double oy, double oz, double extra) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        try {
            if (particle == Particle.DUST) {
                world.spawnParticle(particle, loc, count, ox, oy, oz, extra,
                        new Particle.DustOptions(Color.fromRGB(120, 200, 255), 1.2f));
                return;
            }
            if (particle == Particle.ENTITY_EFFECT) {
                world.spawnParticle(particle, loc, count, ox, oy, oz, extra, Color.fromRGB(160, 90, 255));
                return;
            }
            world.spawnParticle(particle, loc, count, ox, oy, oz, extra);
        } catch (IllegalArgumentException ex) {
            world.spawnParticle(Particle.CRIT, loc, count, ox, oy, oz, extra);
        }
    }

    static void beam(Location from, Location to, Particle particle, int steps) {
        if (from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return;
        }
        steps = Math.max(4, steps);
        org.bukkit.util.Vector delta = to.toVector().subtract(from.toVector());
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            particleAt(from.clone().add(delta.clone().multiply(t)), particle, 2, 0.02, 0.02, 0.02, 0);
        }
    }

    static void ring(Location center, Particle particle, double radius, int points) {
        if (center.getWorld() == null) {
            return;
        }
        for (int i = 0; i < points; i++) {
            double ang = (Math.PI * 2 * i) / points;
            particleAt(center.clone().add(Math.cos(ang) * radius, 0, Math.sin(ang) * radius), particle, 1, 0, 0, 0, 0);
        }
    }

    void expandingRing(Player player, Particle particle, double maxRadius, int ticks) {
        AtomicInteger tick = new AtomicInteger();
        YapSched.entityTimer(plugin, player, task -> {
            if (!player.isOnline() || tick.incrementAndGet() > ticks) {
                task.cancel();
                return;
            }
            double r = (maxRadius * tick.get()) / ticks;
            ring(player.getLocation().add(0, 0.4, 0), particle, r, 32);
        }, 1L, 1L);
    }

    void followTrail(Player player, Particle particle, int ticks, long period) {
        AtomicInteger left = new AtomicInteger(ticks);
        YapSched.entityTimer(plugin, player, task -> {
            if (!player.isOnline() || left.decrementAndGet() < 0) {
                task.cancel();
                return;
            }
            particleAt(player.getLocation().add(0, 0.9, 0), particle, 6, 0.15, 0.2, 0.15, 0.01);
        }, 1L, Math.max(1L, period));
    }

    void followEntityTrail(Entity entity, Particle particle, int ticks, long period) {
        AtomicInteger left = new AtomicInteger(ticks);
        YapSched.entityTimer(plugin, entity, task -> {
            if (!entity.isValid() || entity.isDead() || left.decrementAndGet() < 0) {
                task.cancel();
                return;
            }
            particleAt(entity.getLocation(), particle, 4, 0.08, 0.08, 0.08, 0.01);
        }, 1L, Math.max(1L, period));
    }

    void breakBlocks(Player player, AbilityDefinition ability, boolean doFx) {
        int reach = Math.max(1, ability.paramInt("range", 5));
        int radius = Math.max(0, ability.paramInt("radius", 0));
        int maxBlocks = Math.max(1, ability.paramInt("amount", ability.paramInt("count", 1)));
        Block focus = player.getTargetBlockExact(reach);
        if (focus != null && !focus.getType().isAir() && focus.getType().getHardness() >= 0) {
            Location at = focus.getLocation().add(0.5, 0.5, 0.5);
            if (doFx) {
                beam(player.getEyeLocation(), at, Particle.FLAME, 14);
                particleAt(at, Particle.LAVA, 10, 0.25, 0.25, 0.25, 0.02);
            }
            java.util.List<Block> toBreak = new java.util.ArrayList<>();
            toBreak.add(focus);
            if (radius > 0) {
                World world = focus.getWorld();
                int fx0 = focus.getX();
                int fy = focus.getY();
                int fz = focus.getZ();
                java.util.List<Block> candidates = new java.util.ArrayList<>();
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -radius; dy <= radius; dy++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) {
                                continue;
                            }
                            Block b = world.getBlockAt(fx0 + dx, fy + dy, fz + dz);
                            if (!b.getType().isAir() && b.getType().getHardness() >= 0) {
                                candidates.add(b);
                            }
                        }
                    }
                }
                candidates.sort((a, b) -> Double.compare(
                        a.getLocation().distanceSquared(focus.getLocation()),
                        b.getLocation().distanceSquared(focus.getLocation())));
                for (Block b : candidates) {
                    if (toBreak.size() >= maxBlocks) {
                        break;
                    }
                    toBreak.add(b);
                }
            }
            ItemStack tool = player.getInventory().getItemInMainHand();
            for (Block b : toBreak) {
                YapSched.region(plugin, b.getLocation(), () -> b.breakNaturally(tool));
            }
        }
        String msg = ability.paramString("message", null);
        if (msg != null && !msg.isBlank()) {
            player.sendMessage(LEGACY.deserialize(msg));
        }
        if (doFx) {
            burst(player, ability);
        }
    }

    void areaEffect(Player player, AbilityDefinition ability, boolean doFx, java.util.function.Function<String, org.bukkit.potion.PotionEffectType> potionLookup) {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        Object rawList = ability.params().get("effects");
        if (rawList instanceof java.util.List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    String e = String.valueOf(o).trim();
                    if (!e.isEmpty()) {
                        names.add(e);
                    }
                }
            }
        }
        String primary = ability.paramString("effect", null);
        if (primary != null && !primary.isBlank()) {
            names.add(primary);
        }
        for (int i = 2; i <= 6; i++) {
            String extra = ability.paramString("effect" + i, null);
            if (extra != null && !extra.isBlank()) {
                names.add(extra);
            }
        }
        if (names.isEmpty()) {
            names.add("SLOWNESS");
        }
        double radius = ability.paramDouble("radius", 4);
        int duration = ability.paramInt("duration", 60);
        if (duration < 0) {
            duration = -1;
        }
        int baseAmplifier = Math.max(0, Math.min(99, ability.paramInt("amplifier", 0)));
        java.util.List<org.bukkit.potion.PotionEffect> built = new java.util.ArrayList<>();
        for (String name : names) {
            org.bukkit.potion.PotionEffectType potionType = potionLookup.apply(name);
            if (potionType != null) {
                built.add(new org.bukkit.potion.PotionEffect(
                        potionType, duration, PotionAmpLimits.clamp(name, baseAmplifier)));
            }
        }
        if (!built.isEmpty()) {
            for (Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
                if (nearby instanceof org.bukkit.entity.LivingEntity living && nearby != player) {
                    for (org.bukkit.potion.PotionEffect effect : built) {
                        living.addPotionEffect(effect);
                    }
                    if (doFx) {
                        particleAt(living.getLocation().add(0, 1, 0), Particle.SNOWFLAKE, 8, 0.3, 0.4, 0.3, 0.01);
                    }
                }
            }
            if (ability.paramBool("self", false)) {
                for (org.bukkit.potion.PotionEffect effect : built) {
                    player.addPotionEffect(effect);
                }
            }
        }
        if (doFx) {
            Particle p = resolveParticle(ability);
            burst(player, ability);
            ring(player.getLocation().add(0, 0.3, 0), p, radius, 48);
            expandingRing(player, p, radius, 8);
        }
    }

    void pull(Player player, AbilityDefinition ability, boolean doFx) {
        double radius = ability.paramDouble("radius", 6);
        double strength = ability.paramDouble("strength", 1.2);
        Location origin = player.getLocation();
        for (Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof org.bukkit.entity.LivingEntity living) || nearby == player) {
                continue;
            }
            org.bukkit.util.Vector pull = origin.toVector().subtract(living.getLocation().toVector()).normalize().multiply(strength);
            pull.setY(Math.max(0.15, pull.getY()));
            living.setVelocity(pull);
            if (doFx) {
                particleAt(living.getLocation().add(0, 1, 0), Particle.PORTAL, 10, 0.2, 0.3, 0.2, 0.02);
            }
        }
        if (doFx) {
            burst(player, ability);
            ring(origin.add(0, 0.3, 0), Particle.PORTAL, radius, 40);
        }
    }

    void push(Player player, AbilityDefinition ability, boolean doFx) {
        double radius = ability.paramDouble("radius", 5);
        double strength = ability.paramDouble("strength", 1.4);
        Location origin = player.getLocation();
        for (Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof org.bukkit.entity.LivingEntity living) || nearby == player) {
                continue;
            }
            org.bukkit.util.Vector push = living.getLocation().toVector().subtract(origin.toVector());
            if (push.lengthSquared() < 1.0e-4) {
                push = player.getLocation().getDirection();
            }
            push.normalize().multiply(strength);
            push.setY(Math.max(0.25, push.getY()));
            living.setVelocity(push);
            if (doFx) {
                particleAt(living.getLocation().add(0, 1, 0), Particle.CLOUD, 8, 0.2, 0.2, 0.2, 0.02);
            }
        }
        if (doFx) {
            burst(player, ability);
            ring(origin.clone().add(0, 0.3, 0), Particle.CLOUD, radius, 36);
        }
    }

    record FxProfile(String sound, String particle, int count) {
    }
}
