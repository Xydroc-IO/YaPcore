package com.yapcore.crossplay.bedrock.paper;

import com.yapcore.crossplay.bedrock.BedrockItemStates;

import java.util.logging.Level;

final class PaperWorldInventory {

    private final PaperWorldSyncBackend backend;

    PaperWorldInventory(PaperWorldSyncBackend backend) {
        this.backend = backend;
    }

    void applyInventory(String playerName, int hotbar, int slot, String itemHint) {
        try {
            if (playerName == null || playerName.isBlank()) {
                return;
            }
            ClassLoader cl = backend.paperLoader.get();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Object player = PaperWorldMainThread.findPlayer(bukkit, playerName);
            if (player == null) {
                return;
            }
            Object inv = player.getClass().getMethod("getInventory").invoke(player);
            if (hotbar >= 0 && hotbar <= 8) {
                inv.getClass().getMethod("setHeldItemSlot", int.class).invoke(inv, hotbar);
            }
            if (slot >= 0 && itemHint != null && !itemHint.isBlank() && !"air".equalsIgnoreCase(itemHint)) {
                Object mat = backend.blocks.materialValue(itemHint.toUpperCase().replace("MINECRAFT:", ""));
                if (mat != null) {
                    Class<?> isClass = Class.forName("org.bukkit.inventory.ItemStack", true, cl);
                    Object stack = isClass.getConstructor(
                            Class.forName("org.bukkit.Material", true, cl)).newInstance(mat);
                    inv.getClass().getMethod("setItem", int.class, isClass).invoke(inv, slot, stack);
                }
            }
            PaperWorldSyncBackend.LOG.fine(() -> "Paper INV " + playerName + " hotbar=" + hotbar + " slot=" + slot);
        } catch (Exception e) {
            PaperWorldSyncBackend.LOG.log(Level.FINE, "Paper INV failed", e);
        }
    }

    int[] snapshotInventoryNetworkIds(String username, int slots) {
        int[] live = snapshotInventoryNetworkIdsLiveOnly(username, slots);
        if (live != null) {
            return live;
        }
        return backend.inventoryInject.snapshotNetworkIds(username, slots);
    }

    int[] snapshotInventoryNetworkIdsLiveOnly(String username, int slots) {
        int[][] full = snapshotInventoryStacksLiveOnly(username, slots);
        return full == null ? null : full[0];
    }

    int[][] snapshotInventoryStacksLiveOnly(String username, int slots) {
        if (!backend.isEnabled() || username == null || username.isBlank()) {
            return null;
        }
        try {
            ClassLoader cl = backend.liveLoader();
            if (cl == null) {
                return null;
            }
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Object player = PaperWorldMainThread.findPlayer(bukkit, username);
            if (player == null) {
                return null;
            }
            Object inv = player.getClass().getMethod("getInventory").invoke(player);
            Object[] contents = (Object[]) inv.getClass().getMethod("getStorageContents").invoke(inv);
            int n = Math.max(0, slots);
            int[] ids = new int[n];
            int[] counts = new int[n];
            for (int i = 0; i < n; i++) {
                Object stack = contents != null && i < contents.length ? contents[i] : null;
                ids[i] = itemStackToNetworkId(stack);
                counts[i] = itemStackAmount(stack);
            }
            return new int[][]{ids, counts};
        } catch (Exception e) {
            PaperWorldSyncBackend.LOG.log(Level.FINE, "inventory snapshot failed for " + username, e);
            return null;
        }
    }

    /** Parallel SkullOwner names for player heads in storage (same length as snapshot ids). */
    String[] snapshotSkullOwnersLiveOnly(String username, int slots) {
        if (!backend.isEnabled() || username == null || username.isBlank()) {
            return null;
        }
        try {
            ClassLoader cl = backend.liveLoader();
            if (cl == null) {
                return null;
            }
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Object player = PaperWorldMainThread.findPlayer(bukkit, username);
            if (player == null) {
                return null;
            }
            Object inv = player.getClass().getMethod("getInventory").invoke(player);
            Object[] contents = (Object[]) inv.getClass().getMethod("getStorageContents").invoke(inv);
            int n = Math.max(0, slots);
            String[] owners = new String[n];
            for (int i = 0; i < n; i++) {
                Object stack = contents != null && i < contents.length ? contents[i] : null;
                owners[i] = skullOwnerFromStack(stack);
            }
            return owners;
        } catch (Exception e) {
            return null;
        }
    }

