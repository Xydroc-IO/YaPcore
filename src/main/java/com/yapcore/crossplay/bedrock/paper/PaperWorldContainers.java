package com.yapcore.crossplay.bedrock.paper;

import com.yapcore.crossplay.bedrock.BedrockContainerBridge;
import com.yapcore.crossplay.bedrock.BedrockItemStates;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

final class PaperWorldContainers {

    private final PaperWorldSyncBackend backend;

    PaperWorldContainers(PaperWorldSyncBackend backend) {
        this.backend = backend;
    }

    void openContainer(String playerName, int type, int x, int y, int z) {
        try {
            if (playerName == null || playerName.isBlank() || !backend.isEnabled()) {
                return;
            }
            ClassLoader cl = backend.liveLoader();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Object player = PaperWorldMainThread.findPlayer(bukkit, playerName);
            if (player == null) {
                return;
            }
            Object block = backend.blocks.blockAt(x, y, z);
            if (block == null) {
                return;
            }
            Object state = block.getClass().getMethod("getState").invoke(block);
            if (state == null) {
                return;
            }
            try {
                Object inv = state.getClass().getMethod("getInventory").invoke(state);
                if (inv != null) {
                    player.getClass().getMethod("openInventory",
                                    Class.forName("org.bukkit.inventory.Inventory", true, cl))
                            .invoke(player, inv);
                    PaperWorldSyncBackend.LOG.fine(() -> "Paper openInventory " + playerName + " @" + x + "," + y + "," + z);
                    return;
                }
            } catch (NoSuchMethodException ignored) {
            }
            if (type == BedrockContainerBridge.TYPE_ENCHANT) {
                try {
                    Object loc = block.getClass().getMethod("getLocation").invoke(block);
                    player.getClass().getMethod("openEnchanting",
                                    Class.forName("org.bukkit.Location", true, cl), boolean.class)
                            .invoke(player, loc, true);
                } catch (NoSuchMethodException ignored) {
                }
            } else if (type == BedrockContainerBridge.TYPE_WORKBENCH) {
                try {
                    Object loc = block.getClass().getMethod("getLocation").invoke(block);
                    player.getClass().getMethod("openWorkbench",
                                    Class.forName("org.bukkit.Location", true, cl), boolean.class)
                            .invoke(player, loc, true);
                } catch (NoSuchMethodException ignored) {
                }
            } else if (type == BedrockContainerBridge.TYPE_ANVIL) {
                openVirtual(player, block, cl, "openAnvil");
            } else if (type == BedrockContainerBridge.TYPE_SMITHING) {
                openVirtual(player, block, cl, "openSmithingTable");
            } else if (type == BedrockContainerBridge.TYPE_LOOM) {
                openVirtual(player, block, cl, "openLoom");
            } else if (type == BedrockContainerBridge.TYPE_STONECUTTER) {
                openVirtual(player, block, cl, "openStonecutter");
            } else if (type == BedrockContainerBridge.TYPE_CARTOGRAPHY) {
                openVirtual(player, block, cl, "openCartographyTable");
            }
        } catch (Exception e) {
            PaperWorldSyncBackend.LOG.log(Level.FINE, "Paper openContainer failed", e);
        }
    }

    private void openVirtual(Object player, Object block, ClassLoader cl, String method) {
        try {
            Object loc = block.getClass().getMethod("getLocation").invoke(block);
            player.getClass().getMethod(method,
                            Class.forName("org.bukkit.Location", true, cl), boolean.class)
                    .invoke(player, loc, true);
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            PaperWorldSyncBackend.LOG.log(Level.FINE, "Paper " + method + " failed", e);
        }
    }

    void closeContainer(String playerName) {
        try {
            if (playerName == null || !backend.isEnabled()) {
                return;
            }
            ClassLoader cl = backend.liveLoader();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Object player = PaperWorldMainThread.findPlayer(bukkit, playerName);
            if (player != null) {
                player.getClass().getMethod("closeInventory").invoke(player);
            }
        } catch (Exception e) {
            PaperWorldSyncBackend.LOG.log(Level.FINE, "Paper closeContainer failed", e);
        }
    }

