package com.yapcore.lib.inject;

import com.yapcore.lib.packet.PacketContainer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.bukkit.entity.Player;

import java.util.Collection;

/** Send / receive through the injected pipeline. */
public final class PacketSendOps {

    private final ConnectionTracker tracker;

    public PacketSendOps(ConnectionTracker tracker) {
        this.tracker = tracker;
    }

    public void send(Player player, PacketContainer packet, boolean filters) {
        if (player == null || packet == null) {
            return;
        }
        Object handle = packet.handle();
        Channel channel = tracker.channel(player);
        if (channel == null || !channel.isOpen()) {
            return;
        }
        if (filters) {
            channel.eventLoop().execute(() -> channel.writeAndFlush(handle));
            return;
        }
        channel.eventLoop().execute(() -> {
            ChannelHandlerContext ctx = channel.pipeline().context(PacketChannelHandler.NAME);
            if (ctx != null) {
                ctx.writeAndFlush(handle);
                return;
            }
            if (!ChannelAccess.send(player, handle)) {
                channel.writeAndFlush(handle);
            }
        });
    }

    public void receive(Player player, PacketContainer packet, boolean filters) {
        if (player == null || packet == null) {
            return;
        }
        Object handle = packet.handle();
        Channel channel = tracker.channel(player);
        if (channel == null || !channel.isOpen()) {
            return;
        }
        channel.eventLoop().execute(() -> {
            ChannelHandlerContext yap = channel.pipeline().context(PacketChannelHandler.NAME);
            ChannelHandlerContext game = channel.pipeline().context("packet_handler");
            if (filters) {
                channel.pipeline().fireChannelRead(handle);
                return;
            }
            if (game != null) {
                game.fireChannelRead(handle);
            } else if (yap != null) {
                yap.fireChannelRead(handle);
            }
        });
    }

    public void broadcast(PacketContainer packet, boolean filters, Collection<? extends Player> viewers) {
        if (packet == null || viewers == null) {
            return;
        }
        for (Player player : viewers) {
            send(player, packet, filters);
        }
    }
}
