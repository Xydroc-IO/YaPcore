package com.yapcore.lib.dispatch;

import com.yapcore.lib.PacketService;
import com.yapcore.lib.inject.ChannelAccess;
import com.yapcore.lib.inject.ConnectionTracker;
import com.yapcore.lib.inject.PacketSendOps;
import com.yapcore.lib.nms.PacketAllocator;
import com.yapcore.lib.packet.PacketContainer;
import com.yapcore.lib.packet.PacketListener;
import com.yapcore.lib.packet.PacketType;
import com.yapcore.lib.packet.PacketTypeResolver;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;

public final class PacketServiceImpl implements PacketService {

    private final ListenerRegistry registry;
    private final ConnectionTracker tracker;
    private final PacketSendOps sendOps;
    private final PacketAllocator allocator;

    public PacketServiceImpl(ListenerRegistry registry, ConnectionTracker tracker, PacketSendOps sendOps,
                             PacketAllocator allocator) {
        this.registry = registry;
        this.tracker = tracker;
        this.sendOps = sendOps;
        this.allocator = allocator;
    }

    @Override
    public void addListener(Plugin plugin, PacketListener listener) {
        registry.add(plugin, listener);
    }

    @Override
    public void removeListener(PacketListener listener) {
        registry.remove(listener);
    }

    @Override
    public void removeListeners(Plugin plugin) {
        registry.remove(plugin);
    }

    @Override
    public int listenerCount() {
        return registry.size();
    }

    @Override
    public PacketContainer createPacket(PacketType type) {
        return allocator.create(type);
    }

    @Override
    public Class<?> nmsClass(PacketType type) {
        return allocator.nmsClass(type);
    }

    @Override
    public PacketType typeOf(Object nmsPacket) {
        return PacketTypeResolver.resolve(nmsPacket);
    }

    @Override
    public PacketContainer wrap(Object nmsPacket) {
        return PacketContainer.wrap(nmsPacket, PacketTypeResolver.resolve(nmsPacket));
    }

    @Override
    public void send(Player player, PacketContainer packet, boolean filters) {
        sendOps.send(player, packet, filters);
    }

    @Override
    public void send(Player player, Object nmsPacket, boolean filters) {
        send(player, wrap(nmsPacket), filters);
    }

    @Override
    public void receive(Player player, PacketContainer packet, boolean filters) {
        sendOps.receive(player, packet, filters);
    }

    @Override
    public void receive(Player player, Object nmsPacket, boolean filters) {
        receive(player, wrap(nmsPacket), filters);
    }

    @Override
    public void broadcast(PacketContainer packet, boolean filters, Collection<? extends Player> viewers) {
        sendOps.broadcast(packet, filters, viewers);
    }

    @Override
    public boolean hasChannel(Player player) {
        return tracker.channel(player) != null || ChannelAccess.channel(player) != null;
    }
}
