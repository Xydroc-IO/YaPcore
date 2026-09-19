package com.yapcore.holo.impl;

import com.yapcore.holo.Hologram;
import com.yapcore.lib.packet.PacketAdapter;
import com.yapcore.lib.packet.PacketEvent;
import com.yapcore.lib.packet.PacketTypes;
import com.yapcore.sched.YapSched;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Netty interact/attack → hologram click on the player's entity thread. */
public final class HologramClickListener extends PacketAdapter {

    private final JavaPlugin plugin;
    private final HologramServiceImpl holograms;
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();
    private final long cooldownMs;

    public HologramClickListener(JavaPlugin plugin, HologramServiceImpl holograms, int cooldownTicks) {
        super(plugin, PacketTypes.Play.Client.INTERACT, PacketTypes.Play.Client.ATTACK);
        this.plugin = plugin;
        this.holograms = holograms;
        this.cooldownMs = Math.max(1, cooldownTicks) * 50L;
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        Player player = event.player();
        if (player == null) {
            return;
        }
        Integer entityId = readEntityId(event);
        if (entityId == null) {
            return;
        }
        Hologram holo = holograms.byEntityId(entityId);
        if (holo == null || holo.clicks().isEmpty()) {
            return;
        }
        event.setCancelled(true);
        boolean left = isAttack(event);
        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = cooldown.get(uid);
        if (last != null && now - last < cooldownMs) {
            return;
        }
        cooldown.put(uid, now);
        YapSched.entity(plugin, player, () -> holo.handleClick(player, left));
    }

    static boolean isAttack(PacketEvent event) {
        if (event.type() != null && "attack".equals(event.type().name())) {
            return true;
        }
        Object handle = event.packet().handle();
        Class<?> type = handle.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                field.setAccessible(true);
                Object value;
                try {
                    value = field.get(handle);
                } catch (IllegalAccessException e) {
                    continue;
                }
                if (value == null) {
                    continue;
                }
                String name = value.getClass().getSimpleName().toLowerCase();
                if (name.contains("attack")) {
                    return true;
                }
            }
            type = type.getSuperclass();
        }
        return false;
    }

    static Integer readEntityId(PacketEvent event) {
        Integer boxed = event.packet().readOrNull(Integer.class, 0);
        if (boxed != null) {
            return boxed;
        }
        int count = event.packet().count(int.class);
        if (count > 0) {
            return event.packet().read(int.class, 0);
        }
        return null;
    }
}