    static String skullOwnerFromStack(Object stack) {
        if (stack == null) {
            return null;
        }
        try {
            Object type = stack.getClass().getMethod("getType").invoke(stack);
            String name = String.valueOf(type);
            if (!name.toUpperCase(java.util.Locale.ROOT).contains("PLAYER_HEAD")
                    && !name.toUpperCase(java.util.Locale.ROOT).contains("SKULL")) {
                return null;
            }
            Object meta = stack.getClass().getMethod("getItemMeta").invoke(stack);
            if (meta == null) {
                return null;
            }
            try {
                Object offline = meta.getClass().getMethod("getOwningPlayer").invoke(meta);
                if (offline != null) {
                    Object n = offline.getClass().getMethod("getName").invoke(offline);
                    if (n != null && !String.valueOf(n).isBlank()) {
                        return String.valueOf(n);
                    }
                }
            } catch (NoSuchMethodException ignored) {
            }
            try {
                Object profile = meta.getClass().getMethod("getOwnerProfile").invoke(meta);
                if (profile != null) {
                    Object n = profile.getClass().getMethod("getName").invoke(profile);
                    if (n != null && !String.valueOf(n).isBlank()) {
                        return String.valueOf(n);
                    }
                }
            } catch (NoSuchMethodException ignored) {
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    void injectBedrockPlayer(java.util.UUID uuid, String username) {
        backend.inventoryInject.inject(uuid, username);
    }

    void ejectBedrockPlayer(String username) {
        backend.inventoryInject.eject(username);
    }

    boolean clearInventory(String username) {
        backend.inventoryInject.clear(username);
        boolean live = backend.mainThread.runPlayerInv(username, (player, inv, cl) -> {
            inv.getClass().getMethod("clear").invoke(inv);
            return true;
        });
        return live || backend.inventoryInject.has(username);
    }

    boolean giveItem(String username, String materialName, int amount) {
        if (amount <= 0) {
            return false;
        }
        backend.inventoryInject.give(username, materialName, amount);
        boolean live = backend.mainThread.runPlayerInv(username, (player, inv, cl) -> {
            Class<?> matCl = Class.forName("org.bukkit.Material", true, cl);
            Object mat = matCl.getMethod("matchMaterial", String.class).invoke(null, materialName);
            if (mat == null) {
                mat = matCl.getMethod("valueOf", String.class).invoke(null,
                        materialName.toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
            }
            Class<?> stackCl = Class.forName("org.bukkit.inventory.ItemStack", true, cl);
            Object stack = stackCl.getConstructor(matCl, int.class).newInstance(mat, amount);
            Object[] arr = (Object[]) java.lang.reflect.Array.newInstance(stackCl, 1);
            arr[0] = stack;
            inv.getClass().getMethod("addItem", arr.getClass()).invoke(inv, (Object) arr);
            return true;
        });
        return live || backend.inventoryInject.has(username);
    }

    boolean setStorageSlot(String username, int slot, String materialName, int amount) {
        if (slot < 0 || slot >= 36) {
            return false;
        }
        backend.inventoryInject.setSlot(username, slot, materialName, amount);
        boolean live = backend.mainThread.runPlayerInv(username, (player, inv, cl) -> {
            Class<?> matCl = Class.forName("org.bukkit.Material", true, cl);
            Class<?> stackCl = Class.forName("org.bukkit.inventory.ItemStack", true, cl);
            Object stack;
            if (amount <= 0 || "AIR".equalsIgnoreCase(materialName)) {
                stack = null;
            } else {
                Object mat = matCl.getMethod("matchMaterial", String.class).invoke(null, materialName);
                if (mat == null) {
                    mat = matCl.getMethod("valueOf", String.class).invoke(null,
                            materialName.toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
                }
                stack = stackCl.getConstructor(matCl, int.class).newInstance(mat, amount);
            }
            inv.getClass().getMethod("setItem", int.class, stackCl).invoke(inv, slot, stack);
            return true;
        });
        return live || backend.inventoryInject.has(username);
    }

    boolean setHeldItemSlot(String username, int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot > 8) {
            return false;
        }
        backend.inventoryInject.setHeld(username, hotbarSlot);
        boolean live = backend.mainThread.runPlayerInv(username, (player, inv, cl) -> {
            inv.getClass().getMethod("setHeldItemSlot", int.class).invoke(inv, hotbarSlot);
            return true;
        });
        return live || backend.inventoryInject.has(username);
    }

    boolean setOffhand(String username, String materialName, int amount) {
        return backend.mainThread.runPlayerInv(username, (player, inv, cl) -> {
            Class<?> matCl = Class.forName("org.bukkit.Material", true, cl);
            Class<?> stackCl = Class.forName("org.bukkit.inventory.ItemStack", true, cl);
            Object stack = null;
            if (amount > 0 && materialName != null && !"AIR".equalsIgnoreCase(materialName)) {
                Object mat = matCl.getMethod("matchMaterial", String.class).invoke(null, materialName);
                if (mat == null) {
                    mat = matCl.getMethod("valueOf", String.class).invoke(null,
                            materialName.toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
                }
                stack = stackCl.getConstructor(matCl, int.class).newInstance(mat, amount);
            }
            inv.getClass().getMethod("setItemInOffHand", stackCl).invoke(inv, stack);
            return true;
        });
    }

    boolean setArmorSlot(String username, int armorIndex, String materialName, int amount) {
        if (armorIndex < 0 || armorIndex > 3) {
            return false;
        }
        String[] methods = {"setHelmet", "setChestplate", "setLeggings", "setBoots"};
        return backend.mainThread.runPlayerInv(username, (player, inv, cl) -> {
            Class<?> matCl = Class.forName("org.bukkit.Material", true, cl);
            Class<?> stackCl = Class.forName("org.bukkit.inventory.ItemStack", true, cl);
            Object stack = null;
            if (amount > 0 && materialName != null && !"AIR".equalsIgnoreCase(materialName)) {
                Object mat = matCl.getMethod("matchMaterial", String.class).invoke(null, materialName);
                if (mat == null) {
                    mat = matCl.getMethod("valueOf", String.class).invoke(null,
                            materialName.toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
                }
                stack = stackCl.getConstructor(matCl, int.class).newInstance(mat, amount);
            }
            inv.getClass().getMethod(methods[armorIndex], stackCl).invoke(inv, stack);
            return true;
        });
    }

    boolean setCraftSlot(String username, int slot, String materialName, int amount) {
        if (slot < 0 || slot >= 9) {
            return false;
        }
        return backend.mainThread.runPlayerInv(username, (player, inv, cl) -> {
            Object open = player.getClass().getMethod("getOpenInventory").invoke(player);
            if (open == null) {
                return false;
            }
            Object top;
            try {
                top = open.getClass().getMethod("getTopInventory").invoke(open);
            } catch (NoSuchMethodException e) {
                return false;
            }
            if (top == null) {
                return false;
            }
            int size = ((Number) top.getClass().getMethod("getSize").invoke(top)).intValue();
            if (slot >= size) {
                return false;
            }
            Class<?> matCl = Class.forName("org.bukkit.Material", true, cl);
            Class<?> stackCl = Class.forName("org.bukkit.inventory.ItemStack", true, cl);
            Object stack = null;
            if (amount > 0 && materialName != null && !"AIR".equalsIgnoreCase(materialName)) {
                Object mat = matCl.getMethod("matchMaterial", String.class).invoke(null, materialName);
                if (mat == null) {
                    mat = matCl.getMethod("valueOf", String.class).invoke(null,
                            materialName.toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
                }
                stack = stackCl.getConstructor(matCl, int.class).newInstance(mat, amount);
            }
            top.getClass().getMethod("setItem", int.class, stackCl).invoke(top, slot, stack);
            return true;
        });
    }

    static int itemStackToNetworkId(Object stack) {
        if (stack == null) {
            return 0;
        }
        try {
            Object type = stack.getClass().getMethod("getType").invoke(stack);
            if (type == null) {
                return 0;
            }
            String name = String.valueOf(type);
            int matDot = name.lastIndexOf('.');
            if (matDot >= 0) {
                name = name.substring(matDot + 1);
            }
            String key = "minecraft:" + name.toLowerCase(java.util.Locale.ROOT);
            for (BedrockItemStates.ItemState s : BedrockItemStates.all()) {
                if (s.name().equals(key) || s.name().equalsIgnoreCase(key)) {
                    return s.runtimeId() & 0xFFFF;
                }
            }
            if ("AIR".equalsIgnoreCase(name) || "CAVE_AIR".equalsIgnoreCase(name)) {
                return 0;
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    static int itemStackAmount(Object stack) {
        if (stack == null) {
            return 0;
        }
        try {
            Object amt = stack.getClass().getMethod("getAmount").invoke(stack);
            return amt instanceof Number n ? Math.max(0, n.intValue()) : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
