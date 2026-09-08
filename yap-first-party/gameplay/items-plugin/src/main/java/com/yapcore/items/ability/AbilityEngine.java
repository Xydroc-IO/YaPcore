package com.yapcore.items.ability;

import com.yapcore.items.ItemsConfig;
import com.yapcore.items.ItemsPlugin;
import com.yapcore.items.item.ItemDefinition;
import com.yapcore.items.item.ItemFactory;
import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Dispatches config-driven item abilities with baked-in FX.
 * <p>
 * Avoids enum {@code switch} / synthetic {@code AbilityEngine$1} SwitchMap classes —
 * Folia's PluginClassLoader has been observed to fail loading those after jar replace.
 */
public final class AbilityEngine {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final Map<AbilityType, FxProfile> DEFAULT_FX = new EnumMap<>(AbilityType.class);

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
    private final ItemsConfig config;
    private final ItemFactory factory;
    private final CooldownService cooldowns;

    public AbilityEngine(ItemsPlugin plugin, ItemsConfig config, ItemFactory factory, CooldownService cooldowns) {
        this.plugin = plugin;
        this.config = config;
        this.factory = factory;
        this.cooldowns = cooldowns;
    }

    public boolean tryUse(Player player, ItemStack stack, AbilityDefinition.Trigger trigger) {
        var defOpt = factory.definitionOf(stack);
        if (defOpt.isEmpty()) {
            return false;
        }
        ItemDefinition def = defOpt.get();
        List<AbilityDefinition> abilities = def.abilities();
        if (abilities.isEmpty()) {
            return false;
        }
        if (def.permission() != null && !def.permission().isBlank() && !player.hasPermission(def.permission())) {
            player.sendMessage(LEGACY.deserialize(config.msgNoPerm() + " &8(" + def.permission() + ")"));
            return true;
        }

        boolean anyMatch = false;
        boolean fired = false;
        long longestRemain = 0L;
        AbilityDefinition.Trigger primary = primaryTrigger(def);
        for (int i = 0; i < abilities.size(); i++) {
            AbilityDefinition ability = abilities.get(i);
            if (!matchesTrigger(ability, trigger, primary)) {
                continue;
            }
            anyMatch = true;
            if (ability.permission() != null && !ability.permission().isBlank()
                    && !player.hasPermission(ability.permission())) {
                player.sendMessage(LEGACY.deserialize(config.msgNoPerm() + " &8(" + ability.permission() + ")"));
                continue;
            }
            String cdKey = cooldownKey(def.id(), i, ability);
            long remain = cooldowns.remainingMs(player.getUniqueId(), cdKey);
            if (remain > 0L) {
                longestRemain = Math.max(longestRemain, remain);
                continue;
            }
            if (!player.isOnline()) {
                return true;
            }
            try {
                execute(player, def, ability);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Ability " + ability.type() + " failed for " + def.id(), t);
                player.sendMessage(LEGACY.deserialize("&cAbility failed — see console."));
                continue;
            }
            cooldowns.set(player.getUniqueId(), cdKey, ability.cooldownMs());
            // Legacy shared key so old CD tools still affect the primary ability.
            if (i == 0) {
                cooldowns.set(player.getUniqueId(), def.id(), ability.cooldownMs());
            }
            fired = true;
        }
        if (!anyMatch) {
            return false;
        }
        if (!fired && longestRemain > 0L) {
            String msg = config.msgCooldown().replace("{seconds}",
                    String.format(Locale.ROOT, "%.1f", longestRemain / 1000.0));
            player.sendMessage(LEGACY.deserialize(msg));
            return true;
        }
        if (fired && (def.consume() || trigger == AbilityDefinition.Trigger.CONSUME)) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand != null && factory.idOf(hand).filter(id -> id.equals(def.id())).isPresent()) {
                hand.setAmount(hand.getAmount() - 1);
            }
        }
        return true;
    }

    /** First non-{@link AbilityDefinition.Trigger#TOGETHER} trigger, else right-click. */
    static AbilityDefinition.Trigger primaryTrigger(ItemDefinition def) {
        for (AbilityDefinition a : def.abilities()) {
            if (a.trigger() != AbilityDefinition.Trigger.TOGETHER) {
                return a.trigger();
            }
        }
        return AbilityDefinition.Trigger.RIGHT_CLICK;
    }

    static boolean matchesTrigger(
            AbilityDefinition ability,
            AbilityDefinition.Trigger requested,
            AbilityDefinition.Trigger primary) {
        if (ability.trigger() == requested) {
            return true;
        }
        return ability.trigger() == AbilityDefinition.Trigger.TOGETHER && requested == primary;
    }

    private static String cooldownKey(String itemId, int index, AbilityDefinition ability) {
        return itemId + "#" + index + ":" + ability.type().name().toLowerCase(Locale.ROOT);
    }

    private void execute(Player player, ItemDefinition def, AbilityDefinition ability) {
        boolean fx = ability.paramBool("fx", true);
        AbilityType type = ability.type();
        if (type == AbilityType.MESSAGE) {
            player.sendMessage(LEGACY.deserialize(ability.paramString("text", "&7*")));
            if (fx) {
                burst(player, ability);
            }
            return;
        }
        if (type == AbilityType.EFFECT) {
            applyEffect(player, ability, fx);
            return;
        }
        if (type == AbilityType.HEAL) {
            double amount = ability.paramDouble("amount", 4);
            player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + amount));
            if (fx) {
                burst(player, ability);
                ring(player.getLocation().add(0, 1, 0), resolveParticle(ability), 1.2, 24);
            }
            return;
        }
        if (type == AbilityType.FEED) {
            int food = ability.paramInt("food", 4);
            float sat = (float) ability.paramDouble("saturation", 2);
            player.setFoodLevel(Math.min(20, player.getFoodLevel() + food));
            player.setSaturation(Math.min(20f, player.getSaturation() + sat));
            if (fx) {
                burst(player, ability);
            }
            return;
        }
        if (type == AbilityType.LAUNCH) {
            double power = ability.paramDouble("power", 1.2);
            double y = ability.paramDouble("y", 0.5);
            Vector dir = player.getLocation().getDirection().normalize().multiply(power);
            dir.setY(y);
            Location start = player.getLocation().clone().add(0, 1, 0);
            player.setVelocity(dir);
            if (fx) {
                Particle p = resolveParticle(ability);
                burst(player, ability);
                followTrail(player, p, 12, 2L);
                particleAt(start, p, 18, 0.35, 0.2, 0.35, 0.05);
            }
            return;
        }
        if (type == AbilityType.DASH) {
            double range = ability.paramDouble("range", 6);
            Location start = player.getLocation().clone().add(0, 1, 0);
            Vector dir = player.getLocation().getDirection().normalize().multiply(range / 4.0);
            Location end = start.clone().add(dir.clone().normalize().multiply(Math.min(range, 6)));
            player.setVelocity(dir);
            if (fx) {
                Particle p = resolveParticle(ability);
                burst(player, ability);
                beam(start, end, p, 16);
                followTrail(player, p, 8, 1L);
            }
            return;
        }
        if (type == AbilityType.LIGHTNING) {
            Location loc = targetLocation(player, ability.paramDouble("range", 20));
            if (fx) {
                beam(player.getEyeLocation(), loc, Particle.ELECTRIC_SPARK, 20);
            }
            player.getWorld().strikeLightningEffect(loc);
            if (fx) {
                burstAt(loc, ability);
            }
            return;
        }
        if (type == AbilityType.LIGHTNING_DASH) {
            doLightningDash(player, ability, fx);
            return;
        }
        if (type == AbilityType.SMITE_TARGET) {
            Entity target = rayTarget(player, ability.paramDouble("range", 16));
            if (target != null) {
                if (fx) {
                    beam(player.getEyeLocation(), target.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 24);
                }
                target.getWorld().strikeLightningEffect(target.getLocation());
                if (target instanceof LivingEntity living) {
                    applyAbilityDamage(living, player, ability.paramDouble("damage", 6));
                }
                if (fx) {
                    burstAt(target.getLocation().add(0, 1, 0), ability);
                }
            } else if (fx) {
                playSound(player.getLocation(), "BLOCK_NOTE_BLOCK_BASS", 0.6f, 0.7f);
            }
            return;
        }
        if (type == AbilityType.EXPLODE) {
            Location loc = player.getLocation();
            float power = (float) ability.paramDouble("power", 1.5);
            boolean fire = ability.paramBool("fire", false);
            boolean breakBlocks = ability.paramBool("break-blocks", false);
            if (fx) {
                ring(loc.clone().add(0, 0.2, 0), Particle.EXPLOSION, 2.0, 20);
            }
            player.getWorld().createExplosion(loc, power, fire, breakBlocks, player);
            if (fx) {
                burst(player, ability);
            }
            return;
        }
        if (type == AbilityType.PROJECTILE) {
            String kind = ability.paramString("projectile", ability.paramString("kind", "snowball"))
                    .trim().toLowerCase(Locale.ROOT).replace('-', '_');
            double speed = ability.paramDouble("speed", 1.5);
            Vector vel = player.getLocation().getDirection().normalize().multiply(speed);
            org.bukkit.entity.Projectile thrown = switch (kind) {
                case "arrow", "spectral_arrow" -> player.launchProjectile(org.bukkit.entity.Arrow.class);
                case "egg" -> player.launchProjectile(org.bukkit.entity.Egg.class);
                case "ender_pearl", "pearl" -> player.launchProjectile(org.bukkit.entity.EnderPearl.class);
                case "snowball" -> player.launchProjectile(Snowball.class);
                case "fireball", "small_fireball" -> player.launchProjectile(org.bukkit.entity.SmallFireball.class);
                default -> player.launchProjectile(Snowball.class);
            };
            thrown.setVelocity(vel);
            if (fx) {
                burst(player, ability);
                followEntityTrail(thrown, resolveParticle(ability), 20, 1L);
            }
            return;
        }
        if (type == AbilityType.COMMAND_PLAYER) {
            String cmd = ability.paramString("command", "").replace("{player}", player.getName());
            if (!cmd.isBlank()) {
                player.performCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
            }
            if (fx) {
                burst(player, ability);
            }
            return;
        }
        if (type == AbilityType.COMMAND_CONSOLE) {
            String cmd = ability.paramString("command", "").replace("{player}", player.getName());
            if (!cmd.isBlank()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.startsWith("/") ? cmd.substring(1) : cmd);
            }
            if (fx) {
                burst(player, ability);
            }
            return;
        }
        if (type == AbilityType.SOUND) {
            playSound(player.getLocation(), ability.paramString("sound", "ENTITY_EXPERIENCE_ORB_PICKUP"), 1f, 1f);
            return;
        }
        if (type == AbilityType.PARTICLE) {
            FxProfile d = defaults(ability);
            particleAt(player.getLocation().add(0, 1, 0), resolveParticle(ability),
                    ability.paramInt("count", d.count()), 0.4, 0.6, 0.4, 0.04);
            playSound(player.getLocation(), ability.paramString("sound", d.sound()), 0.8f, 1.1f);
            return;
        }
        if (type == AbilityType.GIVE_ITEM) {
            String giveId = ability.paramString("item", "");
            int amount = ability.paramInt("amount", 1);
            factory.create(giveId, amount).ifPresent(stack -> player.getInventory().addItem(stack));
            if (fx) {
                burst(player, ability);
            }
            return;
        }
        if (type == AbilityType.BREAK_BLOCK) {
            int reach = Math.max(1, ability.paramInt("range", 5));
            int radius = Math.max(0, ability.paramInt("radius", 0));
            int maxBlocks = Math.max(1, ability.paramInt("amount", ability.paramInt("count", 1)));
            Block focus = player.getTargetBlockExact(reach);
            if (focus != null && !focus.getType().isAir() && focus.getType().getHardness() >= 0) {
                Location at = focus.getLocation().add(0.5, 0.5, 0.5);
                if (fx) {
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
            if (fx) {
                burst(player, ability);
            }
            return;
        }
        if (type == AbilityType.AREA_EFFECT) {
            PotionEffectType potionType = potion(ability.paramString("effect", "SLOWNESS"));
            double radius = ability.paramDouble("radius", 4);
            if (potionType != null) {
                PotionEffect effect = new PotionEffect(
                        potionType,
                        ability.paramInt("duration", 60),
                        ability.paramInt("amplifier", 0));
                for (Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
                    if (nearby instanceof LivingEntity living && nearby != player) {
                        living.addPotionEffect(effect);
                        if (fx) {
                            particleAt(living.getLocation().add(0, 1, 0), Particle.SNOWFLAKE, 8, 0.3, 0.4, 0.3, 0.01);
                        }
                    }
                }
                if (ability.paramBool("self", false)) {
                    player.addPotionEffect(effect);
                }
            }
            if (fx) {
                Particle p = resolveParticle(ability);
                burst(player, ability);
                ring(player.getLocation().add(0, 0.3, 0), p, radius, 48);
                expandingRing(player, p, radius, 8);
            }
            return;
        }
        if (type == AbilityType.CLEANSE) {
            for (PotionEffect active : new java.util.ArrayList<>(player.getActivePotionEffects())) {
                player.removePotionEffect(active.getType());
            }
            if (fx) {
                burst(player, ability);
                ring(player.getLocation().add(0, 1, 0), resolveParticle(ability), 1.1, 20);
            }
            return;
        }
        if (type == AbilityType.ABSORB) {
            int amplifier = Math.max(0, ability.paramInt("amplifier", 1));
            int duration = ability.paramInt("duration", 200);
            PotionEffectType absorb = potion("ABSORPTION");
            if (absorb != null) {
                player.addPotionEffect(new PotionEffect(absorb, duration, amplifier));
            }
            if (fx) {
                burst(player, ability);
            }
            return;
        }
        if (type == AbilityType.FIREBALL) {
            org.bukkit.entity.SmallFireball ball = player.launchProjectile(org.bukkit.entity.SmallFireball.class);
            ball.setYield((float) ability.paramDouble("power", 1.0));
            ball.setIsIncendiary(ability.paramBool("fire", true));
            ball.setDirection(player.getLocation().getDirection().normalize()
                    .multiply(ability.paramDouble("speed", 1.2)));
            if (fx) {
                burst(player, ability);
                followEntityTrail(ball, Particle.FLAME, 24, 1L);
            }
            return;
        }
        if (type == AbilityType.PULL) {
            double radius = ability.paramDouble("radius", 6);
            double strength = ability.paramDouble("strength", 1.2);
            Location origin = player.getLocation();
            for (Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
                if (!(nearby instanceof LivingEntity living) || nearby == player) {
                    continue;
                }
                Vector pull = origin.toVector().subtract(living.getLocation().toVector()).normalize().multiply(strength);
                pull.setY(Math.max(0.15, pull.getY()));
                living.setVelocity(pull);
                if (fx) {
                    particleAt(living.getLocation().add(0, 1, 0), Particle.PORTAL, 10, 0.2, 0.3, 0.2, 0.02);
                }
            }
            if (fx) {
                burst(player, ability);
                ring(origin.add(0, 0.3, 0), Particle.PORTAL, radius, 40);
            }
            return;
        }
        if (type == AbilityType.PUSH) {
            double radius = ability.paramDouble("radius", 5);
            double strength = ability.paramDouble("strength", 1.4);
            Location origin = player.getLocation();
            for (Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
                if (!(nearby instanceof LivingEntity living) || nearby == player) {
                    continue;
                }
                Vector push = living.getLocation().toVector().subtract(origin.toVector());
                if (push.lengthSquared() < 1.0e-4) {
                    push = player.getLocation().getDirection();
                }
                push.normalize().multiply(strength);
                push.setY(Math.max(0.25, push.getY()));
                living.setVelocity(push);
                if (fx) {
                    particleAt(living.getLocation().add(0, 1, 0), Particle.CLOUD, 8, 0.2, 0.2, 0.2, 0.02);
                }
            }
            if (fx) {
                burst(player, ability);
                ring(origin.clone().add(0, 0.3, 0), Particle.CLOUD, radius, 36);
            }
            return;
        }
        if (type == AbilityType.BLINK) {
            double range = ability.paramDouble("range", 8);
            Location start = player.getLocation().clone();
            Vector dir = player.getLocation().getDirection();
            if (dir.lengthSquared() < 1.0e-6) {
                dir = new Vector(0, 0, 1);
            } else {
                dir.normalize();
            }
            Location dest = findDashDestination(player, dir, range);
            dest.setYaw(start.getYaw());
            dest.setPitch(start.getPitch());
            if (fx) {
                beam(start.clone().add(0, 1, 0), dest.clone().add(0, 1, 0), Particle.PORTAL, 20);
            }
            player.teleportAsync(dest);
            if (fx) {
                burst(player, ability);
            }
            return;
        }
        if (type == AbilityType.GROUND_SLAM) {
            double radius = ability.paramDouble("radius", 4);
            double damage = ability.paramDouble("damage", 6);
            Location origin = player.getLocation();
            player.setVelocity(new Vector(0, ability.paramDouble("hop", 0.35), 0));
            for (Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
                if (nearby instanceof LivingEntity living && nearby != player) {
                    applyAbilityDamage(living, player, damage);
                    Vector knock = living.getLocation().toVector().subtract(origin.toVector());
                    if (knock.lengthSquared() > 1.0e-4) {
                        living.setVelocity(knock.normalize().multiply(0.8).setY(0.35));
                    }
                }
            }
            if (fx) {
                burst(player, ability);
                ring(origin.clone().add(0, 0.2, 0), Particle.EXPLOSION, radius, 28);
                playSound(origin, "ENTITY_GENERIC_EXPLODE", 0.7f, 1.1f);
            }
            return;
        }
        if (type == AbilityType.REPAIR) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand != null && !hand.getType().isAir() && hand.getType().getMaxDurability() > 0) {
                int amount = ability.paramInt("amount", 50);
                hand.editMeta(meta -> {
                    if (meta instanceof org.bukkit.inventory.meta.Damageable dmg) {
                        dmg.setDamage(Math.max(0, dmg.getDamage() - amount));
                    }
                });
            }
            if (fx) {
                burst(player, ability);
            }
        }
    }

    private void doLightningDash(Player player, AbilityDefinition ability, boolean fx) {
        double range = ability.paramDouble("range", 8);
        double damage = ability.paramDouble("damage", 4);
        Location start = player.getLocation().clone();
        Vector dir = player.getLocation().getDirection();
        if (dir.lengthSquared() < 1.0e-6) {
            dir = new Vector(0, 0, 1);
        } else {
            dir.normalize();
        }
        // Follow look direction in 3D (not flattened to current Y).
        Location dest = findDashDestination(player, dir, range);
        dest.setYaw(start.getYaw());
        dest.setPitch(start.getPitch());
        if (fx) {
            beam(start.clone().add(0, 1, 0), dest.clone().add(0, 1, 0), Particle.ELECTRIC_SPARK, 28);
            particleAt(start.clone().add(0, 1, 0), Particle.FLASH, 1, 0, 0, 0, 0);
        }
        player.getWorld().strikeLightningEffect(start);
        player.teleportAsync(dest).thenAccept(ok -> YapSched.entity(plugin, player, () ->
                finishLightningDash(player, damage, fx)));
    }

    /**
     * Walks along {@code dir} up to {@code range} and returns the farthest location where the
     * player can stand (feet + head clear). Stops before solids so ceilings/walls clip the dash.
     */
    private static Location findDashDestination(Player player, Vector dir, double range) {
        Location start = player.getLocation();
        World world = start.getWorld();
        Location best = start.clone();
        if (world == null || range <= 0) {
            return best;
        }
        double step = 0.4;
        double max = Math.max(step, range);
        for (double d = step; d <= max + 1.0e-6; d += step) {
            Location cand = start.clone().add(dir.clone().multiply(Math.min(d, max)));
            if (!isPassableStanding(world, cand)) {
                break;
            }
            best = cand;
        }
        return best;
    }

    private static boolean isPassableStanding(World world, Location feet) {
        int x = feet.getBlockX();
        int y = feet.getBlockY();
        int z = feet.getBlockZ();
        if (y < world.getMinHeight() || y + 1 > world.getMaxHeight()) {
            return false;
        }
        return !world.getBlockAt(x, y, z).getType().isSolid()
                && !world.getBlockAt(x, y + 1, z).getType().isSolid();
    }

    private void finishLightningDash(Player player, double damage, boolean fx) {
        if (!player.isOnline()) {
            return;
        }
        player.getWorld().strikeLightningEffect(player.getLocation());
        if (fx) {
            particleAt(player.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 40, 0.6, 0.8, 0.6, 0.08);
            playSound(player.getLocation(), "ENTITY_LIGHTNING_BOLT_THUNDER", 0.55f, 1.35f);
            playSound(player.getLocation(), "ENTITY_LIGHTNING_BOLT_IMPACT", 0.8f, 1.1f);
        }
        for (Entity nearby : player.getNearbyEntities(3, 3, 3)) {
            if (nearby instanceof LivingEntity living && nearby != player) {
                applyAbilityDamage(living, player, damage);
            }
        }
    }

    /** Damage &lt; 0 (create UI "kill") or huge values = instant kill. */
    private static void applyAbilityDamage(LivingEntity living, Player source, double damage) {
        if (damage < 0 || damage >= 1_000_000D) {
            living.setHealth(0.0);
            return;
        }
        if (damage > 0) {
            living.damage(damage, source);
        }
    }

    private void applyEffect(Player player, AbilityDefinition ability, boolean fx) {
        int duration = ability.paramInt("duration", 100);
        int amplifier = ability.paramInt("amplifier", 0);
        // Multi-effect: params.effects as list of names, or effect / effect2 / effect3
        java.util.List<String> names = new java.util.ArrayList<>();
        Object rawList = ability.params().get("effects");
        if (rawList instanceof java.util.List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    names.add(String.valueOf(o));
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
            names.add("SPEED");
        }
        boolean any = false;
        for (String name : names) {
            PotionEffectType type = potion(name);
            if (type == null) {
                continue;
            }
            player.addPotionEffect(new PotionEffect(type, duration, amplifier));
            any = true;
        }
        if (!any) {
            return;
        }
        if (fx) {
            burst(player, ability);
            ring(player.getLocation().add(0, 1, 0), resolveParticle(ability), 0.9, 20);
        }
    }

    private FxProfile defaults(AbilityDefinition ability) {
        FxProfile profile = DEFAULT_FX.get(ability.type());
        return profile != null ? profile : new FxProfile("ENTITY_EXPERIENCE_ORB_PICKUP", "CRIT", 12);
    }

    private void burst(Player player, AbilityDefinition ability) {
        burstAt(player.getLocation().add(0, 1, 0), ability);
    }

    private void burstAt(Location loc, AbilityDefinition ability) {
        FxProfile d = defaults(ability);
        playSound(loc, ability.paramString("sound", d.sound()), 1f, 1f);
        particleAt(loc, resolveParticle(ability), ability.paramInt("count", d.count()), 0.35, 0.55, 0.35, 0.04);
    }

    private Particle resolveParticle(AbilityDefinition ability) {
        return particleOf(ability.paramString("particle", defaults(ability).particle()));
    }

    private void playSound(Location loc, String name, float volume, float pitch) {
        if (name == null || name.isBlank() || loc.getWorld() == null) {
            return;
        }
        try {
            Sound sound = Sound.valueOf(name.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_'));
            loc.getWorld().playSound(loc, sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static Particle particleOf(String name) {
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

    private static void particleAt(Location loc, Particle particle, int count,
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

    private static void beam(Location from, Location to, Particle particle, int steps) {
        if (from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return;
        }
        steps = Math.max(4, steps);
        Vector delta = to.toVector().subtract(from.toVector());
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            particleAt(from.clone().add(delta.clone().multiply(t)), particle, 2, 0.02, 0.02, 0.02, 0);
        }
    }

    private static void ring(Location center, Particle particle, double radius, int points) {
        if (center.getWorld() == null) {
            return;
        }
        for (int i = 0; i < points; i++) {
            double ang = (Math.PI * 2 * i) / points;
            particleAt(center.clone().add(Math.cos(ang) * radius, 0, Math.sin(ang) * radius), particle, 1, 0, 0, 0, 0);
        }
    }

    private void expandingRing(Player player, Particle particle, double maxRadius, int ticks) {
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

    private void followTrail(Player player, Particle particle, int ticks, long period) {
        AtomicInteger left = new AtomicInteger(ticks);
        YapSched.entityTimer(plugin, player, task -> {
            if (!player.isOnline() || left.decrementAndGet() < 0) {
                task.cancel();
                return;
            }
            particleAt(player.getLocation().add(0, 0.9, 0), particle, 6, 0.15, 0.2, 0.15, 0.01);
        }, 1L, Math.max(1L, period));
    }

    private void followEntityTrail(Entity entity, Particle particle, int ticks, long period) {
        AtomicInteger left = new AtomicInteger(ticks);
        YapSched.entityTimer(plugin, entity, task -> {
            if (!entity.isValid() || entity.isDead() || left.decrementAndGet() < 0) {
                task.cancel();
                return;
            }
            particleAt(entity.getLocation(), particle, 4, 0.08, 0.08, 0.08, 0.01);
        }, 1L, Math.max(1L, period));
    }

    private static PotionEffectType potion(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.toLowerCase(Locale.ROOT).replace(' ', '_');
        PotionEffectType byKey = PotionEffectType.getByName(key);
        if (byKey != null) {
            return byKey;
        }
        return PotionEffectType.getByName(key.toUpperCase(Locale.ROOT));
    }

    private static Location targetLocation(Player player, double range) {
        RayTraceResult hit = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(), player.getLocation().getDirection(), range);
        if (hit != null && hit.getHitPosition() != null) {
            return hit.getHitPosition().toLocation(player.getWorld());
        }
        return player.getEyeLocation().add(player.getLocation().getDirection().normalize().multiply(range));
    }

    private static Entity rayTarget(Player player, double range) {
        RayTraceResult hit = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getLocation().getDirection(),
                range,
                e -> e instanceof LivingEntity && e != player);
        return hit == null ? null : hit.getHitEntity();
    }

    private record FxProfile(String sound, String particle, int count) {
    }
}
