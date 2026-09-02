package com.yapcore.world.util;

import org.bukkit.Material;
import org.bukkit.Nameable;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Sign;
import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Lightweight tile-entity snapshots for schematics (containers, signs, spawners).
 * Not full Mojang NBT — enough for builder round-trips on Folia.
 */
public final class TileCodec {

    private TileCodec() {
    }

    public static String capture(Block block) {
        if (block == null) {
            return null;
        }
        BlockState state = block.getState();
        List<String> parts = new ArrayList<>();
        if (state instanceof Container container) {
            ItemStack[] contents = container.getInventory().getContents();
            StringBuilder inv = new StringBuilder("inv=");
            for (int i = 0; i < contents.length; i++) {
                ItemStack stack = contents[i];
                if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                    continue;
                }
                if (inv.length() > 4) {
                    inv.append(';');
                }
                inv.append(i).append(':').append(stack.getType().name()).append('*').append(stack.getAmount());
            }
            if (inv.length() > 4) {
                parts.add(inv.toString());
            }
            if (state instanceof Nameable named && named.getCustomName() != null) {
                parts.add("name=" + encodeText(named.getCustomName()));
            }
        }
        if (state instanceof Sign sign) {
            for (int i = 0; i < 4; i++) {
                String line = sign.getLine(i);
                if (line != null && !line.isEmpty()) {
                    parts.add("sign" + i + "=" + encodeText(line));
                }
            }
        }
        if (state instanceof CreatureSpawner spawner) {
            if (spawner.getSpawnedType() != null) {
                parts.add("spawner=" + spawner.getSpawnedType().name());
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        return String.join("|", parts);
    }

    public static void apply(Block block, String tile) {
        if (block == null || tile == null || tile.isBlank()) {
            return;
        }
        BlockState state = block.getState();
        String[] parts = tile.split("\\|");
        if (state instanceof Container container) {
            ItemStack[] contents = container.getInventory().getContents();
            for (String part : parts) {
                if (part.startsWith("inv=")) {
                    for (String slot : part.substring(4).split(";")) {
                        if (slot.isBlank()) {
                            continue;
                        }
                        String[] kv = slot.split(":", 2);
                        if (kv.length < 2) {
                            continue;
                        }
                        int idx = Integer.parseInt(kv[0]);
                        String[] matAmt = kv[1].split("\\*", 2);
                        Material mat = Material.matchMaterial(matAmt[0]);
                        int amt = matAmt.length > 1 ? Integer.parseInt(matAmt[1]) : 1;
                        if (mat != null && idx >= 0 && idx < contents.length) {
                            contents[idx] = new ItemStack(mat, Math.max(1, amt));
                        }
                    }
                    container.getInventory().setContents(contents);
                } else if (part.startsWith("name=") && state instanceof Nameable named) {
                    named.setCustomName(decodeText(part.substring(5)));
                }
            }
            state.update(true, false);
        }
        if (state instanceof Sign sign) {
            for (String part : parts) {
                if (part.matches("sign[0-3]=.*")) {
                    int line = part.charAt(4) - '0';
                    sign.setLine(line, decodeText(part.substring(6)));
                }
            }
            sign.update(true, false);
        }
        if (state instanceof CreatureSpawner spawner) {
            for (String part : parts) {
                if (part.startsWith("spawner=")) {
                    try {
                        spawner.setSpawnedType(org.bukkit.entity.EntityType.valueOf(part.substring(8)));
                        spawner.update(true, false);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
    }

    private static String encodeText(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeText(String s) {
        try {
            return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return s;
        }
    }
}
