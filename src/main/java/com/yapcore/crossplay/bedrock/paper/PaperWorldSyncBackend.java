package com.yapcore.crossplay.bedrock.paper;

import com.yapcore.crossplay.bedrock.BedrockPaperInventoryInject;
import com.yapcore.crossplay.bedrock.BedrockPaperPlayerInject;
import com.yapcore.crossplay.bedrock.BedrockPaperWorldSync;

import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Internal bridge for {@link BedrockPaperWorldSync}.
 * Holds shared Paper-reflection state and delegates to focused helpers.
 */
public final class PaperWorldSyncBackend {

    static final Logger LOG = Logger.getLogger("YaPcore.BedrockPaper");

    final AtomicReference<URLClassLoader> paperLoader = new AtomicReference<>();
    final BedrockPaperPlayerInject playerInject = new BedrockPaperPlayerInject(paperLoader);
    final BedrockPaperInventoryInject inventoryInject = new BedrockPaperInventoryInject();
    volatile boolean enabled;
    record ColumnCache(int[][] states, List<PaperWorldBlocks.SkullBlock> skulls) {
    }

    final ConcurrentHashMap<Long, ColumnCache> columnCache = new ConcurrentHashMap<>();

    final PaperWorldMainThread mainThread = new PaperWorldMainThread(this);
    final PaperWorldBlocks blocks = new PaperWorldBlocks(this);
    final PaperWorldCombat combat = new PaperWorldCombat(this);
    final PaperWorldInventory inventory = new PaperWorldInventory(this);
    final PaperWorldContainers containers = new PaperWorldContainers(this);
    final PaperWorldEntities entities = new PaperWorldEntities(this);

    public void attach(URLClassLoader loader) {
        paperLoader.set(loader);
        enabled = loader != null;
        columnCache.clear();
        if (enabled) {
            LOG.info("Bedrock→Paper world sync attached");
        }
    }

    public void detach() {
        paperLoader.set(null);
        enabled = false;
        columnCache.clear();
    }

    public boolean isEnabled() {
        return enabled && paperLoader.get() != null;
    }

    public ClassLoader liveLoader() {
        return com.yapcore.paper.PaperCommandBridge.resolvePaperLoader(paperLoader.get());
    }

    static long columnKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    public void invalidateColumn(int cx, int cz) {
        columnCache.remove(columnKey(cx, cz));
    }

    public boolean injectPlayer(String username, UUID uuid, double x, double y, double z,
                                BedrockPaperWorldSync facade) {
        inventoryInject.inject(uuid, username);
        boolean ok = isEnabled() && playerInject.inject(username, uuid, x, y, z);
        if (ok) {
            inventoryInject.flushToLivePlayer(username, facade);
        }
        return ok;
    }

    public boolean ejectPlayer(String username, BedrockPaperWorldSync facade) {
        inventoryInject.pullFromLivePlayer(username, facade);
        boolean ok = playerInject.eject(username);
        inventoryInject.eject(username);
        return ok;
    }

    public boolean hasInjectedPlayer(String username) {
        return playerInject.isInjected(username);
    }

    public void apply(String action, Map<String, String> payload) {
        if (!isEnabled() || payload == null) {
            return;
        }
        String act = action == null ? "" : action.trim().toUpperCase();
        switch (act) {
            case "BREAK" -> mainThread.runOnMain(() -> {
                int x = PaperWorldMainThread.parse(payload.get("x"), 0);
                int y = PaperWorldMainThread.parse(payload.get("y"), 0);
                int z = PaperWorldMainThread.parse(payload.get("z"), 0);
                blocks.breakBlock(x, y, z);
                invalidateColumn(x >> 4, z >> 4);
            });
            case "PLACE" -> mainThread.runOnMain(() -> {
                int x = PaperWorldMainThread.parse(payload.get("x"), 0);
                int y = PaperWorldMainThread.parse(payload.get("y"), 0);
                int z = PaperWorldMainThread.parse(payload.get("z"), 0);
                blocks.placeBlock(x, y, z, payload.getOrDefault("block", "stone"));
                invalidateColumn(x >> 4, z >> 4);
            });
            case "ATTACK" -> mainThread.runOnMain(() -> combat.attackEntity(
                    payload.get("attacker"),
                    payload.get("target"),
                    payload.get("targetName"),
                    payload.get("targetUuid")));
            case "OPEN_CONTAINER", "CONTAINER" -> mainThread.runOnMain(() -> containers.openContainer(
                    payload.get("player"),
                    PaperWorldMainThread.parse(payload.get("type"),
                            com.yapcore.crossplay.bedrock.BedrockContainerBridge.TYPE_CHEST),
                    PaperWorldMainThread.parse(payload.get("x"), 0),
                    PaperWorldMainThread.parse(payload.get("y"), 0),
                    PaperWorldMainThread.parse(payload.get("z"), 0)));
            case "CLOSE_CONTAINER" -> mainThread.runOnMain(() -> containers.closeContainer(payload.get("player")));
            case "INV", "HOTBAR" -> mainThread.runOnMain(() -> inventory.applyInventory(
                    payload.get("player"),
                    PaperWorldMainThread.parse(payload.get("hotbar"), -1),
                    PaperWorldMainThread.parse(payload.get("slot"), -1),
                    payload.getOrDefault("item", "")));
            default -> {
            }
        }
    }

