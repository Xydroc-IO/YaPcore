package com.yapcore.crossplay.bedrock;

import io.netty.buffer.ByteBuf;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * P4.6 — Bedrock container open/close (chest, furnace, enchant, villager, workbench,
 * anvil, smithing, loom, stonecutter, cartography).
 * Opens Paper inventories when a Bukkit player is injected; always sends BE container_open.
 */
public final class BedrockContainerBridge {

    private static final Logger LOG = Logger.getLogger("YaPcore.BEContainer");

    public static final int TYPE_CHEST = 0;
    public static final int TYPE_WORKBENCH = 1;
    public static final int TYPE_FURNACE = 2;
    public static final int TYPE_ENCHANT = 3;
    public static final int TYPE_ANVIL = 5;
    public static final int TYPE_HOPPER = 8;
    public static final int TYPE_VILLAGER = 15;
    public static final int TYPE_LOOM = 24;
    public static final int TYPE_STONECUTTER = 29;
    public static final int TYPE_CARTOGRAPHY = 30;
    public static final int TYPE_SMITHING = 33;

    private final AtomicInteger nextWindow = new AtomicInteger(1);
    private final ConcurrentHashMap<String, OpenWindow> openByUser = new ConcurrentHashMap<>();
    private BiConsumer<String, ByteBuf> sender = (u, b) -> {
    };
    private volatile BedrockPaperWorldSync paperWorld;
    private volatile BedrockInventoryAuthority inventory;

    public record OpenWindow(int windowId, int type, int x, int y, int z, long entityRuntimeId) {
        public OpenWindow(int windowId, int type, int x, int y, int z) {
            this(windowId, type, x, y, z, -1L);
        }
    }

    public void setSender(BiConsumer<String, ByteBuf> sender) {
        this.sender = sender != null ? sender : (u, b) -> {
        };
    }

    public void attachPaper(BedrockPaperWorldSync paperWorld) {
        this.paperWorld = paperWorld;
    }

    public void attachInventory(BedrockInventoryAuthority inventory) {
        this.inventory = inventory;
    }

    public OpenWindow open(String username, int type, int x, int y, int z) {
        int id = nextWindow.getAndIncrement() & 0x7F;
        if (id == 0) {
            id = nextWindow.getAndIncrement() & 0x7F;
        }
        OpenWindow w = new OpenWindow(id, type, x, y, z);
        openByUser.put(username.toLowerCase(), w);
        sender.accept(username, BedrockPacketCodec.containerOpen(id, type, x, y, z, -1));
        int slots = slotsForType(type);
        int[] ids = new int[slots];
        int[] counts = new int[slots];
        if (paperWorld != null && paperWorld.isEnabled()) {
            paperWorld.openContainer(username, type, x, y, z);
            int[][] snap = paperWorld.snapshotBlockInventory(x, y, z, slots);
            if (snap != null && snap.length >= 2) {
                System.arraycopy(snap[0], 0, ids, 0, Math.min(slots, snap[0].length));
                System.arraycopy(snap[1], 0, counts, 0, Math.min(slots, snap[1].length));
            }
        }
        sender.accept(username, BedrockPacketCodec.inventoryContent(id, ids, counts));
        if (inventory != null) {
            inventory.seedContainer(username, ids, counts);
        }
        LOG.info("BE container open " + username + " type=" + type + " @" + x + "," + y + "," + z);
        return w;
    }

    public int slotsForType(int type) {
        return switch (type) {
            case TYPE_WORKBENCH -> 10;
            case TYPE_FURNACE -> 3;
            case TYPE_ENCHANT -> 2;
            case TYPE_ANVIL -> 3;
            case TYPE_HOPPER -> 5;
            case TYPE_VILLAGER -> 3;
            case TYPE_LOOM -> 4;
            case TYPE_STONECUTTER -> 2;
            case TYPE_CARTOGRAPHY -> 3;
            case TYPE_SMITHING -> 4;
            default -> 27;
        };
    }

