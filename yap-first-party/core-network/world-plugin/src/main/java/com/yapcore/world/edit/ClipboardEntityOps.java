package com.yapcore.world.edit;

import com.yapcore.sched.YapSched;
import com.yapcore.world.CuboidSelection;
import com.yapcore.world.schem.Schematic;
import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Entity/biome capture and paste helpers for {@link ClipboardService}. */
final class ClipboardEntityOps {

    private final JavaPlugin plugin;

    ClipboardEntityOps(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    CompletableFuture<Integer> applyBiomes(World world, List<ClipboardService.BiomeEntry> biomes,
                                           int ox, int oy, int oz) {
        CompletableFuture<Integer> done = new CompletableFuture<>();
        if (biomes.isEmpty()) {
            done.complete(0);
            return done;
        }
        ClipboardService.BiomeEntry first = biomes.get(0);
        YapSched.region(plugin, new Location(world, ox + first.dx(), oy + first.dy(), oz + first.dz()), () -> {
            int n = 0;
            for (ClipboardService.BiomeEntry b : biomes) {
                Biome biome = matchBiome(b.biome());
                if (biome == null) {
                    continue;
                }
                world.setBiome(ox + b.dx(), oy + b.dy(), oz + b.dz(), biome);
                n++;
            }
            done.complete(n);
        });
        return done;
    }

    CompletableFuture<Integer> spawnEntities(World world, List<Schematic.EntityEntry> entities,
                                             int ox, int oy, int oz) {
        CompletableFuture<Integer> done = new CompletableFuture<>();
        if (entities.isEmpty()) {
            done.complete(0);
            return done;
        }
        Schematic.EntityEntry first = entities.get(0);
        YapSched.region(plugin, new Location(world, ox + first.dx(), oy + first.dy(), oz + first.dz()), () -> {
            int n = 0;
            for (Schematic.EntityEntry e : entities) {
                EntityType type;
                try {
                    type = EntityType.valueOf(e.type());
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                if (!type.isSpawnable()) {
                    continue;
                }
                Location loc = new Location(world, ox + e.dx() + 0.5, oy + e.dy(), oz + e.dz() + 0.5,
                        e.yaw(), e.pitch());
                Entity spawned = world.spawnEntity(loc, type);
                if (spawned instanceof LivingEntity living && e.nbt() != null && e.nbt().startsWith("custom=")
                        && e.nbt().length() > 7) {
                    living.setCustomName(e.nbt().substring(7));
                    living.setCustomNameVisible(true);
                }
                n++;
            }
            done.complete(n);
        });
        return done;
    }

    static List<Schematic.EntityEntry> captureEntities(World world, CuboidSelection sel) {
        List<Schematic.EntityEntry> entities = new ArrayList<>();
        Location min = new Location(world, sel.minX(), sel.minY(), sel.minZ());
        Location max = new Location(world, sel.maxX() + 1, sel.maxY() + 1, sel.maxZ() + 1);
        Collection<Entity> nearby = world.getNearbyEntities(
                min.toVector().getMidpoint(max.toVector()).toLocation(world),
                (sel.maxX() - sel.minX()) / 2.0 + 1,
                (sel.maxY() - sel.minY()) / 2.0 + 1,
                (sel.maxZ() - sel.minZ()) / 2.0 + 1);
        for (Entity e : nearby) {
            if (e instanceof Player) {
                continue;
            }
            Location loc = e.getLocation();
            if (loc.getBlockX() < sel.minX() || loc.getBlockX() > sel.maxX()
                    || loc.getBlockY() < sel.minY() || loc.getBlockY() > sel.maxY()
                    || loc.getBlockZ() < sel.minZ() || loc.getBlockZ() > sel.maxZ()) {
                continue;
            }
            String nbt = e instanceof LivingEntity living
                    ? "custom=" + (living.getCustomName() == null ? "" : living.getCustomName())
                    : "";
            entities.add(new Schematic.EntityEntry(
                    loc.getBlockX() - sel.minX(),
                    loc.getBlockY() - sel.minY(),
                    loc.getBlockZ() - sel.minZ(),
                    e.getType().name(),
                    loc.getYaw(),
                    loc.getPitch(),
                    nbt));
        }
        return entities;
    }

    static void removeEntityAt(World world, int x, int y, int z, String typeName) {
        EntityType type;
        try {
            type = EntityType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            return;
        }
        for (Entity e : world.getNearbyEntities(new Location(world, x + 0.5, y, z + 0.5), 0.6, 0.6, 0.6)) {
            if (e instanceof Player) {
                continue;
            }
            if (e.getType() == type) {
                e.remove();
                return;
            }
        }
    }

    static Biome matchBiome(String name) {
        if (name == null) {
            return null;
        }
        String key = name.toLowerCase(Locale.ROOT).replace("minecraft:", "");
        for (Biome b : Registry.BIOME) {
            if (b.getKey().getKey().equalsIgnoreCase(key)) {
                return b;
            }
        }
        return null;
    }
}
