package com.yapcore.world.schem;

import com.yapcore.world.CuboidSelection;
import com.yapcore.world.util.BlockCodec;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class SchematicIO {

    private SchematicIO() {
    }

    public static Schematic capture(CuboidSelection selection, World world) {
        List<Schematic.BlockEntry> blocks = new ArrayList<>();
        int anchorX = selection.minX();
        int anchorY = selection.minY();
        int anchorZ = selection.minZ();
        for (int x = selection.minX(); x <= selection.maxX(); x++) {
            for (int y = selection.minY(); y <= selection.maxY(); y++) {
                for (int z = selection.minZ(); z <= selection.maxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType().isAir()) {
                        continue;
                    }
                    blocks.add(new Schematic.BlockEntry(
                            x - anchorX, y - anchorY, z - anchorZ, BlockCodec.encode(block)));
                }
            }
        }
        List<Schematic.EntityEntry> entities = new ArrayList<>();
        Location min = new Location(world, selection.minX(), selection.minY(), selection.minZ());
        Location max = new Location(world, selection.maxX() + 1, selection.maxY() + 1, selection.maxZ() + 1);
        Collection<Entity> nearby = world.getNearbyEntities(
                min.toVector().getMidpoint(max.toVector()).toLocation(world),
                (selection.maxX() - selection.minX()) / 2.0 + 1,
                (selection.maxY() - selection.minY()) / 2.0 + 1,
                (selection.maxZ() - selection.minZ()) / 2.0 + 1);
        for (Entity e : nearby) {
            if (e instanceof Player) {
                continue;
            }
            Location loc = e.getLocation();
            if (loc.getBlockX() < selection.minX() || loc.getBlockX() > selection.maxX()
                    || loc.getBlockY() < selection.minY() || loc.getBlockY() > selection.maxY()
                    || loc.getBlockZ() < selection.minZ() || loc.getBlockZ() > selection.maxZ()) {
                continue;
            }
            String nbt = e instanceof LivingEntity living ? "custom=" + (living.getCustomName() == null ? "" : living.getCustomName()) : "";
            entities.add(new Schematic.EntityEntry(
                    loc.getBlockX() - anchorX,
                    loc.getBlockY() - anchorY,
                    loc.getBlockZ() - anchorZ,
                    e.getType().name(),
                    loc.getYaw(),
                    loc.getPitch(),
                    nbt));
        }
        return new Schematic(world.getName(), anchorX, anchorY, anchorZ, blocks, entities);
    }

    public static void save(Path file, Schematic schematic) throws IOException {
        Files.createDirectories(file.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("# yap-schem v2");
            w.newLine();
            w.write("world=" + schematic.world());
            w.newLine();
            w.write("anchor=" + schematic.anchorX() + "," + schematic.anchorY() + "," + schematic.anchorZ());
            w.newLine();
            for (Schematic.BlockEntry entry : schematic.blocks()) {
                w.write("block=" + entry.dx() + "," + entry.dy() + "," + entry.dz() + "," + entry.encoded());
                w.newLine();
            }
            for (Schematic.EntityEntry e : schematic.entities()) {
                w.write("entity=" + e.dx() + "," + e.dy() + "," + e.dz() + ","
                        + e.type() + "," + e.yaw() + "," + e.pitch() + ","
                        + (e.nbt() == null ? "" : e.nbt().replace(',', ';')));
                w.newLine();
            }
        }
    }

    public static Schematic load(Path file) throws IOException {
        String world = "world";
        int ax = 0;
        int ay = 0;
        int az = 0;
        List<Schematic.BlockEntry> blocks = new ArrayList<>();
        List<Schematic.EntityEntry> entities = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("world=")) {
                    world = line.substring("world=".length());
                } else if (line.startsWith("anchor=")) {
                    String[] parts = line.substring("anchor=".length()).split(",");
                    ax = Integer.parseInt(parts[0]);
                    ay = Integer.parseInt(parts[1]);
                    az = Integer.parseInt(parts[2]);
                } else if (line.startsWith("block=")) {
                    String[] parts = line.substring("block=".length()).split(",", 4);
                    blocks.add(new Schematic.BlockEntry(
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2]),
                            parts[3]));
                } else if (line.startsWith("entity=")) {
                    String[] parts = line.substring("entity=".length()).split(",", 7);
                    if (parts.length >= 6) {
                        entities.add(new Schematic.EntityEntry(
                                Integer.parseInt(parts[0]),
                                Integer.parseInt(parts[1]),
                                Integer.parseInt(parts[2]),
                                parts[3],
                                Float.parseFloat(parts[4]),
                                Float.parseFloat(parts[5]),
                                parts.length >= 7 ? parts[6].replace(';', ',') : ""));
                    }
                }
            }
        }
        return new Schematic(world, ax, ay, az, blocks, entities);
    }

    public static void spawnEntities(Schematic schematic, World world, int originX, int originY, int originZ) {
        for (Schematic.EntityEntry e : schematic.entities()) {
            EntityType type;
            try {
                type = EntityType.valueOf(e.type());
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (!type.isSpawnable()) {
                continue;
            }
            Location loc = new Location(world, originX + e.dx() + 0.5, originY + e.dy(), originZ + e.dz() + 0.5,
                    e.yaw(), e.pitch());
            Entity spawned = world.spawnEntity(loc, type);
            if (spawned instanceof LivingEntity living && e.nbt() != null && e.nbt().startsWith("custom=")
                    && e.nbt().length() > 7) {
                living.setCustomName(e.nbt().substring(7));
                living.setCustomNameVisible(true);
            }
        }
    }
}
