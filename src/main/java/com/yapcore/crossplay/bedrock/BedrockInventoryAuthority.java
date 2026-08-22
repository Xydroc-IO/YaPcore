package com.yapcore.crossplay.bedrock;

import com.yapcore.crossplay.bedrock.inventory.BedrockInventoryLayout;
import com.yapcore.crossplay.bedrock.inventory.InventoryCraftTrade;
import com.yapcore.crossplay.bedrock.inventory.InventoryPaperMirror;
import com.yapcore.crossplay.bedrock.inventory.InventorySlotOps;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntUnaryOperator;

/**
 * Server-side Bedrock inventory authority: shadow 36 storage + cursor/offhand +
 * open-window container slots, mirrored onto Paper when a live player / block inv exists.
 */
public final class BedrockInventoryAuthority {

    public static final int SLOTS = BedrockInventoryLayout.SLOTS;
    public static final int CURSOR = BedrockInventoryLayout.CURSOR;
    public static final int OFFHAND = BedrockInventoryLayout.OFFHAND;
    /** Armor: helmet, chest, legs, boots (Bedrock container id 6). */
    public static final int ARMOR_BASE = BedrockInventoryLayout.ARMOR_BASE;
    public static final int ARMOR_SLOTS = BedrockInventoryLayout.ARMOR_SLOTS;
    /** Player/workbench crafting grid (Bedrock container id 13). */
    public static final int CRAFT_BASE = BedrockInventoryLayout.CRAFT_BASE;
    public static final int CRAFT_SLOTS = BedrockInventoryLayout.CRAFT_SLOTS;
    public static final int CRAFT_RESULT = BedrockInventoryLayout.CRAFT_RESULT;
    /** First index of open-window container shadow (chest 27 / furnace 3 / …). */
    public static final int CONTAINER_BASE = BedrockInventoryLayout.CONTAINER_BASE;
    public static final int CONTAINER_MAX = BedrockInventoryLayout.CONTAINER_MAX;
    private static final int TOTAL = BedrockInventoryLayout.TOTAL;

    /** Packed slot from codec: (containerId &lt;&lt; 16) | slot. */
    public static int unpackContainerId(int packed) {
        return (packed >>> 16) & 0xffff;
    }

    public static int unpackSlot(int packed) {
        return packed & 0xffff;
    }

    public record Slot(int networkId, int count) {
        public static final Slot AIR = new Slot(0, 0);

        public boolean isEmpty() {
            return networkId == 0 || count <= 0;
        }

        public Slot withCount(int c) {
            if (c <= 0 || networkId == 0) {
                return AIR;
            }
            return new Slot(networkId, c);
        }
    }

    private final ConcurrentHashMap<String, Slot[]> byUser = new ConcurrentHashMap<>();
    private volatile BedrockPaperWorldSync paperWorld;
    private volatile BedrockContainerBridge containers;
    private volatile BedrockPaperRecipes recipes;

    public void attachPaper(BedrockPaperWorldSync paperWorld) {
        this.paperWorld = paperWorld;
        this.recipes = paperWorld != null ? new BedrockPaperRecipes(paperWorld) : null;
    }

    public void attachContainers(BedrockContainerBridge containers) {
        this.containers = containers;
    }

    public void ensure(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        byUser.computeIfAbsent(username.toLowerCase(), u -> emptySlots());
    }

    public void clear(String username) {
        ensure(username);
        Slot[] slots = byUser.get(username.toLowerCase());
        Arrays.fill(slots, 0, SLOTS, Slot.AIR);
        InventoryPaperMirror.mirrorClearToPaper(paperWorld, username);
    }

    /** Clear matching network-id stacks from storage only (G.28 /clear &lt;item&gt;). */
    public void clearItem(String username, int networkId) {
        if (networkId <= 0) {
            clear(username);
            return;
        }
        ensure(username);
        Slot[] slots = byUser.get(username.toLowerCase());
        for (int i = 0; i < SLOTS; i++) {
            if (!slots[i].isEmpty() && slots[i].networkId() == networkId) {
                slots[i] = Slot.AIR;
            }
        }
        InventoryPaperMirror.mirrorStorageToPaper(paperWorld, byUser, username);
    }

    public void give(String username, int networkId, int count) {
        if (networkId <= 0 || count <= 0) {
            return;
        }
        ensure(username);
        Slot[] slots = byUser.get(username.toLowerCase());
        int remaining = count;
        for (int i = 0; i < SLOTS && remaining > 0; i++) {
            if (slots[i].isEmpty()) {
                int put = Math.min(64, remaining);
                slots[i] = new Slot(networkId, put);
                remaining -= put;
            } else if (slots[i].networkId() == networkId && slots[i].count() < 64) {
                int room = 64 - slots[i].count();
                int put = Math.min(room, remaining);
                slots[i] = slots[i].withCount(slots[i].count() + put);
                remaining -= put;
            }
        }
        InventoryPaperMirror.mirrorGiveToPaper(paperWorld, username, networkId, count - remaining);
    }

