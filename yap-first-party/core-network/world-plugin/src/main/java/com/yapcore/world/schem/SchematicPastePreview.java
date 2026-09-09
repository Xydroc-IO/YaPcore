package com.yapcore.world.schem;

import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import com.yapcore.world.WorldPlugin;
import com.yapcore.world.cui.WorldEditCuiBridge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
 * Supports interactive rotate / flip before placing.
 */
public final class SchematicPastePreview implements Listener {

    public record Pending(String label, Schematic schematic, String world,
                          int originX, int originY, int originZ, boolean ignoreAir,
                          int rotationTurns) {
        public Pending(String label, Schematic schematic, String world,
                       int originX, int originY, int originZ, boolean ignoreAir) {
            this(label, schematic, world, originX, originY, originZ, ignoreAir, 0);
        }

        public Schematic.Bounds bounds() {
            return schematic.bounds();
        }

        public int yawDegrees() {
            return ((rotationTurns % 4) + 4) % 4 * 90;
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
                originX, originY, originZ, ignoreAir, 0);
        pending.put(id, next);
        refreshVisuals(player, next);
        startParticles(player, next);
        Schematic.Bounds b = next.bounds();
        player.sendMessage("§aSchem preview: §f" + label
                + " §7(" + schematic.blocks().size() + " blocks, "
                + b.sizeX() + "×" + b.sizeY() + "×" + b.sizeZ() + ")");
        tipActionBar(player, next);
        if (plugin.previewControls() != null) {
            plugin.previewControls().give(player);
        }
        // Always open clickable schematics menu (Confirm / Move / Rotate).
        plugin.openSchematicsGui(player);
    }

