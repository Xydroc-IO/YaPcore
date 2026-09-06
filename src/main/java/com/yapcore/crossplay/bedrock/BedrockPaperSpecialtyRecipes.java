package com.yapcore.crossplay.bedrock;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stonecutter / smithing / loom / cartography specialty picks for Bedrock UI.
 */
final class BedrockPaperSpecialtyRecipes {

    private static final Logger LOG = Logger.getLogger("YaPcore.BERecipes");

    private final BedrockPaperWorldSync world;

    BedrockPaperSpecialtyRecipes(BedrockPaperWorldSync world) {
        this.world = world;
    }

    int[] applySpecialtyPick(String username, int containerType, int netId) {
        if (world == null || !world.isEnabled() || username == null) {
            return null;
        }
        return switch (containerType) {
            case BedrockContainerBridge.TYPE_STONECUTTER -> applyStonecutterPick(username, netId);
            case BedrockContainerBridge.TYPE_SMITHING -> applySmithingPick(username, netId);
            case BedrockContainerBridge.TYPE_LOOM -> applyLoomPick(username, netId);
            case BedrockContainerBridge.TYPE_CARTOGRAPHY -> applyCartographyPick(username, netId);
            default -> null;
        };
    }

    /** Result slot index within the virtual container window. */
    static int specialtyResultSlot(int containerType) {
        return switch (containerType) {
            case BedrockContainerBridge.TYPE_STONECUTTER -> 1;
            case BedrockContainerBridge.TYPE_CARTOGRAPHY -> 2;
            case BedrockContainerBridge.TYPE_LOOM, BedrockContainerBridge.TYPE_SMITHING -> 3;
            default -> -1;
        };
    }

    private int[] applyStonecutterPick(String username, int netId) {
        try {
            ClassLoader cl = world.liveLoaderPublic();
            Object player = world.findOnlinePlayer(username);
            if (player == null) {
                return null;
            }
            Object open = player.getClass().getMethod("getOpenInventory").invoke(player);
            Object top = open != null ? open.getClass().getMethod("getTopInventory").invoke(open) : null;
            Class<?> stoneInv = Class.forName("org.bukkit.inventory.StonecutterInventory", true, cl);
            if (top == null || !stoneInv.isInstance(top)) {
                return null;
            }
            Object input = top.getClass().getMethod("getInputItem").invoke(top);
            if (input == null) {
                try {
                    input = top.getClass().getMethod("getItem", int.class).invoke(top, 0);
                } catch (NoSuchMethodException ignored) {
                }
            }
            if (input == null) {
                return null;
            }
            Object result = pickCuttingResult(cl, input, netId);
            if (result == null) {
                return null;
            }
            try {
                top.getClass().getMethod("setResult",
                                Class.forName("org.bukkit.inventory.ItemStack", true, cl))
                        .invoke(top, result);
            } catch (NoSuchMethodException e) {
                top.getClass().getMethod("setItem", int.class,
                                Class.forName("org.bukkit.inventory.ItemStack", true, cl))
                        .invoke(top, 1, result);
            }
            trySetRecipeIndex(open, netId);
            return BedrockPaperRecipes.stackToNetwork(result);
        } catch (Exception e) {
            LOG.log(Level.FINE, "applyStonecutterPick failed", e);
            return null;
        }
    }

    private Object pickCuttingResult(ClassLoader cl, Object input, int netId) throws Exception {
        Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
        Class<?> cuttingCl = Class.forName("org.bukkit.inventory.StonecuttingRecipe", true, cl);
        List<Object> matches = new ArrayList<>();
        @SuppressWarnings("unchecked")
        Iterator<Object> it = (Iterator<Object>) bukkit.getMethod("recipeIterator").invoke(null);
        while (it.hasNext()) {
            Object recipe = it.next();
            if (recipe == null || !cuttingCl.isInstance(recipe)) {
                continue;
            }
            Object recipeInput = recipe.getClass().getMethod("getInput").invoke(recipe);
            if (stacksMatch(input, recipeInput)) {
                matches.add(recipe);
            }
        }
        if (matches.isEmpty()) {
            return null;
        }
        int idx = recipeIndex(netId, matches.size());
        Object chosen = matches.get(idx);
        return chosen.getClass().getMethod("getResult").invoke(chosen);
    }