    public int[] storageNetworkIds(String username) {
        ensure(username);
        Slot[] slots = byUser.get(username.toLowerCase());
        int[] out = new int[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            out[i] = slots[i].isEmpty() ? 0 : slots[i].networkId();
        }
        return out;
    }

    public int[] storageCounts(String username) {
        ensure(username);
        Slot[] slots = byUser.get(username.toLowerCase());
        int[] out = new int[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            out[i] = slots[i].isEmpty() ? 0 : slots[i].count();
        }
        return out;
    }

    /** Seed player storage from Paper/live snapshot. */
    public void seedStorage(String username, int[] networkIds, int[] counts) {
        if (username == null || networkIds == null) {
            return;
        }
        ensure(username);
        Slot[] slots = byUser.get(username.toLowerCase());
        int n = Math.min(SLOTS, networkIds.length);
        for (int i = 0; i < n; i++) {
            int id = networkIds[i];
            int c = counts != null && i < counts.length ? counts[i] : (id > 0 ? 1 : 0);
            if (id <= 0 || c <= 0) {
                slots[i] = Slot.AIR;
            } else {
                slots[i] = new Slot(id, Math.min(64, c));
            }
        }
    }

    /** Seed open-window container region (clears previous). */
    public void seedContainer(String username, int[] networkIds, int[] counts) {
        ensure(username);
        Slot[] slots = byUser.get(username.toLowerCase());
        for (int i = CONTAINER_BASE; i < TOTAL; i++) {
            slots[i] = Slot.AIR;
        }
        if (networkIds == null) {
            return;
        }
        int n = Math.min(CONTAINER_MAX, networkIds.length);
        for (int i = 0; i < n; i++) {
            int id = networkIds[i];
            int c = counts != null && i < counts.length ? counts[i] : (id > 0 ? 1 : 0);
            if (id <= 0 || c <= 0) {
                slots[CONTAINER_BASE + i] = Slot.AIR;
            } else {
                slots[CONTAINER_BASE + i] = new Slot(id, Math.min(64, c));
            }
        }
    }

    public void clearContainer(String username) {
        if (username == null || !byUser.containsKey(username.toLowerCase())) {
            return;
        }
        Slot[] slots = byUser.get(username.toLowerCase());
        for (int i = CONTAINER_BASE; i < TOTAL; i++) {
            slots[i] = Slot.AIR;
        }
    }

    /** Snapshot open-window container shadow for BE inventory_content. */
    public int[][] containerSnapshot(String username, int slots) {
        ensure(username);
        int n = Math.max(0, Math.min(CONTAINER_MAX, slots));
        Slot[] all = byUser.get(username.toLowerCase());
        int[] ids = new int[n];
        int[] counts = new int[n];
        for (int i = 0; i < n; i++) {
            Slot s = all[CONTAINER_BASE + i];
            if (!s.isEmpty()) {
                ids[i] = s.networkId();
                counts[i] = s.count();
            }
        }
        return new int[][]{ids, counts};
    }

    /**
     * Apply stack-request actions. Source/dest are packed (containerId&lt;&lt;16)|slot
     * from the codec (legacy absolute indices still accepted if &lt; TOTAL).
     */
    public boolean applyActions(String username, List<BedrockPacketCodec.StackAction> actions) {
        ensure(username);
        if (actions == null || actions.isEmpty()) {
            return false;
        }
        boolean mutated = false;
        boolean touchedContainer = false;
        boolean touchedArmorOrCraft = false;
        for (BedrockPacketCodec.StackAction a : actions) {
            if (a == null) {
                continue;
            }
            int from = resolvePacked(username, a.sourceSlot());
            int to = resolvePacked(username, a.destSlot());
            switch (a.type()) {
                case TAKE, PLACE -> {
                    if (InventorySlotOps.movePartial(byUser, username, from, to, a.count())) {
                        mutated = true;
                        if (InventorySlotOps.isContainerIndex(from) || InventorySlotOps.isContainerIndex(to)) {
                            touchedContainer = true;
                        }
                        if (InventorySlotOps.isArmorOrCraft(from) || InventorySlotOps.isArmorOrCraft(to)) {
                            touchedArmorOrCraft = true;
                        }
                    }
                }
                case SWAP -> {
                    if (InventorySlotOps.swap(byUser, username, from, to)) {
                        mutated = true;
                        if (InventorySlotOps.isContainerIndex(from) || InventorySlotOps.isContainerIndex(to)) {
                            touchedContainer = true;
                        }
                        if (InventorySlotOps.isArmorOrCraft(from) || InventorySlotOps.isArmorOrCraft(to)) {
                            touchedArmorOrCraft = true;
                        }
                    }
                }
                case DROP, DESTROY, CONSUME -> {
                    if (InventorySlotOps.consume(byUser, username, from, a.count())) {
                        mutated = true;
                        if (InventorySlotOps.isContainerIndex(from)) {
                            touchedContainer = true;
                        }
                        if (InventorySlotOps.isArmorOrCraft(from)) {
                            touchedArmorOrCraft = true;
                        }
                    }
                }
                case CREATE -> {
                    if (a.creativeNetworkId() > 0) {
                        int dest = to >= 0 ? to : CURSOR;
                        InventorySlotOps.setSlot(byUser, username, dest,
                                new Slot(a.creativeNetworkId(), Math.max(1, a.count())));
                        mutated = true;
                        if (InventorySlotOps.isArmorOrCraft(dest)) {
                            touchedArmorOrCraft = true;
                        }
                    }
                }
                case CRAFT_RECIPE, CRAFT_RECIPE_AUTO -> {
                    if (InventoryCraftTrade.applyCraftRecipe(byUser, recipes, username,
                            a.creativeNetworkId(), Math.max(1, a.count()))) {
                        mutated = true;
                        touchedArmorOrCraft = true;
                    }
                }
                case CRAFT_RECIPE_OPTIONAL -> {
                    if (InventoryCraftTrade.applyOptionalCraft(byUser, containers, recipes, username,
                            a.creativeNetworkId())) {
                        mutated = true;
                        touchedContainer = true;
                        touchedArmorOrCraft = true;
                    }
                }
                case CRAFT_CREATIVE -> {
                    if (a.creativeNetworkId() > 0) {
                        InventorySlotOps.setSlot(byUser, username, CURSOR,
                                new Slot(a.creativeNetworkId(), Math.max(1, a.count())));
                        mutated = true;
                    }
                }
                default -> {
                }
            }
        }
        if (mutated) {
            InventoryPaperMirror.mirrorStorageToPaper(paperWorld, byUser, username);
            if (touchedArmorOrCraft) {
                InventoryPaperMirror.mirrorArmorCraftToPaper(paperWorld, byUser, username);
            }
            if (touchedContainer) {
                InventoryPaperMirror.mirrorContainerToPaper(paperWorld, containers, byUser, username);
                IntUnaryOperator resolve = packed -> resolvePacked(username, packed);
                InventoryCraftTrade.maybeExecuteTradeOnResultTake(byUser, containers, recipes, username,
                        actions, resolve);
            }
        }
        return mutated;
    }

