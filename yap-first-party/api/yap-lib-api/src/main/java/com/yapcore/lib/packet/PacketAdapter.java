package com.yapcore.lib.packet;

import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.Set;

/**
 * Convenience listener with plugin ownership, priority, thread mode, and type filter.
 */
public abstract class PacketAdapter implements PacketListener {

    private final Plugin plugin;
    private final PacketPriority priority;
    private final PacketThreadMode threadMode;
    private final Set<PacketType> types;

    protected PacketAdapter(Plugin plugin, PacketType... types) {
        this(plugin, PacketPriority.NORMAL, PacketThreadMode.NETTY, types);
    }

    protected PacketAdapter(Plugin plugin, PacketPriority priority, PacketType... types) {
        this(plugin, priority, PacketThreadMode.NETTY, types);
    }

    protected PacketAdapter(Plugin plugin, PacketPriority priority, PacketThreadMode threadMode,
                            PacketType... types) {
        this.plugin = plugin;
        this.priority = priority == null ? PacketPriority.NORMAL : priority;
        this.threadMode = threadMode == null ? PacketThreadMode.NETTY : threadMode;
        if (types == null || types.length == 0) {
            this.types = Set.of(PacketType.ALL);
        } else {
            this.types = Set.of(types);
        }
    }

    public Plugin plugin() {
        return plugin;
    }

    @Override
    public PacketPriority priority() {
        return priority;
    }

    @Override
    public PacketThreadMode threadMode() {
        return threadMode;
    }

    public Set<PacketType> types() {
        return Collections.unmodifiableSet(types);
    }

    @Override
    public boolean listening(PacketType type, PacketDirection direction) {
        if (type == null) {
            return false;
        }
        for (PacketType wanted : types) {
            if (wanted.matches(type)) {
                if (wanted.direction() == null || wanted.direction() == direction) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public final void onPacket(PacketEvent event) {
        if (event.direction() == PacketDirection.CLIENTBOUND) {
            onPacketSending(event);
        } else {
            onPacketReceiving(event);
        }
    }

    public void onPacketReceiving(PacketEvent event) {
    }

    public void onPacketSending(PacketEvent event) {
    }
}
