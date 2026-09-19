package com.yapcore.lib.dispatch;

import com.yapcore.lib.YapLibConfig;
import com.yapcore.lib.packet.PacketContainer;
import com.yapcore.lib.packet.PacketDirection;
import com.yapcore.lib.packet.PacketEvent;
import com.yapcore.lib.packet.PacketType;
import com.yapcore.lib.packet.PacketTypeResolver;
import com.yapcore.sched.YapSched;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public final class PacketDispatcher {

    private final JavaPlugin plugin;
    private final YapLibConfig config;
    private final ListenerRegistry registry;

    public PacketDispatcher(JavaPlugin plugin, YapLibConfig config, ListenerRegistry registry) {
        this.plugin = plugin;
        this.config = config;
        this.registry = registry;
    }

    public PacketEvent dispatchNetty(Player player, UUID playerId, PacketDirection direction, Object nmsPacket) {
        return dispatchNetty(player, playerId, direction, nmsPacket, null);
    }

    public PacketEvent dispatchNetty(Player player, UUID playerId, PacketDirection direction, Object nmsPacket,
                                     InetSocketAddress address) {
        PacketType type = PacketTypeResolver.resolve(nmsPacket);
        PacketContainer container = PacketContainer.wrap(nmsPacket, type);
        PacketEvent event = new PacketEvent(player, playerId, direction, container, true, address);
        fire(registry.netty(), event, type, direction);
        return event;
    }

    /**
     * True when a REGION listener may cancel/rewrite this packet. Holds before
     * {@code packet_handler} even during handshake/login (no Bukkit player yet).
     */
    public boolean shouldHold(Player player, PacketType type, PacketDirection direction) {
        return registry.hasRegionCancel(type, direction);
    }

    public void observeRegion(Player player, PacketEvent event) {
        if (registry.region().isEmpty()) {
            return;
        }
        PacketEvent observe = event.asObserveOnly();
        hop(player, () -> fire(registry.region(), observe, observe.type(), observe.direction()), () -> {
        });
        if (config.debugListeners()) {
            plugin.getLogger().fine("region-observe " + event.type() + " " + who(player, event));
        }
    }

    public void runRegion(Player player, PacketEvent event, Runnable after) {
        Runnable finish = after == null ? () -> {
        } : after;
        hop(player, () -> {
            try {
                fire(registry.region(), event, event.type(), event.direction());
            } finally {
                finish.run();
            }
        }, finish);
        if (config.debugListeners()) {
            plugin.getLogger().fine("region-hold " + event.type() + " " + who(player, event));
        }
    }

    private void hop(Player player, Runnable task, Runnable retired) {
        if (player != null && player.isOnline()) {
            try {
                player.getScheduler().run(plugin, st -> task.run(), retired);
                return;
            } catch (Throwable t) {
                try {
                    YapSched.entity(plugin, player, task);
                    return;
                } catch (RuntimeException ignored) {
                    // global
                }
            }
        }
        try {
            YapSched.global(plugin, task);
        } catch (RuntimeException e) {
            retired.run();
        }
    }

    private static String who(Player player, PacketEvent event) {
        if (player != null) {
            return player.getName();
        }
        return event.address() == null ? "handshake" : String.valueOf(event.address());
    }

    private void fire(List<ListenerRegistry.Binding> bindings, PacketEvent event,
                      PacketType type, PacketDirection direction) {
        for (ListenerRegistry.Binding binding : bindings) {
            if (!binding.plugin().isEnabled()) {
                continue;
            }
            if (!binding.listener().listening(type, direction)) {
                continue;
            }
            event.withPriority(binding.priority());
            try {
                binding.listener().onPacket(event);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING,
                        "YaPLib listener " + binding.listener().getClass().getName()
                                + " from " + binding.plugin().getName() + " failed", e);
            }
        }
    }
}