    /** Virtual JE UIs without a block inventory — shadow + Paper open inventory only. */
    public static boolean isVirtualContainer(int type) {
        return type == TYPE_WORKBENCH
                || type == TYPE_ENCHANT
                || type == TYPE_VILLAGER
                || type == TYPE_ANVIL
                || type == TYPE_SMITHING
                || type == TYPE_LOOM
                || type == TYPE_STONECUTTER
                || type == TYPE_CARTOGRAPHY;
    }

    /** Villager trade UI bound to an entity runtime id (not block coords). */
    public OpenWindow openVillager(String username, long entityRuntimeId, String displayName) {
        int id = nextWindow.getAndIncrement() & 0x7F;
        if (id == 0) {
            id = nextWindow.getAndIncrement() & 0x7F;
        }
        OpenWindow w = new OpenWindow(id, TYPE_VILLAGER, 0, 0, 0, entityRuntimeId);
        openByUser.put(username.toLowerCase(), w);
        sender.accept(username, BedrockPacketCodec.containerOpen(id, TYPE_VILLAGER, 0, 0, 0, entityRuntimeId));
        int[] ids = new int[3];
        int[] counts = new int[3];
        sender.accept(username, BedrockPacketCodec.inventoryContent(id, ids, counts));
        if (inventory != null) {
            inventory.seedContainer(username, ids, counts);
        }
        if (paperWorld != null && paperWorld.isEnabled()) {
            paperWorld.openMerchant(username, displayName);
        }
        LOG.info("BE villager trade open " + username + " entity=" + entityRuntimeId
                + (displayName != null ? " name=" + displayName : ""));
        return w;
    }

    public void close(String username, boolean serverInitiated) {
        OpenWindow w = openByUser.remove(username.toLowerCase());
        if (w == null) {
            return;
        }
        sender.accept(username, BedrockPacketCodec.containerClose(w.windowId(), serverInitiated));
        if (inventory != null) {
            inventory.clearContainer(username);
        }
        if (paperWorld != null) {
            paperWorld.closeContainer(username);
        }
    }

    public void handleClientClose(String username, ByteBuf body) {
        BedrockPacketCodec.ContainerCloseDecode d = BedrockPacketCodec.tryDecodeContainerClose(body);
        openByUser.remove(username.toLowerCase());
        if (inventory != null) {
            inventory.clearContainer(username);
        }
        if (paperWorld != null) {
            paperWorld.closeContainer(username);
        }
        LOG.fine("BE container close " + username
                + (d != null ? " win=" + d.windowId() : ""));
    }

    /** Map JE block material hint → container type. */
    public static int typeForBlock(String blockHint) {
        if (blockHint == null) {
            return TYPE_CHEST;
        }
        String b = blockHint.toLowerCase();
        if (b.contains("crafting") || b.contains("workbench")) {
            return TYPE_WORKBENCH;
        }
        if (b.contains("furnace") || b.contains("smoker") || b.contains("blast")) {
            return TYPE_FURNACE;
        }
        if (b.contains("enchant")) {
            return TYPE_ENCHANT;
        }
        if (b.contains("anvil")) {
            return TYPE_ANVIL;
        }
        if (b.contains("smithing")) {
            return TYPE_SMITHING;
        }
        if (b.contains("loom")) {
            return TYPE_LOOM;
        }
        if (b.contains("stonecutter")) {
            return TYPE_STONECUTTER;
        }
        if (b.contains("cartograph")) {
            return TYPE_CARTOGRAPHY;
        }
        if (b.contains("hopper")) {
            return TYPE_HOPPER;
        }
        if (b.contains("villager") || b.contains("trade")) {
            return TYPE_VILLAGER;
        }
        return TYPE_CHEST;
    }

    public OpenWindow current(String username) {
        return openByUser.get(username.toLowerCase());
    }

    public Map<String, OpenWindow> snapshot() {
        return Map.copyOf(openByUser);
    }
}