    private int[] applySmithingPick(String username, int netId) {
        try {
            ClassLoader cl = world.liveLoaderPublic();
            Object player = world.findOnlinePlayer(username);
            if (player == null) {
                return null;
            }
            Object open = player.getClass().getMethod("getOpenInventory").invoke(player);
            Object top = open != null ? open.getClass().getMethod("getTopInventory").invoke(open) : null;
            Class<?> smithInv = Class.forName("org.bukkit.inventory.SmithingInventory", true, cl);
            if (top == null || !smithInv.isInstance(top)) {
                return null;
            }
            Object result = top.getClass().getMethod("getResult").invoke(top);
            if (result == null) {
                // Force recipe match from inputs when Paper has not filled result yet
                result = matchSmithingResult(cl, top, netId);
                if (result != null) {
                    top.getClass().getMethod("setResult",
                                    Class.forName("org.bukkit.inventory.ItemStack", true, cl))
                            .invoke(top, result);
                }
            }
            trySetRecipeIndex(open, netId);
            return BedrockPaperRecipes.stackToNetwork(result);
        } catch (Exception e) {
            LOG.log(Level.FINE, "applySmithingPick failed", e);
            return null;
        }
    }

    private Object matchSmithingResult(ClassLoader cl, Object top, int netId) throws Exception {
        Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
        Class<?> smithRecipe = Class.forName("org.bukkit.inventory.SmithingRecipe", true, cl);
        List<Object> matches = new ArrayList<>();
        @SuppressWarnings("unchecked")
        Iterator<Object> it = (Iterator<Object>) bukkit.getMethod("recipeIterator").invoke(null);
        while (it.hasNext()) {
            Object recipe = it.next();
            if (recipe != null && smithRecipe.isInstance(recipe)) {
                matches.add(recipe);
            }
        }
        if (matches.isEmpty()) {
            return null;
        }
        Object chosen = matches.get(recipeIndex(netId, matches.size()));
        return chosen.getClass().getMethod("getResult").invoke(chosen);
    }

    private int[] applyLoomPick(String username, int netId) {
        try {
            ClassLoader cl = world.liveLoaderPublic();
            Object player = world.findOnlinePlayer(username);
            if (player == null) {
                return null;
            }
            Object open = player.getClass().getMethod("getOpenInventory").invoke(player);
            Object top = open != null ? open.getClass().getMethod("getTopInventory").invoke(open) : null;
            if (top == null) {
                return null;
            }
            // LoomInventory — result typically slot 3; Paper may already compute when pattern selected
            trySetRecipeIndex(open, netId);
            Object result = null;
            try {
                result = top.getClass().getMethod("getItem", int.class).invoke(top, 3);
            } catch (NoSuchMethodException ignored) {
            }
            if (result == null) {
                result = computeLoomResult(cl, top, netId);
                if (result != null) {
                    top.getClass().getMethod("setItem", int.class,
                                    Class.forName("org.bukkit.inventory.ItemStack", true, cl))
                            .invoke(top, 3, result);
                }
            }
            return BedrockPaperRecipes.stackToNetwork(result);
        } catch (Exception e) {
            LOG.log(Level.FINE, "applyLoomPick failed", e);
            return null;
        }
    }

