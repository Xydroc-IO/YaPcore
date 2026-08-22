package com.yapcore.crossplay.bedrock;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Server-side Bedrock inventory authority: shadow 36 storage + cursor/offhand +
 * open-window container slots, mirrored onto Paper when a live player / block inv exists.
 */
public final class BedrockInventoryAuthority {

    private static final Logger LOG = Logger.getLogger("YaPcore.BedrockInv");

    public static final int SLOTS = 36;
    public static final int CURSOR = 36;
    public static final int OFFHAND = 37;
    /** Armor: helmet, chest, legs, boots (Bedrock container id 6). */
    public static final int ARMOR_BASE = 38;
    public static final int ARMOR_SLOTS = 4;
    /** Player/workbench crafting grid (Bedrock container id 13). */
    public static final int CRAFT_BASE = 42;
    public static final int CRAFT_SLOTS = 9;
    public static final int CRAFT_RESULT = 51;
    /** First index of open-window container shadow (chest 27 / furnace 3 / …). */
    public static final int CONTAINER_BASE = 52;
    public static final int CONTAINER_MAX = 27;
    private static final int TOTAL = CONTAINER_BASE + CONTAINER_MAX;

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
        mirrorClearToPaper(username);
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
        mirrorStorageToPaper(username);
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
        mirrorGiveToPaper(username, networkId, count - remaining);
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
    public boolean applyActions(String username, java.util.List<BedrockPacketCodec.StackAction> actions) {
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
                    if (movePartial(username, from, to, a.count())) {
                        mutated = true;
                        if (isContainerIndex(from) || isContainerIndex(to)) {
                            touchedContainer = true;
                        }
                        if (isArmorOrCraft(from) || isArmorOrCraft(to)) {
                            touchedArmorOrCraft = true;
                        }
                    }
                }
                case SWAP -> {
                    if (swap(username, from, to)) {
                        mutated = true;
                        if (isContainerIndex(from) || isContainerIndex(to)) {
                            touchedContainer = true;
                        }
                        if (isArmorOrCraft(from) || isArmorOrCraft(to)) {
                            touchedArmorOrCraft = true;
                        }
                    }
                }
                case DROP, DESTROY, CONSUME -> {
                    if (consume(username, from, a.count())) {
                        mutated = true;
                        if (isContainerIndex(from)) {
                            touchedContainer = true;
                        }
                        if (isArmorOrCraft(from)) {
                            touchedArmorOrCraft = true;
                        }
                    }
                }
                case CREATE -> {
                    if (a.creativeNetworkId() > 0) {
                        int dest = to >= 0 ? to : CURSOR;
                        setSlot(username, dest, new Slot(a.creativeNetworkId(), Math.max(1, a.count())));
                        mutated = true;
                        if (isArmorOrCraft(dest)) {
                            touchedArmorOrCraft = true;
                        }
                    }
                }
                case CRAFT_RECIPE, CRAFT_RECIPE_AUTO -> {
                    if (applyCraftRecipe(username, a.creativeNetworkId(), Math.max(1, a.count()))) {
                        mutated = true;
                        touchedArmorOrCraft = true;
                    }
                }
                case CRAFT_RECIPE_OPTIONAL -> {
                    // Enchant table option net-id, or trade recipe index
                    if (applyOptionalCraft(username, a.creativeNetworkId())) {
                        mutated = true;
                        touchedContainer = true;
                        touchedArmorOrCraft = true;
                    }
                }
                case CRAFT_CREATIVE -> {
                    if (a.creativeNetworkId() > 0) {
                        setSlot(username, CURSOR, new Slot(a.creativeNetworkId(), Math.max(1, a.count())));
                        mutated = true;
                    }
                }
                default -> {
                }
            }
        }
        if (mutated) {
            mirrorStorageToPaper(username);
            if (touchedArmorOrCraft) {
                mirrorArmorCraftToPaper(username);
            }
            if (touchedContainer) {
                mirrorContainerToPaper(username);
                maybeExecuteTradeOnResultTake(username, actions);
            }
        }
        return mutated;
    }

    /** After villager result TAKE, execute matching Paper merchant recipe. */
    private void maybeExecuteTradeOnResultTake(String username,
                                               java.util.List<BedrockPacketCodec.StackAction> actions) {
        BedrockContainerBridge bridge = containers;
        BedrockPaperRecipes r = recipes;
        if (bridge == null || r == null) {
            return;
        }
        BedrockContainerBridge.OpenWindow w = bridge.current(username);
        if (w == null || w.type() != BedrockContainerBridge.TYPE_VILLAGER) {
            return;
        }
        for (BedrockPacketCodec.StackAction a : actions) {
            if (a == null || a.type() != BedrockPacketCodec.StackActionType.TAKE) {
                continue;
            }
            int from = resolvePacked(username, a.sourceSlot());
            // Villager result is typically container slot 2
            if (from == CONTAINER_BASE + 2) {
                // Prefer recipe index from CREATE/optional in same request; else 0
                int idx = 0;
                for (BedrockPacketCodec.StackAction b : actions) {
                    if (b != null && b.type() == BedrockPacketCodec.StackActionType.CRAFT_RECIPE_OPTIONAL) {
                        idx = Math.max(0, b.creativeNetworkId());
                    }
                }
                int[] sell = r.executeTrade(username, idx);
                if (sell != null && sell[0] > 0) {
                    setSlot(username, CURSOR, new Slot(sell[0], sell[1]));
                    // Clear trade inputs
                    setSlot(username, CONTAINER_BASE, Slot.AIR);
                    setSlot(username, CONTAINER_BASE + 1, Slot.AIR);
                    setSlot(username, CONTAINER_BASE + 2, Slot.AIR);
                }
            }
        }
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
        // Codec always packs (containerId<<16)|slot; containerId 0 + small index = legacy absolute
        if (containerId == 0 && packedOrIndex < TOTAL) {
            return packedOrIndex;
        }
        return mapContainerSlot(username, containerId, slot);
    }

    private static boolean isContainerIndex(int idx) {
        return idx >= CONTAINER_BASE && idx < TOTAL;
    }

    private static boolean isArmorOrCraft(int idx) {
        return (idx >= ARMOR_BASE && idx < ARMOR_BASE + ARMOR_SLOTS)
                || (idx >= CRAFT_BASE && idx <= CRAFT_RESULT);
    }

    /**
     * JE recipe graph via Paper: match craft grid → result on cursor; consume one per cell.
     * Falls back to client-supplied network id when Paper unavailable.
     */
    private boolean applyCraftRecipe(String username, int recipeOrResultNetId, int times) {
        ensure(username);
        Slot[] slots = byUser.get(username.toLowerCase());
        String[] mats = new String[CRAFT_SLOTS];
        int[] counts = new int[CRAFT_SLOTS];
        boolean anyInput = false;
        for (int i = 0; i < CRAFT_SLOTS; i++) {
            Slot s = slots[CRAFT_BASE + i];
            if (!s.isEmpty()) {
                anyInput = true;
                mats[i] = materialForNetworkId(s.networkId());
                counts[i] = s.count();
            }
        }
        int put = Math.max(1, times);
        int resultId = 0;
        int resultCount = put;
        BedrockPaperRecipes r = recipes;
        if (r != null && anyInput) {
            int[] resolved = r.craftResultFromGrid(username, mats, counts);
            if (resolved != null && resolved[0] > 0) {
                resultId = resolved[0];
                resultCount = Math.max(1, resolved[1]) * put;
            }
        }
        if (resultId <= 0 && !anyInput && recipeOrResultNetId > 0) {
            // Creative / empty-grid path only — recipe net id may be a creative item id
            resultId = recipeOrResultNetId;
            resultCount = put;
        }
        if (resultId <= 0) {
            // Never treat JE recipe net-ids as item network ids when the grid has inputs
            return false;
        }
        Slot result = new Slot(resultId, Math.min(64, resultCount));
        slots[CRAFT_RESULT] = result;
        Slot cur = slots[CURSOR];
        if (cur.isEmpty()) {
            slots[CURSOR] = result;
        } else if (cur.networkId() == resultId) {
            slots[CURSOR] = cur.withCount(Math.min(64, cur.count() + resultCount));
        } else {
            // Cursor busy — leave result on CRAFT_RESULT for a subsequent TAKE
            for (int t = 0; t < put; t++) {
                for (int i = 0; i < CRAFT_SLOTS; i++) {
                    Slot s = slots[CRAFT_BASE + i];
                    if (!s.isEmpty()) {
                        slots[CRAFT_BASE + i] = s.withCount(s.count() - 1);
                    }
                }
            }
            return true;
        }
        for (int t = 0; t < put; t++) {
            for (int i = 0; i < CRAFT_SLOTS; i++) {
                Slot s = slots[CRAFT_BASE + i];
                if (!s.isEmpty()) {
                    slots[CRAFT_BASE + i] = s.withCount(s.count() - 1);
                }
            }
        }
        return true;
    }

    /** Enchant option net-id or villager recipe index. */
    private boolean applyOptionalCraft(String username, int netId) {
        BedrockContainerBridge bridge = containers;
        BedrockPaperRecipes r = recipes;
        if (bridge == null || r == null || netId <= 0) {
            return false;
        }
        BedrockContainerBridge.OpenWindow w = bridge.current(username);
        if (w == null) {
            return false;
        }
        if (w.type() == BedrockContainerBridge.TYPE_ENCHANT) {
            int[] result = r.applyEnchantOption(username, netId);
            if (result != null && result[0] > 0) {
                setSlot(username, CONTAINER_BASE, new Slot(result[0], result[1]));
                return true;
            }
            return false;
        }
        if (w.type() == BedrockContainerBridge.TYPE_VILLAGER) {
            int[] sell = r.executeTrade(username, Math.max(0, netId - 1));
            if (sell != null && sell[0] > 0) {
                setSlot(username, CONTAINER_BASE + 2, new Slot(sell[0], sell[1]));
                setSlot(username, CURSOR, new Slot(sell[0], sell[1]));
                return true;
            }
        }
        return false;
    }

    private void mirrorArmorCraftToPaper(String username) {
        BedrockPaperWorldSync sync = paperWorld;
        if (sync == null || !sync.isEnabled()) {
            return;
        }
        Slot[] slots = byUser.get(username.toLowerCase());
        for (int i = 0; i < ARMOR_SLOTS; i++) {
            Slot s = slots[ARMOR_BASE + i];
            if (s.isEmpty()) {
                sync.setArmorSlot(username, i, "AIR", 0);
            } else {
                String mat = materialForNetworkId(s.networkId());
                if (mat != null) {
                    sync.setArmorSlot(username, i, mat, s.count());
                }
            }
        }
        for (int i = 0; i < CRAFT_SLOTS; i++) {
            Slot s = slots[CRAFT_BASE + i];
            if (s.isEmpty()) {
                sync.setCraftSlot(username, i, "AIR", 0);
            } else {
                String mat = materialForNetworkId(s.networkId());
                if (mat != null) {
                    sync.setCraftSlot(username, i, mat, s.count());
                }
            }
        }
    }

    private boolean movePartial(String username, int from, int to, int count) {
        if (from < 0 || to < 0 || from >= TOTAL || to >= TOTAL || count <= 0) {
            return false;
        }
        Slot[] slots = byUser.get(username.toLowerCase());
        Slot src = slots[from];
        if (src.isEmpty()) {
            return false;
        }
        int move = Math.min(count, src.count());
        Slot dst = slots[to];
        if (dst.isEmpty()) {
            slots[to] = new Slot(src.networkId(), move);
            slots[from] = src.withCount(src.count() - move);
            return true;
        }
        if (dst.networkId() != src.networkId()) {
            return false;
        }
        int room = 64 - dst.count();
        if (room <= 0) {
            return false;
        }
        move = Math.min(move, room);
        slots[to] = dst.withCount(dst.count() + move);
        slots[from] = src.withCount(src.count() - move);
        return true;
    }

    private boolean swap(String username, int from, int to) {
        if (from < 0 || to < 0 || from >= TOTAL || to >= TOTAL) {
            return false;
        }
        Slot[] slots = byUser.get(username.toLowerCase());
        Slot a = slots[from];
        slots[from] = slots[to];
        slots[to] = a;
        return true;
    }

    private boolean consume(String username, int from, int count) {
        if (from < 0 || from >= TOTAL || count <= 0) {
            return false;
        }
        Slot[] slots = byUser.get(username.toLowerCase());
        Slot src = slots[from];
        if (src.isEmpty()) {
            return false;
        }
        slots[from] = src.withCount(src.count() - Math.min(count, src.count()));
        return true;
    }

    private void setSlot(String username, int slot, Slot value) {
        if (slot < 0 || slot >= TOTAL) {
            return;
        }
        byUser.get(username.toLowerCase())[slot] = value == null ? Slot.AIR : value;
    }

    private static Slot[] emptySlots() {
        Slot[] s = new Slot[TOTAL];
        Arrays.fill(s, Slot.AIR);
        return s;
    }

    private void mirrorClearToPaper(String username) {
        BedrockPaperWorldSync sync = paperWorld;
        if (sync != null && sync.isEnabled()) {
            sync.clearInventory(username);
        }
    }

    private void mirrorGiveToPaper(String username, int networkId, int count) {
        BedrockPaperWorldSync sync = paperWorld;
        if (sync == null || !sync.isEnabled() || count <= 0) {
            return;
        }
        String mat = materialForNetworkId(networkId);
        if (mat != null) {
            sync.giveItem(username, mat, count);
        }
    }

    private void mirrorStorageToPaper(String username) {
        BedrockPaperWorldSync sync = paperWorld;
        if (sync == null || !sync.isEnabled()) {
            return;
        }
        Slot[] slots = byUser.get(username.toLowerCase());
        for (int i = 0; i < SLOTS; i++) {
            Slot s = slots[i];
            if (s.isEmpty()) {
                sync.setStorageSlot(username, i, "AIR", 0);
            } else {
                String mat = materialForNetworkId(s.networkId());
                if (mat != null) {
                    sync.setStorageSlot(username, i, mat, s.count());
                }
            }
        }
        // Offhand
        Slot oh = slots[OFFHAND];
        if (oh.isEmpty()) {
            sync.setOffhand(username, "AIR", 0);
        } else {
            String mat = materialForNetworkId(oh.networkId());
            if (mat != null) {
                sync.setOffhand(username, mat, oh.count());
            }
        }
    }

    private void mirrorContainerToPaper(String username) {
        BedrockPaperWorldSync sync = paperWorld;
        BedrockContainerBridge bridge = containers;
        if (sync == null || !sync.isEnabled() || bridge == null) {
            return;
        }
        BedrockContainerBridge.OpenWindow w = bridge.current(username);
        if (w == null) {
            return;
        }
        int n = bridge.slotsForType(w.type());
        Slot[] slots = byUser.get(username.toLowerCase());
        for (int i = 0; i < n; i++) {
            Slot s = slots[CONTAINER_BASE + i];
            if (s.isEmpty()) {
                sync.setBlockInventorySlot(w.x(), w.y(), w.z(), i, "AIR", 0);
            } else {
                String mat = materialForNetworkId(s.networkId());
                if (mat != null) {
                    sync.setBlockInventorySlot(w.x(), w.y(), w.z(), i, mat, s.count());
                }
            }
        }
    }

    private static String materialForNetworkId(int networkId) {
        for (BedrockItemStates.ItemState s : BedrockItemStates.all()) {
            if ((s.runtimeId() & 0xFFFF) == networkId) {
                String n = s.name();
                if (n.startsWith("minecraft:")) {
                    n = n.substring(10);
                }
                return n.toUpperCase(java.util.Locale.ROOT);
            }
        }
        return null;
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
            case 50, 14 -> CRAFT_RESULT; // created output / craft result (protocol variants)
            case 7 -> { // opened container (chest / furnace / hopper / villager / enchant …)
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
