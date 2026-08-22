package com.yapcore.crossplay.bedrock;

import com.yapcore.crossplay.bedrock.paper.PaperWorldSyncBackend;

import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Applies Bedrock BREAK/PLACE (and light MOVE) onto the Paper world when
 * {@code game-authority=paper}. Uses Paper's classloader + main-thread schedule.
 */
public final class BedrockPaperWorldSync {

    private final PaperWorldSyncBackend backend = new PaperWorldSyncBackend();

    public void attach(URLClassLoader loader) {
        backend.attach(loader);
    }

    public void detach() {
        backend.detach();
    }

    /** Inject a real Paper Player for a Bedrock Floodgate identity. */
    public boolean injectPlayer(String username, UUID uuid, double x, double y, double z) {
        return backend.injectPlayer(username, uuid, x, y, z, this);
    }

    /** Remove Paper presence when a Bedrock session disconnects. */
    public boolean ejectPlayer(String username) {
        return backend.ejectPlayer(username, this);
    }

    public boolean hasInjectedPlayer(String username) {
        return backend.hasInjectedPlayer(username);
    }

    public void invalidateColumn(int cx, int cz) {
        backend.invalidateColumn(cx, cz);
    }

    /**
     * Snapshot one overworld column as Bedrock hashed state ids
     * ({@link BedrockBlockRuntimeIds} catalog). Y −64..319 → 24×4096.
     * Returns null if Paper is unavailable. Cached until BREAK/PLACE invalidates.
     */
    public int[][] snapshotColumnHashedStates(int chunkX, int chunkZ) {
        return backend.snapshotColumnHashedStates(chunkX, chunkZ);
    }

    public boolean isEnabled() {
        return backend.isEnabled();
    }

    ClassLoader liveLoaderPublic() {
        return backend.liveLoader();
    }

    public void apply(String action, Map<String, String> payload) {
        backend.apply(action, payload);
    }

    public void openContainer(String playerName, int type, int x, int y, int z) {
        backend.openContainer(playerName, type, x, y, z);
    }

    public void closeContainer(String playerName) {
        backend.closeContainer(playerName);
    }

    public Object findOnlinePlayer(String username) {
        return backend.findOnlinePlayer(username);
    }

    public String materialAt(int x, int y, int z) {
        return backend.materialAt(x, y, z);
    }

    /** Paper overworld spawn (block coords), or null if unavailable. */
    public double[] spawnPosition() {
        return backend.spawnPosition();
    }

    /** Highest solid block Y at chunk center, or -1. */
    public int sampleGroundY(int chunkX, int chunkZ) {
        return backend.sampleGroundY(chunkX, chunkZ);
    }

    public record OnlinePlayer(String name, UUID uuid, double x, double y, double z) {
    }

    public record NearbyLiving(String name, UUID uuid, String entityType,
                               double x, double y, double z, float health, float maxHealth) {
    }

    /** Nearby non-player living entities around a point (for BE entity mirror + combat). */
    public List<NearbyLiving> listNearbyLiving(double x, double y, double z, double radius) {
        return backend.listNearbyLiving(x, y, z, radius);
    }

    /** Snapshot of Paper online players (JE) for BE entity mirror. */
    public List<OnlinePlayer> listOnlinePlayers() {
        return backend.listOnlinePlayers();
    }

    /**
     * Snapshot Bukkit player inventory as Bedrock item network ids (air=0).
     * Prefers live Paper player; falls back to P4.4 inventory vault for pure BE.
     */
    public int[] snapshotInventoryNetworkIds(String username, int slots) {
        return backend.snapshotInventoryNetworkIds(username, slots);
    }

    /** Live Paper player only — null if not online (does not consult vault). */
    public int[] snapshotInventoryNetworkIdsLiveOnly(String username, int slots) {
        return backend.snapshotInventoryNetworkIdsLiveOnly(username, slots);
    }

    /**
     * Live Paper storage: {@code [0]=networkIds, [1]=counts}. Null if player offline.
     */
    public int[][] snapshotInventoryStacksLiveOnly(String username, int slots) {
        return backend.snapshotInventoryStacksLiveOnly(username, slots);
    }

    /**
     * Block inventory at world coords → {@code [0]=networkIds, [1]=counts} (padded to {@code slots}).
     */
    public int[][] snapshotBlockInventory(int x, int y, int z, int slots) {
        return backend.snapshotBlockInventory(x, y, z, slots);
    }

    /**
     * Furnace / smoker / blast cook+fuel progress for CONTAINER_SET_DATA.
     * Returns {@code [cookTime, cookTimeTotal, burnTime, burnTimeTotal]} or null.
     */
    public int[] snapshotFurnaceProgress(int x, int y, int z) {
        return backend.snapshotFurnaceProgress(x, y, z);
    }

    /** Online player health for BE metadata mirror (null if unavailable). */
    public float[] snapshotPlayerHealth(String username) {
        return backend.snapshotPlayerHealth(username);
    }

    /** Register Paper inventory vault for a pure-BE join. */
    public void injectBedrockPlayer(UUID uuid, String username) {
        backend.injectBedrockPlayer(uuid, username);
    }

    public void ejectBedrockPlayer(String username) {
        backend.ejectBedrockPlayer(username);
    }

    public boolean clearInventory(String username) {
        return backend.clearInventory(username);
    }

    public boolean giveItem(String username, String materialName, int amount) {
        return backend.giveItem(username, materialName, amount);
    }

    public boolean setStorageSlot(String username, int slot, String materialName, int amount) {
        return backend.setStorageSlot(username, slot, materialName, amount);
    }

    public boolean setHeldItemSlot(String username, int hotbarSlot) {
        return backend.setHeldItemSlot(username, hotbarSlot);
    }

    public boolean setOffhand(String username, String materialName, int amount) {
        return backend.setOffhand(username, materialName, amount);
    }

    /** Armor index: 0=helmet, 1=chest, 2=legs, 3=boots. */
    public boolean setArmorSlot(String username, int armorIndex, String materialName, int amount) {
        return backend.setArmorSlot(username, armorIndex, materialName, amount);
    }

    /** Best-effort craft matrix slot on open workbench (top inventory). */
    public boolean setCraftSlot(String username, int slot, String materialName, int amount) {
        return backend.setCraftSlot(username, slot, materialName, amount);
    }

    /** Open merchant UI for nearest villager / named entity when possible. */
    public boolean openMerchant(String playerName, String villagerNameHint) {
        return backend.openMerchant(playerName, villagerNameHint);
    }

    /** Snapshot merchant offers as [buyA_id, buyA_count, buyB_id, buyB_count, sell_id, sell_count]×N. */
    public List<int[]> snapshotMerchantOffers(String playerName, int max) {
        return backend.snapshotMerchantOffers(playerName, max);
    }

    public boolean setBlockInventorySlot(int x, int y, int z, int slot, String materialName, int amount) {
        return backend.setBlockInventorySlot(x, y, z, slot, materialName, amount);
    }
}
