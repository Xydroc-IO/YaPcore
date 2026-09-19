package com.yapcore.holo.impl;

import com.yapcore.holo.HologramLine;
import com.yapcore.holo.HologramLineLayout;
import com.yapcore.lib.PacketService;
import com.yapcore.holo.nms.NmsHologramPackets;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Per-viewer spawn / metadata / teleport packets. */
final class HologramView {

    record Slot(int entityId, UUID uuid) {
    }

    private final HologramRuntime rt;
    private final PacketService packets;
    private final NmsHologramPackets nms;
    private final HologramPages pages;
    private final double spacing;
    private final List<Slot> slots = new CopyOnWriteArrayList<>();
    private final Set<UUID> viewing = ConcurrentHashMap.newKeySet();
    private final Map<UUID, List<String>> lastSent = new ConcurrentHashMap<>();

    HologramView(HologramRuntime runtime, HologramPages pages, double spacing) {
        this.rt = runtime;
        this.packets = runtime.packets;
        this.nms = runtime.nms;
        this.pages = pages;
        this.spacing = spacing;
        rebuildSlots();
    }

    boolean sees(UUID playerId) {
        return viewing.contains(playerId);
    }

    void rebuildSlots() {
        int need = pages.maxLines();
        while (slots.size() < need) {
            slots.add(new Slot(nms.allocateId(), UUID.randomUUID()));
        }
    }

    boolean matchesEntityId(int entityId) {
        for (Slot slot : slots) {
            if (slot.entityId == entityId) {
                return true;
            }
        }
        return false;
    }

    void spawn(Player player, Location base) {
        if (base.getWorld() == null) {
            return;
        }
        List<HologramLine> lines = pages.linesFor(player.getUniqueId());
        List<String> keys = resolvedKeys(player, lines);
        for (int i = 0; i < lines.size(); i++) {
            Slot slot = slots.get(i);
            Location at = base.clone().add(0, HologramLineLayout.yOffset(i, lines.size(), spacing), 0);
            sendLine(player, slot, lines.get(i), keys.get(i), at);
        }
        lastSent.put(player.getUniqueId(), keys);
        viewing.add(player.getUniqueId());
    }

    void update(Player player, Location base) {
        List<HologramLine> lines = pages.linesFor(player.getUniqueId());
        List<String> keys = resolvedKeys(player, lines);
        List<String> prev = lastSent.get(player.getUniqueId());
        if (prev != null && prev.equals(keys)) {
            teleport(player, base);
            return;
        }
        remove(player);
        spawn(player, base);
    }

    void teleport(Player player, Location base) {
        List<HologramLine> lines = pages.linesFor(player.getUniqueId());
        for (int i = 0; i < lines.size(); i++) {
            Location at = base.clone().add(0, HologramLineLayout.yOffset(i, lines.size(), spacing), 0);
            Object tp = nms.teleport(slots.get(i).entityId, at);
            if (tp != null) {
                packets.send(player, tp, false);
            }
        }
    }

    void remove(Player player) {
        if (slots.isEmpty()) {
            return;
        }
        int[] ids = new int[slots.size()];
        for (int i = 0; i < slots.size(); i++) {
            ids[i] = slots.get(i).entityId;
        }
        Object packet = nms.remove(ids);
        if (packet != null) {
            packets.send(player, packet, false);
        }
        viewing.remove(player.getUniqueId());
        lastSent.remove(player.getUniqueId());
    }

    void despawnAll() {
        for (UUID uuid : Set.copyOf(viewing)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                remove(player);
            }
        }
        viewing.clear();
        lastSent.clear();
    }

    List<Player> viewers() {
        List<Player> out = new ArrayList<>();
        for (UUID uuid : viewing) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                out.add(player);
            }
        }
        return out;
    }

    void forget(UUID playerId) {
        viewing.remove(playerId);
        lastSent.remove(playerId);
    }

    private List<String> resolvedKeys(Player player, List<HologramLine> lines) {
        List<String> keys = new ArrayList<>(lines.size());
        for (HologramLine line : lines) {
            keys.add(switch (line.kind()) {
                case ANIM -> rt.animations.frame(line.value());
                case ITEM -> "#ICON:" + line.value();
                case TEXT -> rt.placeholders.apply(player, line.value());
            });
        }
        return keys;
    }

    private void sendLine(Player player, Slot slot, HologramLine line, String resolved, Location at) {
        if (line.kind() == HologramLine.Kind.ITEM) {
            Object add = nms.spawnItem(slot.entityId, slot.uuid, at);
            Object meta = nms.itemMetadata(slot.entityId, itemOf(line.value()));
            if (add != null) {
                packets.send(player, add, false);
            }
            if (meta != null) {
                packets.send(player, meta, false);
            }
            return;
        }
        Object add = nms.spawnLine(slot.entityId, slot.uuid, at, resolved);
        Object meta = nms.metadata(slot.entityId, resolved);
        if (add != null) {
            packets.send(player, add, false);
        }
        if (meta != null) {
            packets.send(player, meta, false);
        }
    }

    private static ItemStack itemOf(String material) {
        Material mat = Material.matchMaterial(material == null ? "" : material);
        if (mat == null || mat.isAir()) {
            mat = Material.STONE;
        }
        return new ItemStack(mat);
    }
}
