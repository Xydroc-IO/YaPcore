package com.yapcore.yapblock.gen;

import com.yapcore.sched.YapSched;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Classic starter island: grass/dirt mound, bedrock, oak, filled chest.
 * <pre>
 * Grass ~27 · Dirt ~51 · Bedrock 1 · Oak tree (~6 logs) · no sand
 * </pre>
 */
public final class IslandStarterPack {

    private final JavaPlugin plugin;

    public IslandStarterPack(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Void> paste(World world, int centerX, int pasteY, int centerZ) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        YapSched.region(plugin, world, centerX, centerZ, () -> {
            try {
                buildIsland(world, centerX, pasteY, centerZ);
                int chestX = centerX + 1;
                int chestY = pasteY + 1;
                int chestZ = centerZ;
                // Place + fill on the same region tick after oak, then confirm one tick later.
                placeAndFillChest(world, chestX, chestY, chestZ);
                YapSched.regionChunkLater(plugin, world, chestX >> 4, chestZ >> 4, () -> {
                    try {
                        if (!ensureChestFilled(world, chestX, chestY, chestZ)) {
                            plugin.getLogger().warning("Starter chest still empty at "
                                    + chestX + "," + chestY + "," + chestZ + " — retrying");
                            placeAndFillChest(world, chestX, chestY, chestZ);
                        }
                        done.complete(null);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Starter chest fill failed", e);
                        done.completeExceptionally(e);
                    }
                }, 2L);
            } catch (Exception e) {
                done.completeExceptionally(e);
            }
        });
        return done;
    }

    private static void buildIsland(World world, int cx, int cy, int cz) {
        List<int[]> surface = new ArrayList<>(30);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                surface.add(new int[]{dx, dz});
            }
        }
        surface.add(new int[]{-3, 0});
        surface.add(new int[]{3, 0});
        // Extra grass where sand used to sit (no sand on starter).
        surface.add(new int[]{3, -1});
        surface.add(new int[]{3, -2});
        surface.add(new int[]{2, -3});

        for (int[] p : surface) {
            world.getBlockAt(cx + p[0], cy, cz + p[1]).setType(Material.GRASS_BLOCK, false);
            world.getBlockAt(cx + p[0], cy - 1, cz + p[1]).setType(Material.DIRT, false);
        }

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(cx + dx, cy - 2, cz + dz).setType(Material.DIRT, false);
            }
        }
        world.getBlockAt(cx - 1, cy - 3, cz).setType(Material.DIRT, false);
        world.getBlockAt(cx + 1, cy - 3, cz).setType(Material.DIRT, false);
        world.getBlockAt(cx, cy - 3, cz - 1).setType(Material.DIRT, false);
        world.getBlockAt(cx, cy - 3, cz + 1).setType(Material.DIRT, false);
        world.getBlockAt(cx - 1, cy - 3, cz - 1).setType(Material.DIRT, false);
        world.getBlockAt(cx + 1, cy - 3, cz + 1).setType(Material.DIRT, false);
        world.getBlockAt(cx, cy - 3, cz).setType(Material.BEDROCK, false);

        placeFallbackOak(world, cx - 1, cy + 1, cz);
    }

    static void placeAndFillChest(World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.CHEST, false);
        if (!(block.getState() instanceof Chest chest)) {
            return;
        }
        // Snapshot inventory + update — do not mix getBlockInventory() with update().
        Inventory inv = chest.getInventory();
        inv.clear();
        for (ItemStack stack : starterItems()) {
            inv.addItem(stack);
        }
        chest.update(true, false);
    }

    static boolean ensureChestFilled(World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() != Material.CHEST) {
            return false;
        }
        if (!(block.getState() instanceof Chest chest)) {
            return false;
        }
        return !chest.getBlockInventory().isEmpty();
    }

    private static ItemStack[] starterItems() {
        return new ItemStack[]{
                new ItemStack(Material.WATER_BUCKET, 1),
                new ItemStack(Material.LAVA_BUCKET, 1),
                new ItemStack(Material.ICE, 2),
                new ItemStack(Material.SUGAR_CANE, 1),
                new ItemStack(Material.MELON_SLICE, 1),
                new ItemStack(Material.PUMPKIN_SEEDS, 1),
                new ItemStack(Material.CACTUS, 1),
                new ItemStack(Material.STRING, 12),
                new ItemStack(Material.BONE, 1),
                new ItemStack(Material.RED_MUSHROOM, 1),
                new ItemStack(Material.BROWN_MUSHROOM, 1)
        };
    }

    private static void placeFallbackOak(World world, int x, int y, int z) {
        for (int i = 0; i < 6; i++) {
            world.getBlockAt(x, y + i, z).setType(Material.OAK_LOG, false);
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 3; dy <= 5; dy++) {
                    if (dx == 0 && dz == 0 && dy < 5) {
                        continue;
                    }
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2 && dy == 3) {
                        continue;
                    }
                    // Never overwrite the starter chest column (east of spawn center).
                    if (dx == 2 && dz == 0 && dy == 0) {
                        continue;
                    }
                    Block leaf = world.getBlockAt(x + dx, y + dy, z + dz);
                    if (leaf.getType().isAir() || leaf.getType() == Material.OAK_LEAVES) {
                        leaf.setType(Material.OAK_LEAVES, false);
                    }
                }
            }
        }
        world.getBlockAt(x, y + 6, z).setType(Material.OAK_LEAVES, false);
    }
}