    private Object computeLoomResult(ClassLoader cl, Object top, int netId) throws Exception {
        Object banner = top.getClass().getMethod("getItem", int.class).invoke(top, 0);
        Object dye = top.getClass().getMethod("getItem", int.class).invoke(top, 1);
        if (banner == null || dye == null) {
            return null;
        }
        // Clone banner and apply first available pattern dye via BannerMeta when present
        Object clone = banner.getClass().getMethod("clone").invoke(banner);
        Object meta = clone.getClass().getMethod("getItemMeta").invoke(clone);
        if (meta == null) {
            return clone;
        }
        Class<?> bannerMeta = Class.forName("org.bukkit.inventory.meta.BannerMeta", true, cl);
        if (!bannerMeta.isInstance(meta)) {
            return clone;
        }
        try {
            Class<?> patternType = Class.forName("org.bukkit.block.banner.PatternType", true, cl);
            Object[] values = (Object[]) patternType.getMethod("values").invoke(null);
            if (values == null || values.length == 0) {
                return clone;
            }
            Object pattern = values[recipeIndex(netId, values.length)];
            Object dyeType = dye.getClass().getMethod("getType").invoke(dye);
            Class<?> dyeColor = Class.forName("org.bukkit.DyeColor", true, cl);
            Object color = dyeColor.getMethod("getByMaterial",
                    Class.forName("org.bukkit.Material", true, cl)).invoke(null, dyeType);
            if (color == null) {
                Object[] colors = (Object[]) dyeColor.getMethod("values").invoke(null);
                color = colors[0];
            }
            Class<?> patternCl = Class.forName("org.bukkit.block.banner.Pattern", true, cl);
            Object pat = patternCl.getConstructor(dyeColor, patternType).newInstance(color, pattern);
            meta.getClass().getMethod("addPattern", patternCl).invoke(meta, pat);
            clone.getClass().getMethod("setItemMeta",
                            Class.forName("org.bukkit.inventory.meta.ItemMeta", true, cl))
                    .invoke(clone, meta);
        } catch (Exception e) {
            LOG.log(Level.FINE, "loom pattern apply best-effort failed", e);
        }
        return clone;
    }

    private int[] applyCartographyPick(String username, int netId) {
        try {
            ClassLoader cl = world.liveLoaderPublic();
            Object player = world.findOnlinePlayer(username);
            if (player == null) {
                return null;
            }
            Object open = player.getClass().getMethod("getOpenInventory").invoke(player);
            Object top = open != null ? open.getClass().getMethod("getTopInventory").invoke(open) : null;
            if (top == null) {
                return null;
            }
            trySetRecipeIndex(open, netId);
            Object result = null;
            try {
                result = top.getClass().getMethod("getItem", int.class).invoke(top, 2);
            } catch (NoSuchMethodException ignored) {
            }
            if (result == null) {
                // Clone map from slot 0 when paper present (clone / lock style)
                Object map = top.getClass().getMethod("getItem", int.class).invoke(top, 0);
                Object paper = top.getClass().getMethod("getItem", int.class).invoke(top, 1);
                if (map != null && paper != null) {
                    result = map.getClass().getMethod("clone").invoke(map);
                    top.getClass().getMethod("setItem", int.class,
                                    Class.forName("org.bukkit.inventory.ItemStack", true, cl))
                            .invoke(top, 2, result);
                }
            }
            return BedrockPaperRecipes.stackToNetwork(result);
        } catch (Exception e) {
            LOG.log(Level.FINE, "applyCartographyPick failed", e);
            return null;
        }
    }

    private static void trySetRecipeIndex(Object openView, int netId) {
        if (openView == null) {
            return;
        }
        try {
            openView.getClass().getMethod("setRecipeIndex", int.class)
                    .invoke(openView, Math.max(0, netId <= 0 ? 0 : netId - 1));
        } catch (Exception ignored) {
            try {
                openView.getClass().getMethod("setSelectedRecipeIndex", int.class)
                        .invoke(openView, Math.max(0, netId <= 0 ? 0 : netId - 1));
            } catch (Exception ignored2) {
            }
        }
    }

    private static int recipeIndex(int netId, int size) {
        if (size <= 0) {
            return 0;
        }
        if (netId <= 0) {
            return 0;
        }
        // Accept 0-based or 1-based client ids
        int zeroBased = netId >= size ? netId - 1 : netId;
        if (zeroBased < 0) {
            zeroBased = 0;
        }
        if (zeroBased >= size) {
            zeroBased = size - 1;
        }
        return zeroBased;
    }

    private static boolean stacksMatch(Object a, Object b) throws Exception {
        if (a == null || b == null) {
            return false;
        }
        Object ta = a.getClass().getMethod("getType").invoke(a);
        Object tb = b.getClass().getMethod("getType").invoke(b);
        return ta != null && ta.equals(tb);
    }
}
