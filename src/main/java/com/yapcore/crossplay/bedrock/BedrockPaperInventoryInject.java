package com.yapcore.crossplay.bedrock;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * P4.4 — Paper-side inventory vault for Bedrock players (fallback / staging).
 * <p>
 * When {@link BedrockPaperPlayerInject} has placed a live Bukkit {@code Player},
 * vault slots flush onto that inventory. Otherwise the vault is the authority
 * for pure-BE sessions until inject succeeds.
 */
public final class BedrockPaperInventoryInject {

    private static final Logger LOG = Logger.getLogger("YaPcore.BEPaperInv");

    public record Stack(String material, int count) {
        public static final Stack AIR = new Stack("AIR", 0);

        public boolean isEmpty() {
            return count <= 0 || material == null || "AIR".equalsIgnoreCase(material)
                    || "CAVE_AIR".equalsIgnoreCase(material) || "VOID_AIR".equalsIgnoreCase(material);
        }
    }

    public record Session(UUID uuid, String username, Stack[] storage, int heldHotbar) {
        Session withHeld(int hotbar) {
            return new Session(uuid, username, storage, hotbar);
        }
    }

    private final ConcurrentHashMap<String, Session> byName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> nameByUuid = new ConcurrentHashMap<>();

    /** Register / refresh vault for a BE login (idempotent). */
    public Session inject(UUID uuid, String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username");
        }
        String key = username.toLowerCase(java.util.Locale.ROOT);
        UUID id = uuid != null ? uuid : UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());
        Session existing = byName.get(key);
        if (existing != null) {
            nameByUuid.put(id, key);
            return existing;
        }
        Stack[] slots = new Stack[36];
        Arrays.fill(slots, Stack.AIR);
        Session created = new Session(id, username, slots, 0);
        byName.put(key, created);
        nameByUuid.put(id, key);
        LOG.info("Paper inventory inject (vault) for BE player " + username + " uuid=" + id);
        return created;
    }

    public void eject(String username) {
        if (username == null) {
            return;
        }
        String key = username.toLowerCase(java.util.Locale.ROOT);
        Session s = byName.remove(key);
        if (s != null) {
            nameByUuid.remove(s.uuid());
            LOG.fine("Paper inventory vault ejected " + username);
        }
    }

    public boolean has(String username) {
        return username != null && byName.containsKey(username.toLowerCase(java.util.Locale.ROOT));
    }

    public Session get(String username) {
        if (username == null) {
            return null;
        }
        return byName.get(username.toLowerCase(java.util.Locale.ROOT));
    }

    public void clear(String username) {
        Session s = get(username);
        if (s == null) {
            return;
        }
        Arrays.fill(s.storage(), Stack.AIR);
    }

    public void setSlot(String username, int slot, String material, int count) {
        Session s = get(username);
        if (s == null || slot < 0 || slot >= 36) {
            return;
        }
        if (count <= 0 || material == null || "AIR".equalsIgnoreCase(material)) {
            s.storage()[slot] = Stack.AIR;
        } else {
            s.storage()[slot] = new Stack(material.toUpperCase(java.util.Locale.ROOT)
                    .replace('-', '_').replace("MINECRAFT:", ""), Math.min(64, count));
        }
    }

    public void give(String username, String material, int count) {
        if (count <= 0 || material == null) {
            return;
        }
        Session s = get(username);
        if (s == null) {
            return;
        }
        String mat = material.toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace("MINECRAFT:", "");
        int remaining = count;
        for (int i = 0; i < 36 && remaining > 0; i++) {
            Stack cur = s.storage()[i];
            if (cur.isEmpty()) {
                int put = Math.min(64, remaining);
                s.storage()[i] = new Stack(mat, put);
                remaining -= put;
            } else if (cur.material().equalsIgnoreCase(mat) && cur.count() < 64) {
                int room = 64 - cur.count();
                int put = Math.min(room, remaining);
                s.storage()[i] = new Stack(mat, cur.count() + put);
                remaining -= put;
            }
        }
    }

    public void setHeld(String username, int hotbar) {
        Session s = get(username);
        if (s == null || hotbar < 0 || hotbar > 8) {
            return;
        }
        byName.put(username.toLowerCase(java.util.Locale.ROOT), s.withHeld(hotbar));
    }

    /** Snapshot vault as Bedrock network ids (air=0). */
    public int[] snapshotNetworkIds(String username, int slots) {
        Session s = get(username);
        if (s == null) {
            return null;
        }
        int n = Math.max(0, slots);
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            Stack st = i < s.storage().length ? s.storage()[i] : Stack.AIR;
            out[i] = st.isEmpty() ? 0 : networkIdForMaterial(st.material());
        }
        return out;
    }

    /** Copy vault → live Paper player inventory when online. Returns true if synced. */
    public boolean flushToLivePlayer(String username, BedrockPaperWorldSync sync) {
        Session s = get(username);
        if (s == null || sync == null || !sync.isEnabled()) {
            return false;
        }
        boolean any = false;
        for (int i = 0; i < 36; i++) {
            Stack st = s.storage()[i];
            if (st.isEmpty()) {
                any |= sync.setStorageSlot(username, i, "AIR", 0);
            } else {
                any |= sync.setStorageSlot(username, i, st.material(), st.count());
            }
        }
        sync.setHeldItemSlot(username, s.heldHotbar());
        return any;
    }

    /** Pull live Paper inventory into vault when player is online. */
    public boolean pullFromLivePlayer(String username, BedrockPaperWorldSync sync) {
        if (sync == null || !sync.isEnabled() || !has(username)) {
            return false;
        }
        int[][] stacks = sync.snapshotInventoryStacksLiveOnly(username, 36);
        if (stacks == null) {
            return false;
        }
        int[] ids = stacks[0];
        int[] counts = stacks[1];
        for (int i = 0; i < 36; i++) {
            int nid = ids[i];
            if (nid <= 0) {
                setSlot(username, i, "AIR", 0);
            } else {
                String mat = materialForNetworkId(nid);
                if (mat != null) {
                    setSlot(username, i, mat, Math.max(1, counts[i]));
                }
            }
        }
        return true;
    }

    public Map<String, Session> allSessions() {
        return Map.copyOf(byName);
    }

    private static int networkIdForMaterial(String material) {
        if (material == null) {
            return 0;
        }
        String key = "minecraft:" + material.toLowerCase(java.util.Locale.ROOT);
        for (BedrockItemStates.ItemState s : BedrockItemStates.all()) {
            if (s.name().equals(key) || s.name().equalsIgnoreCase(key)) {
                return s.runtimeId() & 0xFFFF;
            }
        }
        return 0;
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
}
