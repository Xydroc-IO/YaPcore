package com.yapcore.dungeons.gen;

import com.yapcore.sched.YapSched;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Applies a planned layout into a Bukkit world. */
public final class DungeonCarver {

    public static final String BOSS_PDC_KEY = "yap_dungeon_boss";
    public static final String MOB_PDC_KEY = "yap_dungeon_mob";

    private final JavaPlugin plugin;
    private final RoomTemplates templates;

    public DungeonCarver(JavaPlugin plugin) {
        this.plugin = plugin;
        this.templates = new RoomTemplates(plugin);
    }

    public record BuildResult(Location entrance, Location bossArena, List<Location> chestLocations) {
    }

    public CompletableFuture<BuildResult> carve(
            World world,
            RoomGraphBuilder.Layout layout,
            ThemeTable.Theme theme,
            DifficultyTable.LevelDiff diff,
            long seed,
            String runId,
            Consumer<String> progress) {
        CompletableFuture<BuildResult> future = new CompletableFuture<>();
        Random rng = new Random(seed ^ 0xD00DL);
        RoomGraphBuilder.Room entranceRoom = layout.rooms().getFirst();
        Location entrance = new Location(world,
                entranceRoom.centerX() + 0.5,
                layout.originY() + 1,
                entranceRoom.centerZ() + 0.5);
        RoomGraphBuilder.Room bossRoom = layout.rooms().getLast();
        Location bossLoc = new Location(world,
                bossRoom.centerX() + 0.5,
                layout.originY() + 3,
                bossRoom.centerZ() + 0.5);
        List<Location> chests = new ArrayList<>();
        Map<Integer, RoomGraphBuilder.Room> byId = new HashMap<>();
        for (RoomGraphBuilder.Room r : layout.rooms()) {
            byId.put(r.id(), r);
        }

        YapSched.global(plugin, () -> {
            try {
                progress.accept("Laying foundation…");
                layFoundation(world, layout, theme);
                progress.accept("Carving rooms…");
                for (RoomGraphBuilder.Room room : layout.rooms()) {
                    carveRoomShell(world, room, layout.originY(), theme);
                }
                progress.accept("Connecting corridors…");
                for (RoomGraphBuilder.Corridor c : layout.corridors()) {
                    RoomGraphBuilder.Room a = byId.get(c.fromId());
                    RoomGraphBuilder.Room b = byId.get(c.toId());
                    if (a == null || b == null) {
                        continue;
                    }
                    carveCorridor(world, a, b, layout.originY(), theme);
                    templates.carveDoorway(world, a, layout.originY(), b.centerX(), b.centerZ(), theme);
                    templates.carveDoorway(world, b, layout.originY(), a.centerX(), a.centerZ(), theme);
                }
                progress.accept("Decorating rooms…");
                for (RoomGraphBuilder.Room room : layout.rooms()) {
                    templates.decorate(world, room, layout.originY(), theme, rng, runId, chests);
                }
                world.setSpawnLocation(entrance);
                progress.accept("Spawning hostiles…");
                for (RoomGraphBuilder.Room room : layout.rooms()) {
                    if (room.kind() == RoomGraphBuilder.RoomKind.ENTRANCE) {
                        continue;
                    }
                    if (room.kind() == RoomGraphBuilder.RoomKind.BOSS) {
                        spawnBoss(world, bossLoc, theme, diff, runId);
                    } else if (room.kind() == RoomGraphBuilder.RoomKind.COMBAT
                            || room.kind() == RoomGraphBuilder.RoomKind.TRAP) {
                        spawnMobs(world, room, layout.originY(), theme, diff, rng, runId);
                    }
                }
                future.complete(new BuildResult(entrance, bossLoc, List.copyOf(chests)));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private void layFoundation(World world, RoomGraphBuilder.Layout layout, ThemeTable.Theme theme) {
        int y = layout.originY();
        int minX = layout.minX() - 2;
        int minZ = layout.minZ() - 2;
        int maxX = layout.maxX() + 2;
        int maxZ = layout.maxZ() + 2;
        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                world.getBlockAt(x, y - 1, z).setType(Material.BEDROCK, false);
                // Thin gravel rim so the complex sits on a pad instead of void
                if (x == minX || z == minZ || x == maxX - 1 || z == maxZ - 1) {
                    world.getBlockAt(x, y, z).setType(theme.wall(), false);
                }
            }
        }
    }

    private void carveRoomShell(World world, RoomGraphBuilder.Room room, int y, ThemeTable.Theme theme) {
        int x0 = room.x();
        int z0 = room.z();
        int x1 = x0 + room.sizeX();
        int z1 = z0 + room.sizeZ();
        int floor = y;
        int ceil = y + 5;
        for (int x = x0; x < x1; x++) {
            for (int z = z0; z < z1; z++) {
                for (int yy = floor; yy <= ceil; yy++) {
                    Block b = world.getBlockAt(x, yy, z);
                    boolean edge = x == x0 || z == z0 || x == x1 - 1 || z == z1 - 1;
                    boolean corner = (x == x0 || x == x1 - 1) && (z == z0 || z == z1 - 1);
                    if (yy == floor) {
                        b.setType(theme.floor(), false);
                    } else if (yy == ceil) {
                        b.setType(corner ? theme.accent() : theme.wall(), false);
                    } else if (edge) {
                        b.setType(corner ? theme.accent() : theme.wall(), false);
                    } else {
                        b.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private void carveCorridor(
            World world, RoomGraphBuilder.Room a, RoomGraphBuilder.Room b, int y, ThemeTable.Theme theme) {
        int ax = a.centerX();
        int az = a.centerZ();
        int bx = b.centerX();
        int bz = b.centerZ();
        // Orthogonal hallway: horizontal then vertical (deterministic order by seedless compare)
        int x = ax;
        int z = az;
        while (x != bx) {
            digHall(world, x, y, z, theme);
            x += Integer.compare(bx, x);
        }
        while (z != bz) {
            digHall(world, x, y, z, theme);
            z += Integer.compare(bz, z);
        }
    }

    private void digHall(World world, int x, int y, int z, ThemeTable.Theme theme) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(x + dx, y - 1, z + dz).setType(Material.BEDROCK, false);
                world.getBlockAt(x + dx, y, z + dz).setType(theme.floor(), false);
                world.getBlockAt(x + dx, y + 1, z + dz).setType(Material.AIR, false);
                world.getBlockAt(x + dx, y + 2, z + dz).setType(Material.AIR, false);
                world.getBlockAt(x + dx, y + 3, z + dz).setType(Material.AIR, false);
                // Walls only on corridor edges
                if (Math.abs(dx) == 1 || Math.abs(dz) == 1) {
                    world.getBlockAt(x + dx, y + 1, z + dz).setType(theme.wall(), false);
                    world.getBlockAt(x + dx, y + 2, z + dz).setType(theme.wall(), false);
                }
                world.getBlockAt(x + dx, y + 4, z + dz).setType(theme.wall(), false);
            }
        }
        // Re-clear center walkway 1-wide (after edge walls)
        world.getBlockAt(x, y + 1, z).setType(Material.AIR, false);
        world.getBlockAt(x, y + 2, z).setType(Material.AIR, false);
        world.getBlockAt(x, y + 3, z).setType(Material.AIR, false);
        // Occasional ceiling light
        if (((x + z) & 7) == 0) {
            world.getBlockAt(x, y + 3, z).setType(theme.light(), false);
        }
    }

    private void spawnMobs(
            World world, RoomGraphBuilder.Room room, int y, ThemeTable.Theme theme,
            DifficultyTable.LevelDiff diff, Random rng, String runId) {
        int count = diff.mobsPerRoom();
        for (int i = 0; i < count; i++) {
            EntityType type = theme.mobs().get(rng.nextInt(theme.mobs().size()));
            Location loc = new Location(world,
                    room.x() + 3 + rng.nextInt(Math.max(1, room.sizeX() - 6)) + 0.5,
                    y + 1,
                    room.z() + 3 + rng.nextInt(Math.max(1, room.sizeZ() - 6)) + 0.5);
            LivingEntity entity = (LivingEntity) world.spawnEntity(loc, type);
            scale(entity, diff, rng.nextDouble() < diff.eliteChance());
            tag(entity, MOB_PDC_KEY, runId);
        }
    }

    private void spawnBoss(
            World world, Location loc, ThemeTable.Theme theme, DifficultyTable.LevelDiff diff, String runId) {
        LivingEntity boss = (LivingEntity) world.spawnEntity(loc, theme.boss());
        boss.customName(net.kyori.adventure.text.Component.text("Dungeon Boss"));
        boss.setCustomNameVisible(true);
        var hp = boss.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(diff.bossMaxHealth());
            boss.setHealth(diff.bossMaxHealth());
        }
        var dmg = boss.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmg != null) {
            dmg.setBaseValue(dmg.getBaseValue() * diff.damageMult() * 1.5);
        }
        tag(boss, BOSS_PDC_KEY, runId);
        tag(boss, MOB_PDC_KEY, runId);
    }

    private void scale(LivingEntity entity, DifficultyTable.LevelDiff diff, boolean elite) {
        var hp = entity.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            double mult = diff.hpMult() * (elite ? 1.75 : 1.0);
            hp.setBaseValue(hp.getBaseValue() * mult);
            entity.setHealth(hp.getBaseValue());
        }
        var dmg = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmg != null) {
            dmg.setBaseValue(dmg.getBaseValue() * diff.damageMult() * (elite ? 1.4 : 1.0));
        }
        if (elite) {
            entity.customName(net.kyori.adventure.text.Component.text("Elite"));
            entity.setCustomNameVisible(true);
        }
    }

    private void tag(LivingEntity entity, String key, String runId) {
        entity.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, key), PersistentDataType.STRING, runId);
    }

    public void notifyPlayers(List<Player> players, String msg) {
        for (Player p : players) {
            YapSched.entity(plugin, p, () -> p.sendMessage("§6[Dungeon] §7" + msg));
        }
    }
}
