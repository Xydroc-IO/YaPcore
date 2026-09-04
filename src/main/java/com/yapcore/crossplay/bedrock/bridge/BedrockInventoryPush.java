package com.yapcore.crossplay.bedrock.bridge;

import com.yapcore.crossplay.bedrock.*;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

/** Inventory, container, enchant, villager, and furnace UI pushes. */
public final class BedrockInventoryPush {

    private final BedrockBridgeContext ctx;
    private final BedrockWorldPush world;

    public BedrockInventoryPush(BedrockBridgeContext ctx, BedrockWorldPush world) {
        this.ctx = ctx;
        this.world = world;
    }

    void pushInventory(long guid, String username) {
        ctx.inventory.ensure(username);
        BedrockPaperWorldSync sync = ctx.paperWorld;
        if (sync != null && sync.isEnabled()) {
            int[][] paper = sync.snapshotInventoryStacksLiveOnly(username, 36);
            if (paper != null) {
                ctx.inventory.seedStorage(username, paper[0], paper[1]);
                ctx.inventoryFingerprint.put(username.toLowerCase(), fingerprintStacks(paper[0], paper[1]));
                String[] owners = sync.snapshotSkullOwnersLiveOnly(username, 36);
                ctx.send(guid, BedrockPacketCodec.inventoryContent(0, paper[0], paper[1], owners));
                return;
            }
        }
        int[] ids = ctx.inventory.storageNetworkIds(username);
        int[] counts = ctx.inventory.storageCounts(username);
        ctx.inventoryFingerprint.put(username.toLowerCase(), fingerprintStacks(ids, counts));
        ctx.send(guid, BedrockPacketCodec.inventoryContent(0, ids, counts));
    }

    void maybePushPaperInventory(long guid, String username) {
        BedrockPaperWorldSync sync = ctx.paperWorld;
        if (sync == null || !sync.isEnabled() || !sync.hasInjectedPlayer(username)) {
            return;
        }
        int[][] paper = sync.snapshotInventoryStacksLiveOnly(username, 36);
        if (paper == null) {
            return;
        }
        long fp = fingerprintStacks(paper[0], paper[1]);
        Long prev = ctx.inventoryFingerprint.get(username.toLowerCase());
        if (prev != null && prev == fp) {
            return;
        }
        ctx.inventory.seedStorage(username, paper[0], paper[1]);
        ctx.inventoryFingerprint.put(username.toLowerCase(), fp);
        String[] owners = sync.snapshotSkullOwnersLiveOnly(username, 36);
        ctx.send(guid, BedrockPacketCodec.inventoryContent(0, paper[0], paper[1], owners));
    }

    void pushOpenContainer(long guid, String username) {
        BedrockContainerBridge.OpenWindow w = ctx.containers.current(username);
        if (w == null) {
            return;
        }
        int n = ctx.containers.slotsForType(w.type());
        BedrockPaperWorldSync sync = ctx.paperWorld;
        if (sync != null && sync.isEnabled()
                && !BedrockContainerBridge.isVirtualContainer(w.type())) {
            int[][] live = sync.snapshotBlockInventory(w.x(), w.y(), w.z(), n);
            if (live != null && live.length >= 2) {
                ctx.inventory.seedContainer(username, live[0], live[1]);
                ctx.send(guid, BedrockPacketCodec.inventoryContent(w.windowId(), live[0], live[1]));
                return;
            }
        }
        int[][] snap = ctx.inventory.containerSnapshot(username, n);
        ctx.send(guid, BedrockPacketCodec.inventoryContent(w.windowId(), snap[0], snap[1]));
    }

    void pushOpenContainerProgress(long guid, String username) {
        BedrockContainerBridge.OpenWindow w = ctx.containers.current(username);
        if (w == null) {
            return;
        }
        if (w.type() == BedrockContainerBridge.TYPE_FURNACE) {
            pushFurnaceProgress(guid, w);
        }
    }

    void pushFurnaceProgress(long guid, BedrockContainerBridge.OpenWindow w) {
        BedrockPaperWorldSync sync = ctx.paperWorld;
        if (sync == null || !sync.isEnabled() || w == null) {
            return;
        }
        int[] prog = sync.snapshotFurnaceProgress(w.x(), w.y(), w.z());
        if (prog == null || prog.length < 4) {
            return;
        }
        ctx.send(guid, List.of(
                BedrockPacketCodec.containerSetData(w.windowId(), 0, prog[0]),
                BedrockPacketCodec.containerSetData(w.windowId(), 1, prog[1]),
                BedrockPacketCodec.containerSetData(w.windowId(), 2, prog[2]),
                BedrockPacketCodec.containerSetData(w.windowId(), 3, prog[3])
        ));
    }

    void pushEnchantOptions(long guid, String user) {
        BedrockContainerBridge.OpenWindow w = ctx.containers.current(user);
        if (w == null) {
            return;
        }
        BedrockPaperWorldSync sync = ctx.paperWorld;
        List<BedrockPaperRecipes.EnchantOption> opts = List.of();
        if (sync != null && sync.isEnabled()) {
            opts = new BedrockPaperRecipes(sync).enchantOptionsFor(user);
        }
        if (opts.isEmpty()) {
            ctx.send(guid, BedrockPacketCodec.playerEnchantOptions(List.of()));
            return;
        }
        List<ByteBuf> pkts = new ArrayList<>();
        pkts.add(BedrockPacketCodec.playerEnchantOptions(opts));
        for (int i = 0; i < opts.size(); i++) {
            pkts.add(BedrockPacketCodec.containerSetData(w.windowId(), i, opts.get(i).cost()));
        }
        ctx.send(guid, pkts);
    }

    void pushVillagerTrade(long guid, String user, BedrockContainerBridge.OpenWindow w,
                           long traderRuntime) {
        if (w == null) {
            return;
        }
        BedrockPaperWorldSync sync = ctx.paperWorld;
        List<int[]> offers = List.of();
        if (sync != null && sync.isEnabled()) {
            sync.openMerchant(user, world.nameForRuntime(traderRuntime));
            offers = sync.snapshotMerchantOffers(user, 16);
        }
        Long playerRt = ctx.runtimeByGuid.get(guid);
        ctx.send(guid, BedrockPacketCodec.updateTrade(
                w.windowId(), BedrockContainerBridge.TYPE_VILLAGER,
                offers.size(), 0, true, false,
                traderRuntime, playerRt == null ? 0L : playerRt,
                "Villager", offers));
    }

    static long fingerprintStacks(int[] ids, int[] counts) {
        long h = 1125899906842597L;
        int n = ids == null ? 0 : ids.length;
        for (int i = 0; i < n; i++) {
            h = 31 * h + (ids[i] & 0xffffffffL);
            int c = counts != null && i < counts.length ? counts[i] : 0;
            h = 31 * h + (c & 0xffffffffL);
        }
        return h;
    }
}