    /** Move pending preview origin to the player's feet. */
    public boolean moveHere(Player player) {
        Pending cur = pending.get(player.getUniqueId());
        if (cur == null) {
            return false;
        }
        Location loc = player.getLocation();
        Pending next = new Pending(cur.label(), cur.schematic(), loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), cur.ignoreAir(), cur.rotationTurns());
        pending.put(player.getUniqueId(), next);
        stopParticles(player.getUniqueId());
        refreshVisuals(player, next);
        startParticles(player, next);
        player.sendMessage("§aPreview moved to §f"
                + next.originX() + " " + next.originY() + " " + next.originZ());
        tipActionBar(player, next);
        return true;
    }

    /**
     * Rotate pending schematic about Y (90° steps). Positive degrees = clockwise looking down.
     * @return new yaw degrees, or empty if no preview
     */
    public Optional<Integer> rotateY(Player player, int degrees) {
        Pending cur = pending.get(player.getUniqueId());
        if (cur == null) {
            return Optional.empty();
        }
        int turns = SchematicTransforms.normalizeTurns(degrees);
        if (turns == 0) {
            tipActionBar(player, cur);
            return Optional.of(cur.yawDegrees());
        }
        Schematic rotated = SchematicTransforms.rotateY(cur.schematic(), turns * 90);
        int nextTurns = (cur.rotationTurns() + turns) % 4;
        Pending next = new Pending(cur.label(), rotated, cur.world(),
                cur.originX(), cur.originY(), cur.originZ(), cur.ignoreAir(), nextTurns);
        UUID id = player.getUniqueId();
        pending.put(id, next);
        stopParticles(id);
        refreshVisuals(player, next);
        startParticles(player, next);
        Schematic.Bounds b = next.bounds();
        player.sendMessage("§aPreview rotated §f" + (turns * 90) + "° §7→ yaw §f" + next.yawDegrees()
                + "° §7(" + b.sizeX() + "×" + b.sizeY() + "×" + b.sizeZ() + ")");
        tipActionBar(player, next);
        return Optional.of(next.yawDegrees());
    }

    /** Flip pending schematic on axis x/y/z. */
    public boolean flip(Player player, char axis) {
        Pending cur = pending.get(player.getUniqueId());
        if (cur == null) {
            return false;
        }
        Schematic flipped = SchematicTransforms.flip(cur.schematic(), axis);
        if (flipped == null) {
            return false;
        }
        Pending next = new Pending(cur.label(), flipped, cur.world(),
                cur.originX(), cur.originY(), cur.originZ(), cur.ignoreAir(), cur.rotationTurns());
        UUID id = player.getUniqueId();
        pending.put(id, next);
        stopParticles(id);
        refreshVisuals(player, next);
        startParticles(player, next);
        player.sendMessage("§aPreview flipped on §f" + Character.toLowerCase(axis));
        tipActionBar(player, next);
        return true;
    }

    public boolean cancel(Player player) {
        UUID id = player.getUniqueId();
        if (pending.remove(id) == null) {
            return false;
        }
        stopParticles(id);
        if (plugin.previewControls() != null) {
            plugin.previewControls().clear(player);
        }
        WorldEditCuiBridge cui = plugin.cui();
        if (cui != null) {
            cui.update(player);
        }
        player.sendActionBar(Component.empty());
        player.sendMessage("§eSchem preview cancelled.");
        return true;
    }

    public CompletableFuture<Integer> confirm(Player player) {
        UUID id = player.getUniqueId();
        Pending cur = pending.remove(id);
        stopParticles(id);
        if (plugin.previewControls() != null) {
            plugin.previewControls().clear(player);
        }
        WorldEditCuiBridge cui = plugin.cui();
        if (cui != null) {
            cui.update(player);
        }
        player.sendActionBar(Component.empty());
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
                    player.sendMessage("§aPasted §f" + n + " §ablocks"
                            + (cur.yawDegrees() != 0 ? " §7(rotated §f" + cur.yawDegrees() + "°§7)" : "")
                            + ".");
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
        Player p = plugin.getServer().getPlayer(playerId);
        if (p != null && plugin.previewControls() != null) {
            plugin.previewControls().clear(p);
        }
    }

    public void clearAll() {
        for (UUID id : particleTasks.keySet()) {
            stopParticles(id);
        }
        if (plugin.previewControls() != null) {
            plugin.previewControls().clearAll();
        }
        pending.clear();
    }

    private static void tipActionBar(Player player, Pending p) {
        player.sendActionBar(Component.text(
                "Preview · " + p.label() + " · " + p.yawDegrees() + "° · Confirm / Move / Rotate",
                NamedTextColor.AQUA));
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
            // Soft remider while walking with a pending preview
            if ((System.currentTimeMillis() / 2000L) % 2 == 0) {
                tipActionBar(player, cur);
            }
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
        // Hue shifts slightly with yaw so rotation is visible on the outline.
        int yaw = p.yawDegrees();
        Color color = switch (yaw) {
            case 90 -> Color.fromRGB(120, 220, 120);
            case 180 -> Color.fromRGB(255, 200, 80);
            case 270 -> Color.fromRGB(220, 120, 255);
            default -> Color.fromRGB(80, 200, 255);
        };
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.1f);
        int x0 = p.originX();
        int y0 = p.originY();
        int z0 = p.originZ();
        int x1 = p.maxX() + 1;
        int y1 = p.maxY() + 1;
        int z1 = p.maxZ() + 1;
        int step = Math.max(1, Math.max(Math.max(x1 - x0, y1 - y0), z1 - z0) / 48);
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
        // Direction tick on +Z edge of the box (facing cue)
        int midX = (x0 + x1) / 2;
        int tipZ = z1;
        int tipX = midX;
        if (yaw == 90) {
            tipX = x1;
            tipZ = (z0 + z1) / 2;
        } else if (yaw == 180) {
            tipZ = z0;
        } else if (yaw == 270) {
            tipX = x0;
            tipZ = (z0 + z1) / 2;
        }
        spawn(player, world, tipX + 0.5, y1 + 0.2, tipZ + 0.5,
                new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.4f));
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
