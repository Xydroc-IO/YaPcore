package com.yapcore.disasters;

import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Runs Folia-safe disaster FX for one active event per world. */
public final class DisasterManager {

    private final DisastersPlugin plugin;
    private final Map<UUID, Active> active = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public DisasterManager(DisastersPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
    }

    public void shutdown() {
        for (UUID id : active.keySet()) {
            stop(id, false);
        }
        active.clear();
    }

    public void stop(World world) {
        if (world != null) {
            stop(world.getUID(), true);
        }
    }

    public void stop(UUID worldId, boolean announce) {
        Active prev = active.remove(worldId);
        if (prev == null) {
            return;
        }
        if (prev.task != null) {
            prev.task.cancel();
        }
        if (prev.endTask != null) {
            prev.endTask.cancel();
        }
        prev.cancelUndos();
        if (announce && plugin.config().broadcastEnd()) {
            World w = Bukkit.getWorld(worldId);
            if (w != null) {
                Bukkit.broadcastMessage("§7Disaster ended in §f" + w.getName() + "§7.");
            }
        }
    }

    public String describeActive(World world) {
        if (world == null) {
            return "none";
        }
        Active a = active.get(world.getUID());
        return a == null ? "none" : a.type.configKey();
    }

    /** One-line status for console / dashboard. */
    public String statusReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("random=").append(plugin.randomEvents().statusLine());
        sb.append(" sites=").append(plugin.volcanoSites().all().size());
        sb.append(" grief=").append(plugin.config().grief());
        sb.append(" pending-warn=").append(plugin.warnings().pendingCount());
        sb.append(" undos=").append(undoTaskCount());
        boolean any = false;
        for (World world : Bukkit.getWorlds()) {
            Active a = active.get(world.getUID());
            if (a == null) {
                continue;
            }
            if (!any) {
                sb.append(" active=");
                any = true;
            } else {
                sb.append(',');
            }
            long left = Math.max(0L, (a.endsAtMs - System.currentTimeMillis()) / 1000L);
            sb.append(world.getName()).append(':').append(a.type.configKey()).append('(').append(left).append("s)");
        }
        if (!any) {
            sb.append(" active=none");
        }
        return sb.toString();
    }

    public int activeCount() {
        return active.size();
    }

    public int undoTaskCount() {
        int n = 0;
        for (Active a : active.values()) {
            n += a.undoCount();
        }
        return n;
    }

    public boolean isActive(World world) {
        return world != null && active.containsKey(world.getUID());
    }

    public boolean isActiveType(World world, DisasterType type) {
        if (world == null || type == null) {
            return false;
        }
        Active a = active.get(world.getUID());
        return a != null && a.type == type;
    }

    public boolean start(World world, DisasterType type, int durationSeconds, Location focus) {
        DisastersConfig cfg = plugin.config();
        if (!cfg.enabled() || !cfg.typeEnabled(type) || !cfg.worldAllowed(world.getName())) {
            return false;
        }
        plugin.warnings().cancel(world);
        stop(world.getUID(), false);

        SkyWeather.apply(plugin, world, type, durationSeconds);
        if (!type.hasFx()) {
            if (cfg.broadcastStart()) {
                Bukkit.broadcastMessage("§bWeather §f" + type.configKey()
                        + " §bin §f" + world.getName() + "§b.");
            }
            return true;
        }

        Location anchor = focus != null ? focus.clone() : defaultAnchor(world);
        if (type == DisasterType.VOLCANO) {
            Location siteFocus = plugin.volcanoSites().resolveVolcanoFocus(world, anchor);
            if (siteFocus != null) {
                anchor = siteFocus.clone();
            }
        }
        Active effect = new Active(type, System.currentTimeMillis() + durationSeconds * 1000L, anchor);
        active.put(world.getUID(), effect);

        long period = cfg.periodTicks(type, defaultPeriod(type));
        long durationTicks = Math.max(20L, (long) durationSeconds * 20L);
        effect.task = YapSched.globalTimer(plugin, () -> tick(world.getUID()), period, period);
        effect.endTask = YapSched.globalLater(plugin, () -> {
            if (active.get(world.getUID()) == effect) {
                stop(world.getUID(), true);
            }
        }, durationTicks);

        if (cfg.broadcastStart()) {
            Bukkit.broadcastMessage("§cDisaster §f" + type.configKey()
                    + " §cstarted in §f" + world.getName()
                    + " §c(" + durationSeconds + "s)§c.");
        }
        return true;
    }

    private static long defaultPeriod(DisasterType type) {
        return switch (type) {
            case THUNDER -> 35L;
            case HURRICANE -> 12L;
            case TORNADO -> 4L;
            case EARTHQUAKE -> 8L;
            case VOLCANO -> 10L;
            case BLIZZARD -> 8L;
            case DROUGHT -> 20L;
            case METEOR -> 14L;
            case TSUNAMI -> 8L;
            default -> 40L;
        };
    }

    private static Location defaultAnchor(World world) {
        for (Player player : world.getPlayers()) {
            return player.getLocation().clone();
        }
        return world.getSpawnLocation().clone();
    }

    private void tick(UUID worldId) {
        Active effect = active.get(worldId);
        if (effect == null) {
            return;
        }
        if (System.currentTimeMillis() > effect.endsAtMs) {
            stop(worldId, true);
            return;
        }
        World world = Bukkit.getWorld(worldId);
        if (world == null) {
            stop(worldId, false);
            return;
        }
        switch (effect.type) {
            case THUNDER -> tickThunder(world);
            case HURRICANE -> {
                tickThunder(world);
                tickWind(world);
            }
            case TORNADO -> tickTornado(world, effect);
            case EARTHQUAKE -> tickEarthquake(world);
            case VOLCANO -> tickVolcano(world, effect);
            case BLIZZARD -> tickBlizzard(world, effect);
            case DROUGHT -> tickDrought(world, effect);
            case METEOR -> tickMeteor(world, effect);
            case TSUNAMI -> tickTsunami(world, effect);
            default -> {
            }
        }
    }

    private void tickThunder(World world) {
        DisastersConfig cfg = plugin.config();
        boolean wantReal = cfg.realLightning();
        for (Player player : world.getPlayers()) {
            if (random.nextFloat() > 0.55f) {
                continue;
            }
            Location base = player.getLocation();
            Location strike = base.clone().add(
                    (random.nextDouble() * 48.0) - 24.0,
                    0,
                    (random.nextDouble() * 48.0) - 24.0);
            YapSched.region(plugin, strike, () -> {
                strike.setY(world.getHighestBlockYAt(strike) + 1);
                boolean real = wantReal
                        && random.nextFloat() < 0.35f
                        && LandProtection.canSystemIgnite(strike, cfg);
                if (real) {
                    world.strikeLightning(strike);
                } else {
                    world.strikeLightningEffect(strike);
                }
                world.playSound(strike, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 4.0f, 0.9f);
            });
        }
    }

    private void tickWind(World world) {
        for (Player player : world.getPlayers()) {
            YapSched.entity(plugin, player, () -> {
                if (!player.isOnline() || player.getWorld() != world) {
                    return;
                }
                Vector wind = new Vector(
                        (random.nextDouble() * 0.55) - 0.1,
                        0.02,
                        (random.nextDouble() * 0.55) - 0.1);
                player.setVelocity(player.getVelocity().add(wind));
                world.spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1.2, 0),
                        8, 0.6, 0.4, 0.6, 0.02);
                if (random.nextFloat() < 0.2f) {
                    player.playSound(player.getLocation(), Sound.ITEM_ELYTRA_FLYING, 0.35f, 0.6f);
                }
            });
        }
    }

    private void tickTornado(World world, Active effect) {
        Location center = effect.anchor.clone();
        YapSched.region(plugin, center, () -> {
            double t = (System.currentTimeMillis() % 4000L) / 4000.0 * Math.PI * 2.0;
            for (int i = 0; i < 18; i++) {
                double ang = t + i * (Math.PI * 2.0 / 18.0);
                double radius = 1.2 + (i % 6) * 0.55;
                double y = (i * 0.45) % 8.0;
                Location p = center.clone().add(Math.cos(ang) * radius, y, Math.sin(ang) * radius);
                world.spawnParticle(Particle.CLOUD, p, 2, 0.05, 0.05, 0.05, 0.0);
                world.spawnParticle(Particle.SMOKE, p, 1, 0.02, 0.02, 0.02, 0.0);
            }
            world.playSound(center, Sound.ENTITY_BREEZE_IDLE_GROUND, 0.8f, 0.5f);
            // Snapshot on this region, then mutate each target on its owning entity scheduler.
            for (Entity entity : List.copyOf(world.getNearbyEntities(center, 10.0, 12.0, 10.0))) {
                if (!entity.isValid()) {
                    continue;
                }
                Location anchor = center.clone();
                YapSched.entity(plugin, entity, () -> {
                    if (!entity.isValid() || entity.getWorld() != world) {
                        return;
                    }
                    if (entity instanceof Player player && player.getAllowFlight() && player.isFlying()) {
                        return;
                    }
                    Vector pull = anchor.toVector().subtract(entity.getLocation().toVector());
                    double dist = Math.max(0.6, pull.length());
                    pull.normalize().multiply(0.28 / dist).setY(0.18);
                    entity.setVelocity(entity.getVelocity().multiply(0.65).add(pull));
                });
            }
        });
    }

    private void tickEarthquake(World world) {
        for (Player player : world.getPlayers()) {
            YapSched.entity(plugin, player, () -> {
                if (!player.isOnline() || player.getWorld() != world) {
                    return;
                }
                Vector shake = new Vector(
                        (random.nextDouble() - 0.5) * 0.35,
                        0.05,
                        (random.nextDouble() - 0.5) * 0.35);
                player.setVelocity(player.getVelocity().add(shake));
                Location feet = player.getLocation();
                world.spawnParticle(Particle.BLOCK, feet, 12, 0.4, 0.1, 0.4, 0.02,
                        Material.DIRT.createBlockData());
                if (random.nextFloat() < 0.35f) {
                    player.playSound(feet, Sound.ENTITY_GENERIC_EXPLODE, 0.25f, 0.4f);
                }
            });
        }
    }

    private void tickVolcano(World world, Active effect) {
        Location mouth = effect.anchor.clone();
        DisastersConfig cfg = plugin.config();
        long lavaTicks = cfg.volcanoLavaTicks();
        YapSched.region(plugin, mouth, () -> {
            int surface = world.getHighestBlockYAt(mouth);
            int configured = mouth.getBlockY();
            // Soft sites keep crater height when close to surface; otherwise snap up.
            int baseY = Math.abs(configured - surface) <= 16
                    ? Math.max(configured, surface)
                    : surface;
            mouth.setY(baseY);
            Location spout = mouth.clone().add(0.5, 1.2, 0.5);
            world.spawnParticle(Particle.LAVA, spout, 18, 0.5, 0.8, 0.5, 0.02);
            world.spawnParticle(Particle.LARGE_SMOKE, mouth.clone().add(0.5, 2.5, 0.5), 10, 0.7, 1.2, 0.7, 0.01);
            world.spawnParticle(Particle.ASH, mouth.clone().add(0.5, 3.0, 0.5), 20, 1.5, 1.0, 1.5, 0.0);
            world.playSound(mouth, Sound.BLOCK_LAVA_POP, 1.2f, 0.7f);
            world.playSound(mouth, Sound.ENTITY_BLAZE_SHOOT, 0.4f, 0.5f);
            if (random.nextFloat() >= 0.25f) {
                return;
            }
            Block above = world.getBlockAt(mouth.getBlockX(), baseY + 1, mouth.getBlockZ());
            Location lavaLoc = above.getLocation().clone();
            if (!LandProtection.canSystemModify(lavaLoc, cfg) || !above.getType().isAir()) {
                return;
            }
            if (effect.undoCount() >= MAX_UNDO_TASKS) {
                return;
            }
            above.setType(Material.LAVA, false);
            if (!scheduleBlockUndo(effect, lavaLoc, lavaTicks, () -> {
                Block b = lavaLoc.getBlock();
                if (b.getType() == Material.LAVA) {
                    b.setType(Material.AIR, false);
                }
            })) {
                above.setType(Material.AIR, false);
            }
        });
    }

    private void tickBlizzard(World world, Active effect) {
        DisastersConfig cfg = plugin.config();
        long snowTicks = cfg.blizzardSnowTicks();
        for (Player player : world.getPlayers()) {
            YapSched.entity(plugin, player, () -> {
                if (!player.isOnline() || player.getWorld() != world) {
                    return;
                }
                Location loc = player.getLocation();
                world.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 2.2, 0),
                        40, 3.5, 2.0, 3.5, 0.01);
                world.spawnParticle(Particle.WHITE_ASH, loc.clone().add(0, 1.5, 0),
                        18, 2.0, 1.2, 2.0, 0.0);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, true, false, true));
                if (random.nextFloat() < 0.25f) {
                    player.playSound(loc, Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.4f, 1.6f);
                }
                if (random.nextFloat() >= 0.18f) {
                    return;
                }
                Location snowAt = loc.clone().add(
                        (random.nextDouble() * 10.0) - 5.0,
                        0,
                        (random.nextDouble() * 10.0) - 5.0);
                YapSched.region(plugin, snowAt, () -> {
                    int y = world.getHighestBlockYAt(snowAt);
                    Block surface = world.getBlockAt(snowAt.getBlockX(), y, snowAt.getBlockZ());
                    Block above = surface.getRelative(0, 1, 0);
                    Location place = above.getLocation();
                    if (!LandProtection.canSystemModify(place, cfg) || !above.getType().isAir()) {
                        return;
                    }
                    Material ground = surface.getType();
                    if (!ground.isSolid() || ground == Material.SNOW || ground == Material.SNOW_BLOCK) {
                        return;
                    }
                    above.setType(Material.SNOW, false);
                    if (!scheduleBlockUndo(effect, place, snowTicks, () -> {
                        Block b = place.getBlock();
                        if (b.getType() == Material.SNOW) {
                            b.setType(Material.AIR, false);
                        }
                    })) {
                        above.setType(Material.AIR, false);
                    }
                });
            });
        }
    }

    private void tickDrought(World world, Active effect) {
        DisastersConfig cfg = plugin.config();
        long dryTicks = cfg.droughtDryTicks();
        Location focus = effect.anchor.clone();
        YapSched.region(plugin, focus, () -> {
            world.spawnParticle(Particle.CRIT, focus.clone().add(0.5, 1.5, 0.5),
                    14, 2.5, 0.6, 2.5, 0.02);
            world.spawnParticle(Particle.SMOKE, focus.clone().add(0.5, 0.8, 0.5),
                    8, 1.8, 0.3, 1.8, 0.01);
            world.playSound(focus, Sound.BLOCK_FIRE_AMBIENT, 0.35f, 1.8f);
            if (random.nextFloat() >= 0.35f) {
                return;
            }
            Location dryAt = focus.clone().add(
                    (random.nextDouble() * 16.0) - 8.0,
                    0,
                    (random.nextDouble() * 16.0) - 8.0);
            int y = world.getHighestBlockYAt(dryAt);
            Block surface = world.getBlockAt(dryAt.getBlockX(), y, dryAt.getBlockZ());
            Material type = surface.getType();
            if (type != Material.WATER && type != Material.SHORT_GRASS && type != Material.TALL_GRASS
                    && type != Material.FERN && type != Material.LARGE_FERN) {
                return;
            }
            Location place = surface.getLocation();
            if (!LandProtection.canSystemModify(place, cfg)) {
                return;
            }
            Material previous = type;
            surface.setType(Material.AIR, false);
            if (!scheduleBlockUndo(effect, place, dryTicks, () -> {
                Block b = place.getBlock();
                if (b.getType().isAir()) {
                    b.setType(previous, false);
                }
            })) {
                surface.setType(previous, false);
            }
        });
        for (Player player : world.getPlayers()) {
            YapSched.entity(plugin, player, () -> {
                if (!player.isOnline() || player.getWorld() != world) {
                    return;
                }
                world.spawnParticle(Particle.DUST, player.getLocation().add(0, 1.0, 0),
                        6, 0.5, 0.4, 0.5, 0.0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(180, 140, 80), 1.1f));
            });
        }
    }

    private void tickMeteor(World world, Active effect) {
        DisastersConfig cfg = plugin.config();
        long fireTicks = cfg.meteorFireTicks();
        Location focus = effect.anchor.clone();
        YapSched.region(plugin, focus, () -> {
            double ox = (random.nextDouble() * 28.0) - 14.0;
            double oz = (random.nextDouble() * 28.0) - 14.0;
            Location impact = focus.clone().add(ox, 0, oz);
            int groundY = world.getHighestBlockYAt(impact);
            impact.setY(groundY + 1);
            Location start = impact.clone().add(0, 28, 0);
            for (int i = 0; i < 10; i++) {
                double t = i / 9.0;
                Location trail = start.clone().add(0, -(start.getY() - impact.getY()) * t, 0);
                world.spawnParticle(Particle.FLAME, trail, 3, 0.08, 0.08, 0.08, 0.01);
                world.spawnParticle(Particle.SMOKE, trail, 2, 0.05, 0.05, 0.05, 0.0);
            }
            world.spawnParticle(Particle.EXPLOSION, impact, 2, 0.2, 0.2, 0.2, 0.0);
            world.spawnParticle(Particle.LAVA, impact, 8, 0.4, 0.3, 0.4, 0.02);
            world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.7f);
            world.playSound(impact, Sound.ENTITY_BLAZE_SHOOT, 0.5f, 0.4f);
            if (random.nextFloat() >= 0.4f) {
                return;
            }
            Block hit = impact.getBlock();
            if (!LandProtection.canSystemIgnite(hit.getLocation(), cfg)) {
                return;
            }
            if (hit.getType().isAir()) {
                hit.setType(Material.FIRE, false);
                Location fireLoc = hit.getLocation().clone();
                if (!scheduleBlockUndo(effect, fireLoc, fireTicks, () -> {
                    Block b = fireLoc.getBlock();
                    if (b.getType() == Material.FIRE) {
                        b.setType(Material.AIR, false);
                    }
                })) {
                    hit.setType(Material.AIR, false);
                }
            }
        });
    }

    private void tickTsunami(World world, Active effect) {
        DisastersConfig cfg = plugin.config();
        long waterTicks = cfg.tsunamiWaterTicks();
        int radius = cfg.tsunamiWaveRadius();
        int height = cfg.tsunamiFloodHeight();
        Location focus = effect.anchor.clone();
        double pulse = ((System.currentTimeMillis() / 250L) % (radius + 4)) * 1.0;
        double angle = (System.currentTimeMillis() % 8000L) / 8000.0 * Math.PI * 2.0;
        double front = 3.0 + (pulse % Math.max(4, radius));

        YapSched.region(plugin, focus, () -> {
            world.playSound(focus, Sound.ENTITY_GENERIC_SPLASH, 1.1f, 0.55f);
            world.playSound(focus, Sound.AMBIENT_UNDERWATER_LOOP, 0.25f, 0.8f);
            for (Entity entity : List.copyOf(world.getNearbyEntities(focus, radius + 4.0, 8.0, radius + 4.0))) {
                if (!(entity instanceof Player) || !entity.isValid()) {
                    continue;
                }
                Location anchor = focus.clone();
                YapSched.entity(plugin, entity, () -> {
                    if (!(entity instanceof Player player) || !player.isOnline() || player.getWorld() != world) {
                        return;
                    }
                    if (player.getAllowFlight() && player.isFlying()) {
                        return;
                    }
                    Vector push = player.getLocation().toVector().subtract(anchor.toVector());
                    if (push.lengthSquared() < 0.01) {
                        push = new Vector(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
                    }
                    push.normalize().multiply(0.35).setY(0.22);
                    player.setVelocity(player.getVelocity().multiply(0.55).add(push));
                });
            }
        });

        // Crest FX / flood blocks hop to their own region (YaP-Folia shard-safe).
        for (int i = 0; i < 16; i++) {
            double a = angle + (i / 16.0) * Math.PI * 0.7 - Math.PI * 0.35;
            Location crest = focus.clone().add(Math.cos(a) * front, 0, Math.sin(a) * front);
            YapSched.region(plugin, crest, () -> {
                int y = world.getHighestBlockYAt(crest);
                crest.setY(y + 1);
                world.spawnParticle(Particle.SPLASH, crest, 18, 0.8, 0.4, 0.8, 0.02);
                world.spawnParticle(Particle.BUBBLE_COLUMN_UP, crest, 8, 0.5, 0.6, 0.5, 0.01);
                world.spawnParticle(Particle.CLOUD, crest.clone().add(0, 1.2, 0), 4, 0.6, 0.3, 0.6, 0.01);
            });
        }

        if (random.nextFloat() < 0.4f) {
            double a = angle + (random.nextDouble() - 0.5) * 0.8;
            Location floodAt = focus.clone().add(Math.cos(a) * front, 0, Math.sin(a) * front);
            YapSched.region(plugin, floodAt, () -> {
                int baseY = world.getHighestBlockYAt(floodAt);
                for (int dy = 1; dy <= height; dy++) {
                    Block block = world.getBlockAt(floodAt.getBlockX(), baseY + dy, floodAt.getBlockZ());
                    Location place = block.getLocation().clone();
                    if (!LandProtection.canSystemModify(place, cfg)) {
                        continue;
                    }
                    if (!block.getType().isAir() && block.getType() != Material.WATER) {
                        continue;
                    }
                    Material previous = block.getType();
                    block.setType(Material.WATER, false);
                    if (!scheduleBlockUndo(effect, place, waterTicks, () -> {
                        Block b = place.getBlock();
                        if (b.getType() == Material.WATER) {
                            b.setType(previous.isAir() ? Material.AIR : previous, false);
                        }
                    })) {
                        block.setType(previous, false);
                    }
                }
            });
        }
    }

    private static final int MAX_UNDO_TASKS = 256;

    /** Schedule a short-lived block restore; cancelled when the disaster stops. */
    private boolean scheduleBlockUndo(Active effect, Location loc, long delayTicks, Runnable regionWork) {
        if (effect == null || loc == null || regionWork == null) {
            return false;
        }
        if (effect.undoCount() >= MAX_UNDO_TASKS) {
            return false;
        }
        Location at = loc.clone();
        YapTask[] holder = new YapTask[1];
        holder[0] = YapSched.globalLater(plugin, () -> {
            YapSched.region(plugin, at, regionWork);
            effect.removeUndo(holder[0]);
        }, delayTicks);
        effect.addUndo(holder[0]);
        return true;
    }

    private static final class Active {
        final DisasterType type;
        final long endsAtMs;
        final Location anchor;
        YapTask task;
        YapTask endTask;
        private final List<YapTask> undos = new java.util.concurrent.CopyOnWriteArrayList<>();

        Active(DisasterType type, long endsAtMs, Location anchor) {
            this.type = type;
            this.endsAtMs = endsAtMs;
            this.anchor = anchor;
        }

        int undoCount() {
            return undos.size();
        }

        void addUndo(YapTask undo) {
            if (undo != null) {
                undos.add(undo);
            }
        }

        void removeUndo(YapTask undo) {
            if (undo != null) {
                undos.remove(undo);
            }
        }

        void cancelUndos() {
            for (YapTask undo : undos) {
                if (undo != null) {
                    undo.cancel();
                }
            }
            undos.clear();
        }
    }
}
