package com.yapcore.world.schem;

import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import com.yapcore.world.WorldPlugin;
import com.yapcore.world.cui.WorldEditCuiBridge;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pending schematic paste with particle + WorldEditCUI outline until confirm/cancel.
 */
public final class SchematicPastePreview implements Listener {

    public record Pending(String label, Schematic schematic, String world,
                          int originX, int originY, int originZ, boolean ignoreAir) {
        public Schematic.Bounds bounds() {
            return schematic.bounds();
        }

        public int maxX() {
            return originX + Math.max(0, bounds().sizeX() - 1);
        }

        public int maxY() {
            return originY + Math.max(0, bounds().sizeY() - 1);
        }

        public int maxZ() {
            return originZ + Math.max(0, bounds().sizeZ() - 1);
        }
    }

    private final WorldPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, YapTask> particleTasks = new ConcurrentHashMap<>();

    public SchematicPastePreview(WorldPlugin plugin) {
        this.plugin = plugin;
    }

    public Optional<Pending> get(UUID playerId) {
        return Optional.ofNullable(pending.get(playerId));
    }

    public boolean has(UUID playerId) {
        return pending.containsKey(playerId);
    }

    /** Start or replace a paste preview at the given origin. */
    public void begin(Player player, Schematic schematic, String label,
                      int originX, int originY, int originZ, boolean ignoreAir) {
        if (player == null || schematic == null) {
            return;
        }
        UUID id = player.getUniqueId();
        stopParticles(id);
        Pending next = new Pending(label, schematic, player.getWorld().getName(),
                originX, originY, originZ, ignoreAir);
        pending.put(id, next);
        refreshVisuals(player, next);
        startParticles(player, next);
        Schematic.Bounds b = next.bounds();
        player.sendMessage("§aSchem preview: §f" + label
                + " §7(" + schematic.blocks().size() + " blocks, "
                + b.sizeX() + "×" + b.sizeY() + "×" + b.sizeZ() + ")");
        player.sendMessage("§7Outline shows paste box. Use §fConfirm §7/ §fMove here §7/ §fCancel §7in the Schematics menu");
        player.sendMessage("§8Or chat: §f//schem confirm§8 · §f//schem here§8 · §f//schem cancel");
        player.sendMessage("§8Skip preview next time: §f//schem paste " + label + " -y");
    }