    public boolean setHeldHotbar(String username, int hotbarSlot) {
        BedrockPaperWorldSync sync = paperWorld;
        if (sync != null && sync.isEnabled() && hotbarSlot >= 0 && hotbarSlot <= 8) {
            return sync.setHeldItemSlot(username, hotbarSlot);
        }
        return false;
    }

    private int resolvePacked(String username, int packedOrIndex) {
        if (packedOrIndex < 0) {
            return -1;
        }
        int containerId = unpackContainerId(packedOrIndex);
        int slot = unpackSlot(packedOrIndex);
        if (containerId == 0 && packedOrIndex < TOTAL) {
            return packedOrIndex;
        }
        return mapContainerSlot(username, containerId, slot);
    }

    private static Slot[] emptySlots() {
        Slot[] s = new Slot[TOTAL];
        Arrays.fill(s, Slot.AIR);
        return s;
    }

    /**
     * Map Bedrock container+slot → shadow index for {@code username}'s open window.
     * Container id 7 = opened chest/furnace/hopper UI.
     */
    public int mapContainerSlot(String username, int containerId, int slot) {
        return switch (containerId) {
            case 28 -> (slot >= 0 && slot <= 8) ? slot : -1;
            case 29 -> (slot >= 0 && slot <= 26) ? slot + 9 : -1;
            case 12 -> (slot >= 0 && slot < SLOTS) ? slot : -1;
            case 59 -> CURSOR;
            case 34 -> OFFHAND;
            case 6 -> (slot >= 0 && slot < ARMOR_SLOTS) ? ARMOR_BASE + slot : -1;
            case 13 -> (slot >= 0 && slot < CRAFT_SLOTS) ? CRAFT_BASE + slot : -1;
            case 50, 14 -> CRAFT_RESULT;
            case 7 -> {
                int max = CONTAINER_MAX;
                BedrockContainerBridge bridge = containers;
                if (bridge != null) {
                    BedrockContainerBridge.OpenWindow w = bridge.current(username);
                    if (w != null) {
                        max = bridge.slotsForType(w.type());
                    }
                }
                yield (slot >= 0 && slot < max) ? CONTAINER_BASE + slot : -1;
            }
            default -> -1;
        };
    }

    /** @deprecated use {@link #mapContainerSlot(String, int, int)} */
    @Deprecated
    public static int mapContainerSlot(int containerId, int slot) {
        return switch (containerId) {
            case 28 -> (slot >= 0 && slot <= 8) ? slot : -1;
            case 29 -> (slot >= 0 && slot <= 26) ? slot + 9 : -1;
            case 12 -> (slot >= 0 && slot < SLOTS) ? slot : -1;
            case 59 -> CURSOR;
            case 34 -> OFFHAND;
            case 6 -> (slot >= 0 && slot < ARMOR_SLOTS) ? ARMOR_BASE + slot : -1;
            case 13 -> (slot >= 0 && slot < CRAFT_SLOTS) ? CRAFT_BASE + slot : -1;
            case 50, 14 -> CRAFT_RESULT;
            case 7 -> (slot >= 0 && slot < CONTAINER_MAX) ? CONTAINER_BASE + slot : -1;
            default -> -1;
        };
    }
}
