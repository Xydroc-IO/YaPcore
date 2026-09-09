package com.yapcore.items.ability;

import com.yapcore.items.ItemsPlugin;
import com.yapcore.items.item.ItemDefinition;
import com.yapcore.items.item.ItemFactory;
import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Locale;

/**
 * Executes config-driven ability types.
 * <p>
 * Avoids enum {@code switch} / synthetic SwitchMap classes —
 * Folia's PluginClassLoader has been observed to fail loading those after jar replace.
 */
final class AbilityExecutor {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final ItemsPlugin plugin;
    private final ItemFactory factory;
    private final AbilityFx fx;

    AbilityExecutor(ItemsPlugin plugin, ItemFactory factory, AbilityFx fx) {
        this.plugin = plugin;
        this.factory = factory;
        this.fx = fx;
    }

    void execute(Player player, ItemDefinition def, AbilityDefinition ability) {
        boolean doFx = ability.paramBool("fx", true);
        AbilityType type = ability.type();
        if (type == AbilityType.MESSAGE) {
            player.sendMessage(LEGACY.deserialize(ability.paramString("text", "&7*")));
            if (doFx) {
                fx.burst(player, ability);
            }
            return;
        }
        if (type == AbilityType.EFFECT) {
            applyEffect(player, ability, doFx);
            return;
        }
        if (type == AbilityType.HEAL) {
            double amount = ability.paramDouble("amount", 4);
            player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + amount));
            if (doFx) {
                fx.burst(player, ability);
                AbilityFx.ring(player.getLocation().add(0, 1, 0), fx.resolveParticle(ability), 1.2, 24);
            }
            return;
        }
        if (type == AbilityType.FEED) {
            int food = ability.paramInt("food", 4);
            float sat = (float) ability.paramDouble("saturation", 2);
            player.setFoodLevel(Math.min(20, player.getFoodLevel() + food));
            player.setSaturation(Math.min(20f, player.getSaturation() + sat));
            if (doFx) {
                fx.burst(player, ability);
            }
            return;
        }
        if (type == AbilityType.LAUNCH) {
            double power = Math.min(ability.paramDouble("power", 1.2), 2.0);
            double y = Math.min(Math.max(ability.paramDouble("y", 0.5), 0.0), 1.2);
            Vector dir = player.getLocation().getDirection().normalize().multiply(power);
            dir.setY(y);
            Location start = player.getLocation().clone().add(0, 1, 0);
            player.setVelocity(dir);
            if (doFx) {
                Particle p = fx.resolveParticle(ability);
                fx.burst(player, ability);
                fx.followTrail(player, p, 12, 2L);
                AbilityFx.particleAt(start, p, 18, 0.35, 0.2, 0.35, 0.05);
            }
            return;
        }
        if (type == AbilityType.DASH) {
            // Cap dash impulse — UI range goes to 100, which previously meant velocity 25.
            double range = Math.min(ability.paramDouble("range", 6), 12);
            double speed = Math.min(range / 4.0, 2.5);
            Location start = player.getLocation().clone().add(0, 1, 0);
            Vector dir = player.getLocation().getDirection().normalize().multiply(speed);
            Location end = start.clone().add(dir.clone().normalize().multiply(Math.min(range, 6)));
            player.setVelocity(dir);
            if (doFx) {
                Particle p = fx.resolveParticle(ability);
                fx.burst(player, ability);
                AbilityFx.beam(start, end, p, 16);
                fx.followTrail(player, p, 8, 1L);
            }
            return;
        }
        if (type == AbilityType.LIGHTNING) {
            Location loc = targetLocation(player, ability.paramDouble("range", 20));
            if (doFx) {
                AbilityFx.beam(player.getEyeLocation(), loc, Particle.ELECTRIC_SPARK, 20);
            }
            player.getWorld().strikeLightningEffect(loc);
            if (doFx) {
                fx.burstAt(loc, ability);
            }
            return;
        }
        if (type == AbilityType.LIGHTNING_DASH) {
            doLightningDash(player, ability, doFx);
            return;
        }
        if (type == AbilityType.SMITE_TARGET) {
            Entity target = rayTarget(player, ability.paramDouble("range", 16));
            if (target != null) {
                if (doFx) {
                    AbilityFx.beam(player.getEyeLocation(), target.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 24);
                }
                target.getWorld().strikeLightningEffect(target.getLocation());
                if (target instanceof LivingEntity living) {
                    applyAbilityDamage(living, player, ability.paramDouble("damage", 6));
                }
                if (doFx) {
                    fx.burstAt(target.getLocation().add(0, 1, 0), ability);
                }
            } else if (doFx) {
                fx.playSound(player.getLocation(), "BLOCK_NOTE_BLOCK_BASS", 0.6f, 0.7f);
            }
            return;
        }
        if (type == AbilityType.EXPLODE) {
            Location loc = player.getLocation();
            float power = (float) ability.paramDouble("power", 1.5);
            boolean fire = ability.paramBool("fire", false);
            boolean breakBlocks = ability.paramBool("break-blocks", false);
            if (doFx) {
                AbilityFx.ring(loc.clone().add(0, 0.2, 0), Particle.EXPLOSION, 2.0, 20);
            }
            player.getWorld().createExplosion(loc, power, fire, breakBlocks, player);
            if (doFx) {
                fx.burst(player, ability);
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
            if (doFx) {
                fx.burst(player, ability);
                fx.followEntityTrail(thrown, fx.resolveParticle(ability), 20, 1L);
            }
            return;
        }
        if (type == AbilityType.COMMAND_PLAYER) {
            String cmd = ability.paramString("command", "").replace("{player}", player.getName());
            if (!cmd.isBlank()) {
                player.performCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
            }
            if (doFx) {
                fx.burst(player, ability);
            }
            return;
        }
        if (type == AbilityType.COMMAND_CONSOLE) {
            String cmd = ability.paramString("command", "").replace("{player}", player.getName());
            if (!cmd.isBlank()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.startsWith("/") ? cmd.substring(1) : cmd);
            }
            if (doFx) {
                fx.burst(player, ability);
            }
            return;
        }
        if (type == AbilityType.SOUND) {
            fx.playSound(player.getLocation(), ability.paramString("sound", "ENTITY_EXPERIENCE_ORB_PICKUP"), 1f, 1f);
            return;
        }
        if (type == AbilityType.PARTICLE) {
            AbilityFx.FxProfile d = fx.defaults(ability);
            AbilityFx.particleAt(player.getLocation().add(0, 1, 0), fx.resolveParticle(ability),
                    ability.paramInt("count", d.count()), 0.4, 0.6, 0.4, 0.04);
            fx.playSound(player.getLocation(), ability.paramString("sound", d.sound()), 0.8f, 1.1f);
            return;
        }
        if (type == AbilityType.GIVE_ITEM) {
            String giveId = ability.paramString("item", "");
            int amount = ability.paramInt("amount", 1);
            factory.create(giveId, amount).ifPresent(stack -> player.getInventory().addItem(stack));
            if (doFx) {
                fx.burst(player, ability);
            }
            return;
        }
        if (type == AbilityType.BREAK_BLOCK) {
            fx.breakBlocks(player, ability, doFx);
            return;
        }
        if (type == AbilityType.AREA_EFFECT) {
            fx.areaEffect(player, ability, doFx, AbilityExecutor::potion);
            return;
        }
        if (type == AbilityType.CLEANSE) {
            for (PotionEffect active : new java.util.ArrayList<>(player.getActivePotionEffects())) {
                player.removePotionEffect(active.getType());
            }
            if (doFx) {
                fx.burst(player, ability);
                AbilityFx.ring(player.getLocation().add(0, 1, 0), fx.resolveParticle(ability), 1.1, 20);
            }
            return;
        }
        if (type == AbilityType.ABSORB) {
            int amplifier = Math.max(0, Math.min(99, ability.paramInt("amplifier", 1)));
            int duration = ability.paramInt("duration", 200);
            if (duration < 0) {
                duration = -1;
            }
            PotionEffectType absorb = potion("ABSORPTION");
            if (absorb != null) {
                player.addPotionEffect(new PotionEffect(absorb, duration, amplifier));
            }
            if (doFx) {
                fx.burst(player, ability);
            }
            return;
        }
        if (type == AbilityType.FIREBALL) {
            org.bukkit.entity.SmallFireball ball = player.launchProjectile(org.bukkit.entity.SmallFireball.class);
            ball.setYield((float) ability.paramDouble("power", 1.0));
            ball.setIsIncendiary(ability.paramBool("fire", true));
            ball.setDirection(player.getLocation().getDirection().normalize()
                    .multiply(ability.paramDouble("speed", 1.2)));
            if (doFx) {
                fx.burst(player, ability);
                fx.followEntityTrail(ball, Particle.FLAME, 24, 1L);
            }
            return;
        }
        if (type == AbilityType.PULL) {
            fx.pull(player, ability, doFx);
            return;
        }
        if (type == AbilityType.PUSH) {
            fx.push(player, ability, doFx);
            return;
        }
        if (type == AbilityType.BLINK) {
            double range = Math.min(ability.paramDouble("range", 8), 48);
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
            if (doFx) {
                AbilityFx.beam(start.clone().add(0, 1, 0), dest.clone().add(0, 1, 0), Particle.PORTAL, 20);
            }
            player.teleportAsync(dest);
            if (doFx) {
                fx.burst(player, ability);
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
            if (doFx) {
                fx.burst(player, ability);
                AbilityFx.ring(origin.clone().add(0, 0.2, 0), Particle.EXPLOSION, radius, 28);
                fx.playSound(origin, "ENTITY_GENERIC_EXPLODE", 0.7f, 1.1f);
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
            if (doFx) {
                fx.burst(player, ability);
            }
        }
    }

    private void doLightningDash(Player player, AbilityDefinition ability, boolean doFx) {
        double range = Math.min(ability.paramDouble("range", 8), 48);
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
        if (doFx) {
            AbilityFx.beam(start.clone().add(0, 1, 0), dest.clone().add(0, 1, 0), Particle.ELECTRIC_SPARK, 28);
            AbilityFx.particleAt(start.clone().add(0, 1, 0), Particle.FLASH, 1, 0, 0, 0, 0);
        }
        player.getWorld().strikeLightningEffect(start);
        player.teleportAsync(dest).thenAccept(ok -> YapSched.entity(plugin, player, () ->
                finishLightningDash(player, damage, doFx)));
    }

    private void finishLightningDash(Player player, double damage, boolean doFx) {
        if (!player.isOnline()) {
            return;
        }
        player.getWorld().strikeLightningEffect(player.getLocation());
        if (doFx) {
            AbilityFx.particleAt(player.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 40, 0.6, 0.8, 0.6, 0.08);
            fx.playSound(player.getLocation(), "ENTITY_LIGHTNING_BOLT_THUNDER", 0.55f, 1.35f);
            fx.playSound(player.getLocation(), "ENTITY_LIGHTNING_BOLT_IMPACT", 0.8f, 1.1f);
        }
        for (Entity nearby : player.getNearbyEntities(3, 3, 3)) {
            if (nearby instanceof LivingEntity living && nearby != player) {
                applyAbilityDamage(living, player, damage);
            }
        }
    }

    /**
     * Walks along {@code dir} up to {@code range} and returns the farthest location where the
     * player can stand (feet + head clear). Stops before solids so ceilings/walls clip the dash.
     * Snaps down onto solid ground so mid-air landings don't feel floaty / glitchy.
     */
    private static Location findDashDestination(Player player, Vector dir, double range) {
        Location start = player.getLocation();
        World world = start.getWorld();
        Location best = start.clone();
        if (world == null || range <= 0) {
            return best;
        }
        double step = 0.35;
        double max = Math.max(step, range);
        for (double d = step; d <= max + 1.0e-6; d += step) {
            Location cand = start.clone().add(dir.clone().multiply(Math.min(d, max)));
            if (!isPassableStanding(world, cand)) {
                break;
            }
            best = cand;
        }
        return snapDashToGround(world, best);
    }

    /** Drop onto the nearest solid under the feet (up to 10 blocks) while staying passable. */
    private static Location snapDashToGround(World world, Location feet) {
        Location loc = feet.clone();
        // Prefer standing on a block: search downward from current feet.
        for (int drop = 0; drop <= 10; drop++) {
            Location tryFeet = loc.clone().subtract(0, drop, 0);
            Location below = tryFeet.clone().subtract(0, 0.05, 0);
            if (world.getBlockAt(below).getType().isSolid() && isPassableStanding(world, tryFeet)) {
                tryFeet.setX(Math.floor(tryFeet.getX()) + 0.5);
                tryFeet.setZ(Math.floor(tryFeet.getZ()) + 0.5);
                tryFeet.setY(Math.floor(below.getY()) + 1.0);
                return tryFeet;
            }
        }
        // No ground nearby — keep air spot but center in the block for less wonky camera.
        loc.setX(Math.floor(loc.getX()) + 0.5);
        loc.setZ(Math.floor(loc.getZ()) + 0.5);
        return loc;
    }

    private static boolean isPassableStanding(World world, Location feet) {
        int x = feet.getBlockX();
        int y = feet.getBlockY();
        int z = feet.getBlockZ();
        if (y < world.getMinHeight() || y + 1 >= world.getMaxHeight()) {
            return false;
        }
        return !world.getBlockAt(x, y, z).getType().isSolid()
                && !world.getBlockAt(x, y + 1, z).getType().isSolid();
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

            private void applyEffect(Player player, AbilityDefinition ability, boolean doFx) {
        int duration = ability.paramInt("duration", 100);
        if (duration < 0) {
            duration = -1; // Paper infinite
        }
        int baseAmplifier = Math.max(0, Math.min(99, ability.paramInt("amplifier", 0)));
        // Multi-effect: params.effects list, and/or effect / effect2…effect6
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
            names.add("SPEED");
        }
        boolean any = false;
        for (String name : names) {
            PotionEffectType potionType = potion(name);
            if (potionType == null) {
                continue;
            }
            int amplifier = PotionAmpLimits.clamp(name, baseAmplifier);
            player.addPotionEffect(new PotionEffect(potionType, duration, amplifier));
            any = true;
        }
        if (!any) {
            return;
        }
        if (doFx) {
            fx.burst(player, ability);
            AbilityFx.ring(player.getLocation().add(0, 1, 0), fx.resolveParticle(ability), 0.9, 20);
        }
    }

    static PotionEffectType potion(String raw) {
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
}