    /** Move pending preview origin to the player's feet. */
    public boolean moveHere(Player player) {
        Pending cur = pending.get(player.getUniqueId());
        if (cur == null) {
            return false;
        }
        Location loc = player.getLocation();
        Pending next = new Pending(cur.label(), cur.schematic(), loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), cur.ignoreAir());
        pending.put(player.getUniqueId(), next);
        stopParticles(player.getUniqueId());
        refreshVisuals(player, next);
        startParticles(player, next);
        player.sendMessage("§aPreview moved to §f"
                + next.originX() + " " + next.originY() + " " + next.originZ());
        return true;
    }

    public boolean cancel(Player player) {
        UUID id = player.getUniqueId();
        if (pending.remove(id) == null) {
            return false;
        }
        stopParticles(id);
        WorldEditCuiBridge cui = plugin.cui();
        if (cui != null) {
            cui.update(player);
        }
        player.sendMessage("§eSchem preview cancelled.");
        return true;
    }

    public CompletableFuture<Integer> confirm(Player player) {
        UUID id = player.getUniqueId();
        Pending cur = pending.remove(id);
        stopParticles(id);
        WorldEditCuiBridge cui = plugin.cui();
        if (cui != null) {
            cui.update(player);
        }
        if (cur == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("no pending preview"));
        }
        World world = player.getServer().getWorld(cur.world());
        if (world == null) {
            world = player.getWorld();
        }
        boolean large = plugin.paster().isLargePaste(cur.schematic().blocks().size());
        if (large) {
            player.sendMessage("§eLarge schem paste §7(§f" + cur.schematic().blocks().size()
                    + " §7blocks) — progress on.");
        }
        World target = world;
        return plugin.paster().paste(player, cur.schematic(), target,
                        cur.originX(), cur.originY(), cur.originZ(), cur.ignoreAir())
                .whenComplete((count, err) -> YapSched.global(plugin, () -> {
                    if (err != null) {
                        player.sendMessage("§cPaste failed: " + err.getMessage());
                        return;
                    }
                    int n = count == null ? 0 : count;
                    player.sendMessage("§aPasted §f" + n + " §ablocks.");
                    if (plugin.editState() != null && plugin.editState().isFast(id)) {
                        player.sendMessage("§7§oFast mode on — paste was not recorded for //undo.");
                    } else {
                        player.sendMessage("§7Wrong place? §f//undo §7(or §f//schem undo§7) to remove.");
                    }
                    if (large && plugin.worldConfig().deferRelightLarge()) {
                        player.sendMessage("§7Relighting…");
                    }
                }));
    }

    public void clear(UUID playerId) {
        pending.remove(playerId);
        stopParticles(playerId);
    }

    public void clearAll() {
        for (UUID id : particleTasks.keySet()) {
            stopParticles(id);
        }
        pending.clear();
    }

    private void refreshVisuals(Player player, Pending p) {
        WorldEditCuiBridge cui = plugin.cui();
        if (cui != null) {
            cui.showCuboid(player, p.originX(), p.originY(), p.originZ(),
                    p.maxX(), p.maxY(), p.maxZ());
        }
    }

    private void startParticles(Player player, Pending p) {
        UUID id = player.getUniqueId();
        final YapTask[] handle = new YapTask[1];
        handle[0] = YapSched.globalTimer(plugin, () -> YapSched.entity(plugin, player, () -> {
            Pending cur = pending.get(id);
            if (!player.isOnline() || cur == null) {
                if (handle[0] != null) {
                    handle[0].cancel();
                }
                particleTasks.remove(id);
                return;
            }
            if (!player.getWorld().getName().equals(cur.world())) {
                return;
            }
            drawBox(player, cur);
        }), 1L, 10L);
        particleTasks.put(id, handle[0]);
    }

    private void stopParticles(UUID id) {
        YapTask task = particleTasks.remove(id);
        if (task != null) {
            task.cancel();
        }
    }

    static void drawBox(Player player, Pending p) {
        World world = player.getWorld();
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(80, 200, 255), 1.1f);
        int x0 = p.originX();
        int y0 = p.originY();
        int z0 = p.originZ();
        int x1 = p.maxX() + 1;
        int y1 = p.maxY() + 1;
        int z1 = p.maxZ() + 1;
        int step = Math.max(1, Math.max(Math.max(x1 - x0, y1 - y0), z1 - z0) / 48);
        // 12 edges of the AABB
        edge(player, world, x0, y0, z0, x1, y0, z0, step, dust);
        edge(player, world, x0, y0, z1, x1, y0, z1, step, dust);
        edge(player, world, x0, y1, z0, x1, y1, z0, step, dust);
        edge(player, world, x0, y1, z1, x1, y1, z1, step, dust);
        edge(player, world, x0, y0, z0, x0, y0, z1, step, dust);
        edge(player, world, x1, y0, z0, x1, y0, z1, step, dust);
        edge(player, world, x0, y1, z0, x0, y1, z1, step, dust);
        edge(player, world, x1, y1, z0, x1, y1, z1, step, dust);
        edge(player, world, x0, y0, z0, x0, y1, z0, step, dust);
        edge(player, world, x1, y0, z0, x1, y1, z0, step, dust);
        edge(player, world, x0, y0, z1, x0, y1, z1, step, dust);
        edge(player, world, x1, y0, z1, x1, y1, z1, step, dust);
    }

    private static void edge(Player player, World world,
                             int ax, int ay, int az, int bx, int by, int bz,
                             int step, Particle.DustOptions dust) {
        int dx = Integer.compare(bx, ax);
        int dy = Integer.compare(by, ay);
        int dz = Integer.compare(bz, az);
        int x = ax;
        int y = ay;
        int z = az;
        while (true) {
            spawn(player, world, x + 0.5, y + 0.5, z + 0.5, dust);
            if (x == bx && y == by && z == bz) {
                break;
            }
            x += dx * step;
            y += dy * step;
            z += dz * step;
            if ((dx > 0 && x > bx) || (dx < 0 && x < bx)) {
                x = bx;
            }
            if ((dy > 0 && y > by) || (dy < 0 && y < by)) {
                y = by;
            }
            if ((dz > 0 && z > bz) || (dz < 0 && z < bz)) {
                z = bz;
            }
        }
    }

    private static void spawn(Player player, World world, double x, double y, double z,
                              Particle.DustOptions dust) {
        player.spawnParticle(Particle.DUST, new Location(world, x, y, z), 1, 0, 0, 0, 0, dust);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer().getUniqueId());
    }
}
