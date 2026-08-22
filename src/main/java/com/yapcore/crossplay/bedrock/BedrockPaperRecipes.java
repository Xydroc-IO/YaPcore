package com.yapcore.crossplay.bedrock;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Paper-backed JE recipe craft, villager trade execute, and enchant-table apply
 * for Bedrock stack-request / UI flows.
 */
public final class BedrockPaperRecipes {

    private static final Logger LOG = Logger.getLogger("YaPcore.BERecipes");

    public record EnchantOption(int netId, int cost, String primaryName, int enchantType, int enchantLevel) {
    }

    private final BedrockPaperWorldSync world;

    public BedrockPaperRecipes(BedrockPaperWorldSync world) {
        this.world = world;
    }

    /**
     * Match craft-grid materials against Bukkit shaped/shapeless recipes;
     * returns [resultNetworkId, resultCount] or null.
     */
    public int[] craftResultFromGrid(String[] materialNames, int[] counts) {
        return craftResultFromGrid(null, materialNames, counts);
    }

    public int[] craftResultFromGrid(String username, String[] materialNames, int[] counts) {
        if (world == null || !world.isEnabled() || materialNames == null) {
            return null;
        }
        try {
            ClassLoader cl = world.liveLoaderPublic();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Class<?> matCl = Class.forName("org.bukkit.Material", true, cl);
            Class<?> stackCl = Class.forName("org.bukkit.inventory.ItemStack", true, cl);
            Object[] matrix = new Object[9];
            for (int i = 0; i < 9; i++) {
                String m = i < materialNames.length ? materialNames[i] : null;
                int c = counts != null && i < counts.length ? counts[i] : 0;
                if (m == null || m.isBlank() || "AIR".equalsIgnoreCase(m) || c <= 0) {
                    matrix[i] = null;
                    continue;
                }
                Object mat = BedrockRecipeMatching.matchMaterial(matCl, m);
                if (mat == null) {
                    matrix[i] = null;
                    continue;
                }
                matrix[i] = stackCl.getConstructor(matCl, int.class).newInstance(mat, Math.max(1, c));
            }
            Object server = bukkit.getMethod("getServer").invoke(null);
            Object player = username != null ? world.findOnlinePlayer(username) : null;
            Object world0 = firstWorld(bukkit);
            try {
                Method craftItem = server.getClass().getMethod("craftItem",
                        stackCl.arrayType(),
                        Class.forName("org.bukkit.World", true, cl),
                        Class.forName("org.bukkit.entity.Player", true, cl));
                Object result = craftItem.invoke(server, matrix, world0, player);
                int[] net = stackToNetwork(result);
                if (net != null) {
                    return net;
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                LOG.log(Level.FINE, "Server.craftItem failed, trying iterator", e);
            }
            @SuppressWarnings("unchecked")
            Iterator<Object> it = (Iterator<Object>) bukkit.getMethod("recipeIterator").invoke(null);
            while (it.hasNext()) {
                Object recipe = it.next();
                if (recipe == null) {
                    continue;
                }
                if (BedrockRecipeMatching.matchesCraftingRecipe(recipe, matrix, matCl, stackCl, cl)) {
                    Object result = recipe.getClass().getMethod("getResult").invoke(recipe);
                    return stackToNetwork(result);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "craftResultFromGrid failed", e);
        }
        return null;
    }

    /**
     * Execute merchant recipe {@code index}: deduct buyA/buyB from player storage,
     * give sell result. Returns sell [networkId, count] or null.
     */
    public int[] executeTrade(String username, int recipeIndex) {
        if (world == null || !world.isEnabled() || username == null || recipeIndex < 0) {
            return null;
        }
        try {
            ClassLoader cl = world.liveLoaderPublic();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Object player = world.findOnlinePlayer(username);
            if (player == null) {
                return null;
            }
            Object open = player.getClass().getMethod("getOpenInventory").invoke(player);
            if (open == null) {
                return null;
            }
            Object top = open.getClass().getMethod("getTopInventory").invoke(open);
            Object holder = top != null ? top.getClass().getMethod("getHolder").invoke(top) : null;
            Class<?> merchantCl = Class.forName("org.bukkit.inventory.Merchant", true, cl);
            if (holder == null || !merchantCl.isInstance(holder)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            List<Object> recipes = (List<Object>) holder.getClass().getMethod("getRecipes").invoke(holder);
            if (recipes == null || recipeIndex >= recipes.size()) {
                return null;
            }
            Object recipe = recipes.get(recipeIndex);
            boolean uses = ((Number) recipe.getClass().getMethod("getUses").invoke(recipe)).intValue()
                    < ((Number) recipe.getClass().getMethod("getMaxUses").invoke(recipe)).intValue();
            if (!uses) {
                return null;
            }
            @SuppressWarnings("unchecked")
            List<Object> ingredients = (List<Object>) recipe.getClass().getMethod("getIngredients").invoke(recipe);
            Object result = recipe.getClass().getMethod("getResult").invoke(recipe);
            Object inv = player.getClass().getMethod("getInventory").invoke(player);
            Class<?> stackCl = Class.forName("org.bukkit.inventory.ItemStack", true, cl);
            if (ingredients != null) {
                for (Object ing : ingredients) {
                    if (ing == null) {
                        continue;
                    }
                    Object arr = java.lang.reflect.Array.newInstance(stackCl, 1);
                    java.lang.reflect.Array.set(arr, 0, ing);
                    Object leftover = inv.getClass().getMethod("removeItem", arr.getClass()).invoke(inv, arr);
                    if (leftover instanceof java.util.Map<?, ?> map && !map.isEmpty()) {
                        Object add = java.lang.reflect.Array.newInstance(stackCl, 1);
                        java.lang.reflect.Array.set(add, 0, ing);
                        inv.getClass().getMethod("addItem", add.getClass()).invoke(inv, add);
                        return null;
                    }
                }
            }
            if (result != null) {
                Object add = java.lang.reflect.Array.newInstance(stackCl, 1);
                java.lang.reflect.Array.set(add, 0, result);
                inv.getClass().getMethod("addItem", add.getClass()).invoke(inv, add);
            }
            int usesNow = ((Number) recipe.getClass().getMethod("getUses").invoke(recipe)).intValue();
            recipe.getClass().getMethod("setUses", int.class).invoke(recipe, usesNow + 1);
            try {
                player.getClass().getMethod("setExperience", int.class).invoke(player,
                        ((Number) player.getClass().getMethod("getTotalExperience").invoke(player)).intValue()
                                + Math.max(0, ((Number) recipe.getClass().getMethod("getVillagerExperience")
                                .invoke(recipe)).intValue()));
            } catch (NoSuchMethodException ignored) {
            }
            return stackToNetwork(result);
        } catch (Exception e) {
            LOG.log(Level.FINE, "executeTrade failed", e);
            return null;
        }
    }

    /**
     * Build up to 3 enchant options for the item currently in enchant slot 0.
     * Uses Paper EnchantingInventory offers when available; empty when none (fail closed).
     */
    public List<EnchantOption> enchantOptionsFor(String username) {
        List<EnchantOption> out = new ArrayList<>();
        if (world == null || !world.isEnabled()) {
            return out;
        }
        try {
            ClassLoader cl = world.liveLoaderPublic();
            Object player = world.findOnlinePlayer(username);
            if (player == null) {
                return out;
            }
            Object open = player.getClass().getMethod("getOpenInventory").invoke(player);
            if (open == null) {
                return out;
            }
            Object top = open.getClass().getMethod("getTopInventory").invoke(open);
            Class<?> enchInv = Class.forName("org.bukkit.inventory.EnchantingInventory", true, cl);
            if (top == null || !enchInv.isInstance(top)) {
                return out;
            }
            Object item = top.getClass().getMethod("getItem").invoke(top);
            if (item == null) {
                return out;
            }
            // Paper: getOffers() on EnchantmentView if present
            try {
                Object view = player.getClass().getMethod("getOpenInventory").invoke(player);
                Method getOffers = view.getClass().getMethod("getOffers");
                Object[] offers = (Object[]) getOffers.invoke(view);
                if (offers != null) {
                    for (int i = 0; i < offers.length && i < 3; i++) {
                        Object offer = offers[i];
                        if (offer == null) {
                            continue;
                        }
                        int cost = ((Number) offer.getClass().getMethod("getCost").invoke(offer)).intValue();
                        Object ench = offer.getClass().getMethod("getEnchantment").invoke(offer);
                        int level = ((Number) offer.getClass().getMethod("getEnchantmentLevel").invoke(offer)).intValue();
                        String name = ench != null ? String.valueOf(ench) : "enchant";
                        int typeId = Math.floorMod(name.hashCode(), 40);
                        out.add(new EnchantOption(i + 1, cost, name, typeId, level));
                    }
                    if (!out.isEmpty()) {
                        return out;
                    }
                }
            } catch (NoSuchMethodException ignored) {
            }
            // Fail closed — no Paper offers → empty (do not ship fake Protection/etc.)
            return out;
        } catch (Exception e) {
            LOG.log(Level.FINE, "enchantOptionsFor failed", e);
            return out;
        }
    }

    /**
     * Apply enchant option {@code netId} (1..3) to the item in the enchant table;
     * consumes lapis and XP on Paper when possible.
     */
    public int[] applyEnchantOption(String username, int netId) {
        if (world == null || !world.isEnabled() || netId <= 0) {
            return null;
        }
        List<EnchantOption> opts = enchantOptionsFor(username);
        EnchantOption chosen = null;
        for (EnchantOption o : opts) {
            if (o.netId() == netId) {
                chosen = o;
                break;
            }
        }
        if (chosen == null && netId >= 1 && netId <= opts.size()) {
            chosen = opts.get(netId - 1);
        }
        if (chosen == null) {
            return null;
        }
        try {
            ClassLoader cl = world.liveLoaderPublic();
            Object player = world.findOnlinePlayer(username);
            if (player == null) {
                return null;
            }
            Object open = player.getClass().getMethod("getOpenInventory").invoke(player);
            Object top = open.getClass().getMethod("getTopInventory").invoke(open);
            Class<?> enchInv = Class.forName("org.bukkit.inventory.EnchantingInventory", true, cl);
            if (top == null || !enchInv.isInstance(top)) {
                return null;
            }
            Object item = top.getClass().getMethod("getItem").invoke(top);
            if (item == null) {
                return null;
            }
            Class<?> enchCl = Class.forName("org.bukkit.enchantments.Enchantment", true, cl);
            Object ench = null;
            try {
                ench = enchCl.getMethod("getByKey", Class.forName("org.bukkit.NamespacedKey", true, cl))
                        .invoke(null, parseKey(cl, chosen.primaryName()));
            } catch (Exception ignored) {
            }
            if (ench == null) {
                try {
                    ench = enchCl.getMethod("getByName", String.class).invoke(null,
                            chosen.primaryName().toUpperCase(Locale.ROOT).replace(' ', '_'));
                } catch (Exception ignored) {
                }
            }
            if (ench == null) {
                // fallback: first enchantment in registry
                Object[] values = (Object[]) enchCl.getMethod("values").invoke(null);
                if (values != null && values.length > 0) {
                    ench = values[Math.floorMod(chosen.enchantType(), values.length)];
                }
            }
            if (ench != null) {
                item.getClass().getMethod("addUnsafeEnchantment", enchCl, int.class)
                        .invoke(item, ench, Math.max(1, chosen.enchantLevel()));
            }
            // consume lapis (slot secondary)
            try {
                Object lapis = top.getClass().getMethod("getSecondary").invoke(top);
                if (lapis != null) {
                    int amt = ((Number) lapis.getClass().getMethod("getAmount").invoke(lapis)).intValue();
                    int cost = Math.max(1, Math.min(3, chosen.cost()));
                    if (amt > cost) {
                        lapis.getClass().getMethod("setAmount", int.class).invoke(lapis, amt - cost);
                        top.getClass().getMethod("setSecondary",
                                        Class.forName("org.bukkit.inventory.ItemStack", true, cl))
                                .invoke(top, lapis);
                    } else {
                        top.getClass().getMethod("setSecondary",
                                        Class.forName("org.bukkit.inventory.ItemStack", true, cl))
                                .invoke(top, new Object[]{null});
                    }
                }
            } catch (NoSuchMethodException ignored) {
            }
            try {
                int lvl = ((Number) player.getClass().getMethod("getLevel").invoke(player)).intValue();
                player.getClass().getMethod("setLevel", int.class)
                        .invoke(player, Math.max(0, lvl - chosen.cost()));
            } catch (NoSuchMethodException ignored) {
            }
            top.getClass().getMethod("setItem", Class.forName("org.bukkit.inventory.ItemStack", true, cl))
                    .invoke(top, item);
            return stackToNetwork(item);
        } catch (Exception e) {
            LOG.log(Level.FINE, "applyEnchantOption failed", e);
            return null;
        }
    }

    /** Offline / no-Paper soak stand-in — not used when Paper enchant view is available. */
    public static List<EnchantOption> soakHeuristicOptions() {
        List<EnchantOption> out = new ArrayList<>();
        out.add(new EnchantOption(1, 1, "PROTECTION", 0, 1));
        out.add(new EnchantOption(2, 5, "UNBREAKING", 17, 1));
        out.add(new EnchantOption(3, 10, "EFFICIENCY", 15, 2));
        return out;
    }

    private static Object parseKey(ClassLoader cl, String name) throws Exception {
        String key = name == null ? "protection" : name.toLowerCase(Locale.ROOT)
                .replace("minecraft:", "").replace(' ', '_');
        Class<?> nk = Class.forName("org.bukkit.NamespacedKey", true, cl);
        return nk.getMethod("minecraft", String.class).invoke(null, key);
    }

    private static Object firstWorld(Class<?> bukkit) throws Exception {
        @SuppressWarnings("unchecked")
        List<Object> worlds = (List<Object>) bukkit.getMethod("getWorlds").invoke(null);
        return worlds == null || worlds.isEmpty() ? null : worlds.get(0);
    }

    private static int[] stackToNetwork(Object stack) throws Exception {
        if (stack == null) {
            return null;
        }
        Object type = stack.getClass().getMethod("getType").invoke(stack);
        int amount = ((Number) stack.getClass().getMethod("getAmount").invoke(stack)).intValue();
        String mat = type == null ? "air" : String.valueOf(type).toLowerCase(Locale.ROOT)
                .replace("minecraft:", "");
        if ("air".equals(mat)) {
            return null;
        }
        int nid = 0;
        for (BedrockItemStates.ItemState s : BedrockItemStates.all()) {
            if (s.name().equals("minecraft:" + mat) || s.name().endsWith(":" + mat)) {
                nid = s.runtimeId() & 0xFFFF;
                break;
            }
        }
        if (nid == 0) {
            return null;
        }
        return new int[]{nid, Math.max(1, amount)};
    }
}
