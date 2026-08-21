package com.yapcore.phase3plugin;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Phase 3 bridge plugin — snapshots entities on the main thread, then fans
 * interior work onto YapEngine cores 3–6 under DLM leases; border chunks go
 * through ChunkSyncLayer handoffs (T7/T8).
 */
public final class YapSpatialTickPlugin extends JavaPlugin {

    private Object coordinator;
    private Method runParallelTick;
    private Method runLeased;
    private Method submitBorderHandoff;
    private Method isBorderChunk;
    private Method chunkKey;
    private Class<?> quadrantClass;
    private Object[] quadrants;
    private Object nmsDriver;
    private Method tickEntities;
    private boolean nmsEnabled;
    private long ticks;

    @Override
    public void onEnable() {
        try {
            ClassLoader app = ClassLoader.getSystemClassLoader();
            Class<?> holder = Class.forName("com.yapcore.paper.phase3.PaperTickBridgeHolder", true, app);
            coordinator = holder.getField("COORDINATOR").get(null);
            if (coordinator == null) {
                getLogger().severe("PaperTickBridgeHolder.COORDINATOR is null — is Phase 3 host running?");
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
            Object nmsFlag = holder.getField("NMS_TICK_ENABLED").get(null);
            nmsEnabled = nmsFlag instanceof Boolean b && b;

            quadrantClass = Class.forName("com.yaplabs.yapengine.core.spatial.SpatialQuadrant", true, app);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object[] qs = new Object[]{
                    Enum.valueOf((Class) quadrantClass, "NW"),
                    Enum.valueOf((Class) quadrantClass, "NE"),
                    Enum.valueOf((Class) quadrantClass, "SW"),
                    Enum.valueOf((Class) quadrantClass, "SE")
            };
            quadrants = qs;
            Class<?> coordCl = coordinator.getClass();
            runParallelTick = coordCl.getMethod("runParallelTick", Map.class);
            runLeased = coordCl.getMethod("runLeased", String.class, Runnable.class);
            submitBorderHandoff = coordCl.getMethod("submitBorderHandoff",
                    String.class, String.class, quadrantClass, quadrantClass, Runnable.class);
            isBorderChunk = coordCl.getMethod("isBorderChunk", int.class, int.class);
            chunkKey = coordCl.getMethod("chunkKey", String.class, int.class, int.class);

            if (nmsEnabled) {
                Class<?> driverCl = Class.forName(
                        "com.yapcore.paper.phase3.nms.InteriorEntityTickDriver", true, app);
                nmsDriver = driverCl.getConstructor().newInstance();
                tickEntities = driverCl.getMethod("tickEntities", java.util.Collection.class);
            }

            getLogger().info("Linked to YapSpatialTickCoordinator — Phase 3 leased spatial tick"
                    + " nms=" + nmsEnabled);
        } catch (ReflectiveOperationException e) {
            getLogger().log(Level.SEVERE, "Failed to link YapEngine Phase 3 bridge", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                onServerTick();
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    private record ChunkSnap(String world, int x, int z, List<Entity> entities) {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void onServerTick() {
        // Interior entity NMS tick is owned by ServerLevel offer/flush (same tick,
        // barrier before tick ends) — avoids Moonrise races from Bukkit-after-tick fan-out.
        return;
    }

    @SuppressWarnings("unused")
    private void onServerTickLegacy() {
        try {
            int entityCount = 0;
            for (World world : Bukkit.getWorlds()) {
                entityCount += world.getEntityCount();
            }
            if (entityCount == 0) {
                return;
            }
            // Snapshot on main thread (Bukkit API is not thread-safe)
            List<ChunkSnap>[] interior = new List[]{
                    new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()
            };
            List<ChunkSnap>[] border = new List[]{
                    new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()
            };
            for (World world : Bukkit.getWorlds()) {
                // Prefer entity iteration — scanning every loaded chunk every tick
                // dominates MSPT on large worlds and loses the scoreboard.
                for (Entity e : world.getEntities()) {
                    Chunk chunk = e.getLocation().getChunk();
                    int cx = chunk.getX();
                    int cz = chunk.getZ();
                    int id = quadrantId(cx, cz);
                    boolean isBorder = (Boolean) isBorderChunk.invoke(null, cx, cz);
                    // Group into per-chunk snaps
                    List<ChunkSnap>[] target = isBorder ? border : interior;
                    ChunkSnap found = null;
                    for (ChunkSnap existing : target[id]) {
                        if (existing.x() == cx && existing.z() == cz
                                && existing.world().equals(world.getName())) {
                            found = existing;
                            break;
                        }
                    }
                    if (found == null) {
                        List<Entity> snap = new ArrayList<>();
                        snap.add(e);
                        target[id].add(new ChunkSnap(world.getName(), cx, cz, snap));
                    } else {
                        found.entities().add(e);
                    }
                }
            }

            for (int i = 0; i < 4; i++) {
                for (ChunkSnap snap : border[i]) {
                    final ChunkSnap c = snap;
                    final int qi = i;
                    String key = (String) chunkKey.invoke(null, c.world(), c.x(), c.z());
                    submitBorderHandoff.invoke(coordinator,
                            "chunk:" + key,
                            key,
                            quadrants[qi],
                            quadrants[qi],
                            (Runnable) () -> tickSnapLeased(c));
                }
            }

            Map map = new EnumMap(quadrantClass);
            for (int i = 0; i < 4; i++) {
                List<ChunkSnap> list = interior[i];
                if (list.isEmpty()) {
                    continue;
                }
                final int qi = i;
                map.put(quadrants[i], (Runnable) () -> tickQuadrantLeased(qi, list));
            }
            if (!map.isEmpty()) {
                Boolean ok = (Boolean) runParallelTick.invoke(coordinator, map);
                ticks++;
                if (Boolean.FALSE.equals(ok) && (ticks % 200) == 0) {
                    getLogger().warning("Phase 3 spatial tick barrier overrun (ticks=" + ticks + ")");
                }
                if ((ticks % 400) == 0) {
                    getLogger().info("Phase 3 tick heartbeat ticks=" + ticks
                            + " nms=" + nmsEnabled);
                }
            }
        } catch (ReflectiveOperationException e) {
            getLogger().log(Level.WARNING, "Phase 3 tick invoke failed", e);
        }
    }

    /** One DLM lease per quadrant — avoids per-chunk acquire overhead under load. */
    private void tickQuadrantLeased(int qi, List<ChunkSnap> list) {
        try {
            ChunkSnap first = list.getFirst();
            String key = "q:" + first.world() + ":" + qi;
            runLeased.invoke(coordinator, key, (Runnable) () -> {
                for (ChunkSnap snap : list) {
                    tickSnapEntities(snap);
                }
            });
        } catch (ReflectiveOperationException e) {
            getLogger().log(Level.FINE, "quadrant leased tick failed", e);
        }
    }

    private void tickSnapEntities(ChunkSnap snap) {
        if (!nmsEnabled || nmsDriver == null || tickEntities == null) {
            if (snap.entities() != null) {
                snap.entities().size();
            }
            return;
        }
        try {
            tickEntities.invoke(nmsDriver, snap.entities());
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void tickSnapLeased(ChunkSnap snap) {
        try {
            String key = (String) chunkKey.invoke(null, snap.world(), snap.x(), snap.z());
            runLeased.invoke(coordinator, key, (Runnable) () -> tickSnapEntities(snap));
        } catch (ReflectiveOperationException e) {
            getLogger().log(Level.FINE, "leased tick failed", e);
        }
    }

    private static int quadrantId(int chunkX, int chunkZ) {
        int east = chunkX >= 0 ? 1 : 0;
        int south = chunkZ >= 0 ? 1 : 0;
        return east | (south << 1);
    }
}
