package com.yapcore.crossplay.bedrock.inventory;

import com.yapcore.crossplay.bedrock.BedrockContainerBridge;
import com.yapcore.crossplay.bedrock.BedrockInventoryAuthority.Slot;
import com.yapcore.crossplay.bedrock.BedrockPacketCodec;
import com.yapcore.crossplay.bedrock.BedrockPaperRecipes;

import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/** Craft-grid, enchant, and villager trade execution for inventory stack requests. */
public final class InventoryCraftTrade {

    private InventoryCraftTrade() {
    }

    public static void maybeExecuteTradeOnResultTake(
            Map<String, Slot[]> byUser,
            BedrockContainerBridge bridge,
            BedrockPaperRecipes recipes,
            String username,
            List<BedrockPacketCodec.StackAction> actions,
            IntUnaryOperator resolvePacked) {
        if (bridge == null || recipes == null) {
            return;
        }
        BedrockContainerBridge.OpenWindow w = bridge.current(username);
        if (w == null || w.type() != BedrockContainerBridge.TYPE_VILLAGER) {
            return;
        }
        Slot[] slots = byUser.get(username.toLowerCase());
        for (BedrockPacketCodec.StackAction a : actions) {
            if (a == null || a.type() != BedrockPacketCodec.StackActionType.TAKE) {
                continue;
            }
            int from = resolvePacked.applyAsInt(a.sourceSlot());
            if (from == BedrockInventoryLayout.CONTAINER_BASE + 2) {
                int idx = 0;
                for (BedrockPacketCodec.StackAction b : actions) {
                    if (b != null && b.type() == BedrockPacketCodec.StackActionType.CRAFT_RECIPE_OPTIONAL) {
                        idx = Math.max(0, b.creativeNetworkId());
                    }
                }
                int[] sell = recipes.executeTrade(username, idx);
                if (sell != null && sell[0] > 0) {
                    InventorySlotOps.setSlot(byUser, username, BedrockInventoryLayout.CURSOR,
                            new Slot(sell[0], sell[1]));
                    InventorySlotOps.setSlot(byUser, username, BedrockInventoryLayout.CONTAINER_BASE, Slot.AIR);
                    InventorySlotOps.setSlot(byUser, username, BedrockInventoryLayout.CONTAINER_BASE + 1, Slot.AIR);
                    InventorySlotOps.setSlot(byUser, username, BedrockInventoryLayout.CONTAINER_BASE + 2, Slot.AIR);
                }
            }
        }
    }

    public static boolean applyCraftRecipe(Map<String, Slot[]> byUser, BedrockPaperRecipes recipes,
                                           String username, int recipeOrResultNetId, int times) {
        Slot[] slots = byUser.get(username.toLowerCase());
        String[] mats = new String[BedrockInventoryLayout.CRAFT_SLOTS];
        int[] counts = new int[BedrockInventoryLayout.CRAFT_SLOTS];
        boolean anyInput = false;
        for (int i = 0; i < BedrockInventoryLayout.CRAFT_SLOTS; i++) {
            Slot s = slots[BedrockInventoryLayout.CRAFT_BASE + i];
            if (!s.isEmpty()) {
                anyInput = true;
                mats[i] = InventoryPaperMirror.materialForNetworkId(s.networkId());
                counts[i] = s.count();
            }
        }
        int put = Math.max(1, times);
        int resultId = 0;
        int resultCount = put;
        if (recipes != null && anyInput) {
            int[] resolved = recipes.craftResultFromGrid(username, mats, counts);
            if (resolved != null && resolved[0] > 0) {
                resultId = resolved[0];
                resultCount = Math.max(1, resolved[1]) * put;
            }
        }
        if (resultId <= 0 && !anyInput && recipeOrResultNetId > 0) {
            // Fail closed: recipe net-ids are not item runtime ids.
            return false;
        }
        if (resultId <= 0) {
            return false;
        }
        for (int i = 0; i < BedrockInventoryLayout.CRAFT_SLOTS; i++) {
            Slot s = slots[BedrockInventoryLayout.CRAFT_BASE + i];
            if (!s.isEmpty()) {
                slots[BedrockInventoryLayout.CRAFT_BASE + i] = s.withCount(s.count() - put);
            }
        }
        InventorySlotOps.setSlot(byUser, username, BedrockInventoryLayout.CRAFT_RESULT,
                new Slot(resultId, resultCount));
        InventorySlotOps.setSlot(byUser, username, BedrockInventoryLayout.CURSOR,
                new Slot(resultId, resultCount));
        return true;
    }

    public static boolean applyOptionalCraft(Map<String, Slot[]> byUser,
                                             BedrockContainerBridge bridge,
                                             BedrockPaperRecipes recipes,
                                             String username, int netId) {
        if (bridge == null || recipes == null) {
            return false;
        }
        BedrockContainerBridge.OpenWindow w = bridge.current(username);
        if (w == null) {
            return false;
        }
        if (w.type() == BedrockContainerBridge.TYPE_ENCHANT) {
            int[] result = recipes.applyEnchantOption(username, netId);
            if (result != null && result[0] > 0) {
                InventorySlotOps.setSlot(byUser, username, BedrockInventoryLayout.CONTAINER_BASE,
                        new Slot(result[0], result[1]));
                return true;
            }
            return false;
        }
        if (w.type() == BedrockContainerBridge.TYPE_VILLAGER) {
            int[] sell = recipes.executeTrade(username, Math.max(0, netId - 1));
            if (sell != null && sell[0] > 0) {
                InventorySlotOps.setSlot(byUser, username, BedrockInventoryLayout.CONTAINER_BASE + 2,
                        new Slot(sell[0], sell[1]));
                InventorySlotOps.setSlot(byUser, username, BedrockInventoryLayout.CURSOR,
                        new Slot(sell[0], sell[1]));
                return true;
            }
            return false;
        }
        int resultSlot = BedrockPaperRecipes.specialtyResultSlot(w.type());
        if (resultSlot < 0) {
            return false;
        }
        int[] result = recipes.applySpecialtyPick(username, w.type(), netId);
        if (result == null || result[0] <= 0) {
            return false;
        }
        InventorySlotOps.setSlot(byUser, username, BedrockInventoryLayout.CONTAINER_BASE + resultSlot,
                new Slot(result[0], result[1]));
        InventorySlotOps.setSlot(byUser, username, BedrockInventoryLayout.CURSOR,
                new Slot(result[0], result[1]));
        return true;
    }
}