    int[][] snapshotBlockInventory(int x, int y, int z, int slots) {
        if (!backend.isEnabled() || slots <= 0) {
            return null;
        }
        try {
            ClassLoader cl = backend.liveLoader();
            if (cl == null) {
                return null;
            }
            Object block = backend.blocks.blockAt(x, y, z);
            if (block == null) {
                return null;
            }
            Object state = block.getClass().getMethod("getState").invoke(block);
            if (state == null) {
                return null;
            }
            Object inv = state.getClass().getMethod("getInventory").invoke(state);
            if (inv == null) {
                return null;
            }
            Object[] contents = (Object[]) inv.getClass().getMethod("getContents").invoke(inv);
            int[] ids = new int[slots];
            int[] counts = new int[slots];
            for (int i = 0; i < slots; i++) {
                Object stack = contents != null && i < contents.length ? contents[i] : null;
                ids[i] = PaperWorldInventory.itemStackToNetworkId(stack);
                counts[i] = PaperWorldInventory.itemStackAmount(stack);
            }
            return new int[][]{ids, counts};
        } catch (Exception e) {
            PaperWorldSyncBackend.LOG.log(Level.FINE, "block inventory snapshot failed @" + x + "," + y + "," + z, e);
            return null;
        }
    }

    int[] snapshotFurnaceProgress(int x, int y, int z) {
        if (!backend.isEnabled()) {
            return null;
        }
        try {
            Object block = backend.blocks.blockAt(x, y, z);
            if (block == null) {
                return null;
            }
            Object state = block.getClass().getMethod("getState").invoke(block);
            if (state == null) {
                return null;
            }
            ClassLoader cl = backend.liveLoader();
            Class<?> furnaceCl = Class.forName("org.bukkit.block.Furnace", true, cl);
            if (!furnaceCl.isInstance(state)) {
                return null;
            }
            short cook = ((Number) state.getClass().getMethod("getCookTime").invoke(state)).shortValue();
            short cookTotal = 200;
            try {
                cookTotal = ((Number) state.getClass().getMethod("getCookTimeTotal").invoke(state)).shortValue();
            } catch (NoSuchMethodException ignored) {
            }
            short burn = ((Number) state.getClass().getMethod("getBurnTime").invoke(state)).shortValue();
            short burnTotal = burn;
            try {
                Object inv = state.getClass().getMethod("getInventory").invoke(state);
                if (inv != null) {
                    burnTotal = (short) Math.max(burn, 1);
                }
            } catch (Exception ignored) {
            }
            return new int[]{cook & 0xffff, cookTotal & 0xffff, burn & 0xffff, burnTotal & 0xffff};
        } catch (Exception e) {
            PaperWorldSyncBackend.LOG.log(Level.FINE, "furnace progress snapshot failed @" + x + "," + y + "," + z, e);
            return null;
        }
    }