    public int[][] snapshotColumnHashedStates(int chunkX, int chunkZ) {
        ColumnCache cached = snapshotColumn(chunkX, chunkZ);
        return cached == null ? null : cached.states();
    }

    public List<BedrockPaperWorldSync.SkullBlock> skullsInColumn(int chunkX, int chunkZ) {
        ColumnCache cached = snapshotColumn(chunkX, chunkZ);
        if (cached == null) {
            return List.of();
        }
        return cached.skulls().stream()
                .map(s -> new BedrockPaperWorldSync.SkullBlock(s.x(), s.y(), s.z(), s.owner()))
                .toList();
    }

    private ColumnCache snapshotColumn(int chunkX, int chunkZ) {
        if (!isEnabled()) {
            return null;
        }
        long key = columnKey(chunkX, chunkZ);
        ColumnCache cached = columnCache.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            List<PaperWorldBlocks.SkullBlock> skulls = new java.util.ArrayList<>();
            int[][] column = blocks.readColumnHashedStates(chunkX, chunkZ, skulls);
            if (column != null) {
                ColumnCache bundle = new ColumnCache(column, List.copyOf(skulls));
                columnCache.put(key, bundle);
                return bundle;
            }
            return null;
        } catch (Exception e) {
            LOG.log(Level.FINE, "column snapshot failed", e);
            return null;
        }
    }

    public void openContainer(String playerName, int type, int x, int y, int z) {
        containers.openContainer(playerName, type, x, y, z);
    }

    public void closeContainer(String playerName) {
        containers.closeContainer(playerName);
    }

    public void applyAnvilRename(String playerName, String text) {
        containers.applyAnvilRename(playerName, text);
    }

    public Object findOnlinePlayer(String username) {
        return entities.findOnlinePlayer(username);
    }

    public String materialAt(int x, int y, int z) {
        return blocks.materialAt(x, y, z);
    }

    public String skullOwnerAt(int x, int y, int z) {
        return blocks.skullOwnerAt(x, y, z);
    }

    public double[] spawnPosition() {
        return entities.spawnPosition();
    }

    public int sampleGroundY(int chunkX, int chunkZ) {
        return entities.sampleGroundY(chunkX, chunkZ);
    }

    public List<BedrockPaperWorldSync.NearbyLiving> listNearbyLiving(double x, double y, double z, double radius) {
        return entities.listNearbyLiving(x, y, z, radius);
    }

    public List<BedrockPaperWorldSync.OnlinePlayer> listOnlinePlayers() {
        return entities.listOnlinePlayers();
    }

    public int[] snapshotInventoryNetworkIds(String username, int slots) {
        return inventory.snapshotInventoryNetworkIds(username, slots);
    }

    public int[] snapshotInventoryNetworkIdsLiveOnly(String username, int slots) {
        return inventory.snapshotInventoryNetworkIdsLiveOnly(username, slots);
    }

    public int[][] snapshotInventoryStacksLiveOnly(String username, int slots) {
        return inventory.snapshotInventoryStacksLiveOnly(username, slots);
    }

    public String[] snapshotSkullOwnersLiveOnly(String username, int slots) {
        return inventory.snapshotSkullOwnersLiveOnly(username, slots);
    }

    public int[][] snapshotBlockInventory(int x, int y, int z, int slots) {
        return containers.snapshotBlockInventory(x, y, z, slots);
    }

    public int[] snapshotFurnaceProgress(int x, int y, int z) {
        return containers.snapshotFurnaceProgress(x, y, z);
    }

    public float[] snapshotPlayerHealth(String username) {
        return entities.snapshotPlayerHealth(username);
    }

    public void injectBedrockPlayer(UUID uuid, String username) {
        inventory.injectBedrockPlayer(uuid, username);
    }

    public void ejectBedrockPlayer(String username) {
        inventory.ejectBedrockPlayer(username);
    }

    public boolean clearInventory(String username) {
        return inventory.clearInventory(username);
    }

    public boolean giveItem(String username, String materialName, int amount) {
        return inventory.giveItem(username, materialName, amount);
    }

    public boolean setStorageSlot(String username, int slot, String materialName, int amount) {
        return inventory.setStorageSlot(username, slot, materialName, amount);
    }

    public boolean setHeldItemSlot(String username, int hotbarSlot) {
        return inventory.setHeldItemSlot(username, hotbarSlot);
    }

    public boolean setOffhand(String username, String materialName, int amount) {
        return inventory.setOffhand(username, materialName, amount);
    }

    public boolean setArmorSlot(String username, int armorIndex, String materialName, int amount) {
        return inventory.setArmorSlot(username, armorIndex, materialName, amount);
    }

    public boolean setCraftSlot(String username, int slot, String materialName, int amount) {
        return inventory.setCraftSlot(username, slot, materialName, amount);
    }

    public boolean openMerchant(String playerName, String villagerNameHint) {
        return containers.openMerchant(playerName, villagerNameHint);
    }

    public List<int[]> snapshotMerchantOffers(String playerName, int max) {
        return containers.snapshotMerchantOffers(playerName, max);
    }

    public boolean setBlockInventorySlot(int x, int y, int z, int slot, String materialName, int amount) {
        return containers.setBlockInventorySlot(x, y, z, slot, materialName, amount);
    }
}
