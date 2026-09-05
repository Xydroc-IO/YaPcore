package com.yapcore.dungeons.gen;

import com.yapcore.sched.YapSched;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Applies a planned layout into a Bukkit world (Folia region-safe chunks). */
public final class DungeonCarver {

    public static final String BOSS_PDC_KEY = "yap_dungeon_boss";
    public static final String MOB_PDC_KEY = "yap_dungeon_mob";

    private final JavaPlugin plugin;

    public DungeonCarver(JavaPlugin plugin) {
        this.plugin = plugin;
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
        Location entrance = new Location(world,
                layout.rooms().getFirst().x() + layout.rooms().getFirst().sizeX() / 2.0,
                layout.originY() + 1,
                layout.rooms().getFirst().z() + layout.rooms().getFirst().sizeZ() / 2.0);
        RoomGraphBuilder.Room bossRoom = layout.rooms().getLast();
        Location bossLoc = new Location(world,
                bossRoom.x() + bossRoom.sizeX() / 2.0,
                layout.originY() + 1,
                bossRoom.z() + bossRoom.sizeZ() / 2.0);
        List<Location> chests = new ArrayList<>();

        YapSched.global(plugin, () -> {
            try {
                progress.accept("Carving rooms…");
                for (RoomGraphBuilder.Room room : layout.rooms()) {
                    carveRoom(world, room, layout.originY(), theme);
                }
                progress.accept("Carving corridors…");
                for (RoomGraphBuilder.Corridor c : layout.corridors()) {
                    RoomGraphBuilder.Room a = layout.rooms().get(c.fromId());
                    RoomGraphBuilder.Room b = layout.rooms().get(c.toId());
                    carveCorridor(world, a, b, layout.originY(), theme);
                }
                progress.accept("Placing props…");
                for (RoomGraphBuilder.Room room : layout.rooms()) {
                    placeProps(world, room, layout.originY(), theme, diff, rng, runId, chests);
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

    private void carveRoom(World world, RoomGraphBuilder.Room room, int y, ThemeTable.Theme theme) {
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
                    if (yy == floor) {
                        b.setType(theme.floor(), false);
                    } else if (yy == ceil) {
                        b.setType(theme.wall(), false);
                    } else if (edge) {
                        b.setType(theme.wall(), false);
                    } else {
                        b.setType(Material.AIR, false);
                    }
                }
            }
        }
        // Accent pillars
        world.getBlockAt(x0 + 2, floor + 1, z0 + 2).setType(theme.accent(), false);
        world.getBlockAt(x1 - 3, floor + 1, z1 - 3).setType(theme.accent(), false);
        world.getBlockAt(x0 + room.sizeX() / 2, floor + 4, z0 + room.sizeZ() / 2).setType(theme.light(), false);
    }

    private void carveCorridor(World world, RoomGraphBuilder.Room a, RoomGraphBuilder.Room b, int y, ThemeTable.Theme theme) {
        int ax = a.x() + a.sizeX() / 2;
        int az = a.z() + a.sizeZ() / 2;
        int bx = b.x() + b.sizeX() / 2;
        int bz = b.z() + b.sizeZ() / 2;
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
                world.getBlockAt(x + dx, y, z + dz).setType(theme.floor(), false);
                world.getBlockAt(x + dx, y + 1, z + dz).setType(Material.AIR, false);
                world.getBlockAt(x + dx, y + 2, z + dz).setType(Material.AIR, false);
                world.getBlockAt(x + dx, y + 3, z + dz).setType(theme.wall(), false);
            }
        }
    }

    private void placeProps(
            World world, RoomGraphBuilder.Room room, int y, ThemeTable.Theme theme,
            DifficultyTable.LevelDiff diff, Random rng, String runId, List<Location> chests) {
        int cx = room.x() + room.sizeX() / 2;
        int cz = room.z() + room.sizeZ() / 2;
        if (room.kind() == RoomGraphBuilder.RoomKind.TREASURE || room.kind() == RoomGraphBuilder.RoomKind.BOSS) {
            Block chestBlock = world.getBlockAt(cx, y + 1, cz);
            chestBlock.setType(Material.CHEST, false);
            if (chestBlock.getState() instanceof Chest chest) {
                chest.getPersistentDataContainer().set(
                        new org.bukkit.NamespacedKey(plugin, "yap_dungeon_chest"),
                        PersistentDataType.STRING, runId);
                chest.update();
            }
            chests.add(chestBlock.getLocation());
        }
        if (room.kind() == RoomGraphBuilder.RoomKind.TRAP) {
            world.getBlockAt(cx, y, cz).setType(Material.MAGMA_BLOCK, false);
            world.getBlockAt(cx + 1, y, cz).setType(Material.MAGMA_BLOCK, false);
        }
        // Ore veins
        for (int i = 0; i < 3 + rng.nextInt(4); i++) {
            int ox = room.x() + 1 + rng.nextInt(Math.max(1, room.sizeX() - 2));
            int oz = room.z() + 1 + rng.nextInt(Math.max(1, room.sizeZ() - 2));
            world.getBlockAt(ox, y, oz).setType(theme.ore(), false);
        }
    }

    private void spawnMobs(
            World world, RoomGraphBuilder.Room room, int y, ThemeTable.Theme theme,
            DifficultyTable.LevelDiff diff, Random rng, String runId) {
        int count = diff.mobsPerRoom();
        for (int i = 0; i < count; i++) {
            EntityType type = theme.mobs().get(rng.nextInt(theme.mobs().size()));
            // Skip warden spam at low counts — still allowed by theme at high bands
            Location loc = new Location(world,
                    room.x() + 2 + rng.nextInt(Math.max(1, room.sizeX() - 4)),
                    y + 1,
                    room.z() + 2 + rng.nextInt(Math.max(1, room.sizeZ() - 4)));
            LivingEntity entity = (LivingEntity) world.spawnEntity(loc, type);
            scale(entity, diff, rng.nextDouble() < diff.eliteChance());
            tag(entity, MOB_PDC_KEY, runId);
        }
    }

    private void spawnBoss(World world, Location loc, ThemeTable.Theme theme, DifficultyTable.LevelDiff diff, String runId) {
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