    boolean openMerchant(String playerName, String villagerNameHint) {
        if (!backend.isEnabled() || playerName == null) {
            return false;
        }
        try {
            backend.mainThread.runOnMain(() -> {
                try {
                    ClassLoader cl = backend.liveLoader();
                    Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
                    Object player = PaperWorldMainThread.findPlayer(bukkit, playerName);
                    if (player == null) {
                        return;
                    }
                    Object loc = player.getClass().getMethod("getLocation").invoke(player);
                    Object world = loc.getClass().getMethod("getWorld").invoke(loc);
                    if (world == null) {
                        return;
                    }
                    Class<?> villagerCl = Class.forName("org.bukkit.entity.Villager", true, cl);
                    @SuppressWarnings("unchecked")
                    Collection<Object> nearby = (Collection<Object>) world.getClass()
                            .getMethod("getNearbyEntities",
                                    loc.getClass(), double.class, double.class, double.class)
                            .invoke(world, loc, 6.0, 4.0, 6.0);
                    Object best = null;
                    for (Object e : nearby) {
                        if (e == null || !villagerCl.isInstance(e)) {
                            continue;
                        }
                        if (villagerNameHint != null && !villagerNameHint.isBlank()) {
                            String n = String.valueOf(e.getClass().getMethod("getName").invoke(e));
                            if (n != null && n.toLowerCase(Locale.ROOT)
                                    .contains(villagerNameHint.toLowerCase(Locale.ROOT))) {
                                best = e;
                                break;
                            }
                        }
                        if (best == null) {
                            best = e;
                        }
                    }
                    if (best == null) {
                        return;
                    }
                    try {
                        player.getClass().getMethod("openMerchant",
                                        Class.forName("org.bukkit.inventory.Merchant", true, cl),
                                        boolean.class)
                                .invoke(player, best, true);
                    } catch (NoSuchMethodException e) {
                        player.getClass().getMethod("openInventory",
                                        Class.forName("org.bukkit.inventory.InventoryView", true, cl))
                                .invoke(player, best.getClass().getMethod("getInventory").invoke(best));
                    }
                } catch (Exception e) {
                    PaperWorldSyncBackend.LOG.log(Level.FINE, "openMerchant failed", e);
                }
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    List<int[]> snapshotMerchantOffers(String playerName, int max) {
        List<int[]> out = new ArrayList<>();
        if (!backend.isEnabled()) {
            return out;
        }
        try {
            ClassLoader cl = backend.liveLoader();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Object player = PaperWorldMainThread.findPlayer(bukkit, playerName);
            if (player == null) {
                return out;
            }
            Object open = player.getClass().getMethod("getOpenInventory").invoke(player);
            if (open == null) {
                return out;
            }
            Object top = open.getClass().getMethod("getTopInventory").invoke(open);
            Object holder = top != null ? top.getClass().getMethod("getHolder").invoke(top) : null;
            Object merchant = holder;
            if (merchant == null || !Class.forName("org.bukkit.inventory.Merchant", true, cl).isInstance(merchant)) {
                return out;
            }
            @SuppressWarnings("unchecked")
            List<Object> recipes = (List<Object>) merchant.getClass()
                    .getMethod("getRecipes").invoke(merchant);
            if (recipes == null) {
                return out;
            }
            int n = Math.min(max, recipes.size());
            for (int i = 0; i < n; i++) {
                Object r = recipes.get(i);
                Object buy = r.getClass().getMethod("getIngredients").invoke(r);
                Object result = r.getClass().getMethod("getResult").invoke(r);
                int[] row = new int[6];
                if (buy instanceof List<?> list && !list.isEmpty()) {
                    fillStackIds(list.get(0), row, 0);
                    if (list.size() > 1) {
                        fillStackIds(list.get(1), row, 2);
                    }
                }
                fillStackIds(result, row, 4);
                out.add(row);
            }
        } catch (Exception e) {
            PaperWorldSyncBackend.LOG.log(Level.FINE, "snapshotMerchantOffers failed", e);
        }
        return out;
    }

    boolean setBlockInventorySlot(int x, int y, int z, int slot, String materialName, int amount) {
        if (!backend.isEnabled() || slot < 0) {
            return false;
        }
        try {
            backend.mainThread.runOnMain(() -> {
                try {
                    Object block = backend.blocks.blockAt(x, y, z);
                    if (block == null) {
                        return;
                    }
                    Object state = block.getClass().getMethod("getState").invoke(block);
                    if (state == null) {
                        return;
                    }
                    Object inv = state.getClass().getMethod("getInventory").invoke(state);
                    if (inv == null) {
                        return;
                    }
                    ClassLoader cl = backend.liveLoader();
                    Class<?> matCl = Class.forName("org.bukkit.Material", true, cl);
                    Class<?> stackCl = Class.forName("org.bukkit.inventory.ItemStack", true, cl);
                    Object stack = null;
                    if (amount > 0 && materialName != null && !"AIR".equalsIgnoreCase(materialName)) {
                        Object mat = matCl.getMethod("matchMaterial", String.class).invoke(null, materialName);
                        if (mat == null) {
                            mat = matCl.getMethod("valueOf", String.class).invoke(null,
                                    materialName.toUpperCase(Locale.ROOT).replace('-', '_'));
                        }
                        stack = stackCl.getConstructor(matCl, int.class).newInstance(mat, amount);
                    }
                    inv.getClass().getMethod("setItem", int.class, stackCl).invoke(inv, slot, stack);
                    try {
                        state.getClass().getMethod("update", boolean.class, boolean.class)
                                .invoke(state, true, false);
                    } catch (NoSuchMethodException ignored) {
                        state.getClass().getMethod("update").invoke(state);
                    }
                } catch (Exception e) {
                    PaperWorldSyncBackend.LOG.log(Level.FINE, "setBlockInventorySlot failed", e);
                }
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void fillStackIds(Object stack, int[] row, int offset) throws ReflectiveOperationException {
        if (stack == null) {
            return;
        }
        Object type = stack.getClass().getMethod("getType").invoke(stack);
        int amount = ((Number) stack.getClass().getMethod("getAmount").invoke(stack)).intValue();
        String mat = type == null ? "air" : String.valueOf(type).toLowerCase(Locale.ROOT)
                .replace("minecraft:", "");
        int nid = 0;
        for (BedrockItemStates.ItemState s : BedrockItemStates.all()) {
            String n = s.name().replace("minecraft:", "");
            if (n.equalsIgnoreCase(mat)) {
                nid = s.runtimeId() & 0xFFFF;
                break;
            }
        }
        row[offset] = nid;
        row[offset + 1] = amount;
    }
}
