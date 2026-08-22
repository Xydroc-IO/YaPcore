package com.yapcore.crossplay.bedrock.inventory;

import com.yapcore.crossplay.bedrock.BedrockContainerBridge;
import com.yapcore.crossplay.bedrock.BedrockInventoryAuthority;
import com.yapcore.crossplay.bedrock.BedrockItemStates;
import com.yapcore.crossplay.bedrock.BedrockPaperWorldSync;

import java.util.Locale;
import java.util.Map;

/** Mirror Bedrock shadow slots onto Paper player / block inventories. */
public final class InventoryPaperMirror {

    private InventoryPaperMirror() {
    }

    public static void mirrorClearToPaper(BedrockPaperWorldSync sync, String username) {
        if (sync != null && sync.isEnabled()) {
            sync.clearInventory(username);
        }
    }

    public static void mirrorGiveToPaper(BedrockPaperWorldSync sync, String username,
                                         int networkId, int count) {
        if (sync == null || !sync.isEnabled() || count <= 0) {
            return;
        }
        String mat = materialForNetworkId(networkId);
        if (mat != null) {
            sync.giveItem(username, mat, count);
        }
    }

    public static void mirrorStorageToPaper(BedrockPaperWorldSync sync,
                                            Map<String, BedrockInventoryAuthority.Slot[]> byUser,
                                            String username) {
        if (sync == null || !sync.isEnabled()) {
            return;
        }
        BedrockInventoryAuthority.Slot[] slots = byUser.get(username.toLowerCase());
        for (int i = 0; i < BedrockInventoryLayout.SLOTS; i++) {
            BedrockInventoryAuthority.Slot s = slots[i];
            if (s.isEmpty()) {
                sync.setStorageSlot(username, i, "AIR", 0);
            } else {
                String mat = materialForNetworkId(s.networkId());
                if (mat != null) {
                    sync.setStorageSlot(username, i, mat, s.count());
                }
            }
        }
        BedrockInventoryAuthority.Slot oh = slots[BedrockInventoryLayout.OFFHAND];
        if (oh.isEmpty()) {
            sync.setOffhand(username, "AIR", 0);
        } else {
            String mat = materialForNetworkId(oh.networkId());
            if (mat != null) {
                sync.setOffhand(username, mat, oh.count());
            }
        }
    }

    public static void mirrorArmorCraftToPaper(BedrockPaperWorldSync sync,
                                               Map<String, BedrockInventoryAuthority.Slot[]> byUser,
                                               String username) {
        if (sync == null || !sync.isEnabled()) {
            return;
        }
        BedrockInventoryAuthority.Slot[] slots = byUser.get(username.toLowerCase());
        for (int i = 0; i < BedrockInventoryLayout.ARMOR_SLOTS; i++) {
            BedrockInventoryAuthority.Slot s = slots[BedrockInventoryLayout.ARMOR_BASE + i];
            if (s.isEmpty()) {
                sync.setArmorSlot(username, i, "AIR", 0);
            } else {
                String mat = materialForNetworkId(s.networkId());
                if (mat != null) {
                    sync.setArmorSlot(username, i, mat, s.count());
                }
            }
        }
        for (int i = 0; i < BedrockInventoryLayout.CRAFT_SLOTS; i++) {
            BedrockInventoryAuthority.Slot s = slots[BedrockInventoryLayout.CRAFT_BASE + i];
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

    public static void mirrorContainerToPaper(BedrockPaperWorldSync sync, BedrockContainerBridge bridge,
                                              Map<String, BedrockInventoryAuthority.Slot[]> byUser,
                                              String username) {
        if (sync == null || !sync.isEnabled() || bridge == null) {
            return;
        }
        BedrockContainerBridge.OpenWindow w = bridge.current(username);
        if (w == null) {
            return;
        }
        int n = bridge.slotsForType(w.type());
        BedrockInventoryAuthority.Slot[] slots = byUser.get(username.toLowerCase());
        for (int i = 0; i < n; i++) {
            BedrockInventoryAuthority.Slot s = slots[BedrockInventoryLayout.CONTAINER_BASE + i];
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

    public static String materialForNetworkId(int networkId) {
        for (BedrockItemStates.ItemState s : BedrockItemStates.all()) {
            if ((s.runtimeId() & 0xFFFF) == networkId) {
                String n = s.name();
                if (n.startsWith("minecraft:")) {
                    n = n.substring(10);
                }
                return n.toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }
}
