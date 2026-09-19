package com.yapcore.lib.inject;

import com.yapcore.lib.YapLibConfig;
import com.yapcore.lib.dispatch.PacketDispatcher;
import com.yapcore.lib.packet.PacketDirection;
import com.yapcore.lib.packet.PacketEvent;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;

public final class PacketChannelHandler extends ChannelDuplexHandler {

    public static final String NAME = "yaplib-packets";

    private final ConnectionTracker tracker;
    private final PacketDispatcher dispatcher;
    private final YapLibConfig config;
    private final PacketHoldGate<Object> inbound = new PacketHoldGate<>();
    private final PacketHoldGate<Outbound> outbound = new PacketHoldGate<>();

    public PacketChannelHandler(ConnectionTracker tracker, PacketDispatcher dispatcher, YapLibConfig config) {
        this.tracker = tracker;
        this.dispatcher = dispatcher;
        this.config = config;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!config.intercept() || skip(msg)) {
            super.channelRead(ctx, msg);
            return;
        }
        if (!inbound.acceptNow()) {
            inbound.enqueue(msg);
            return;
        }
        processInbound(ctx, msg);
        drainInbound(ctx);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!config.intercept() || skip(msg)) {
            super.write(ctx, msg, promise);
            return;
        }
        Outbound item = new Outbound(msg, promise);
        if (!outbound.acceptNow()) {
            outbound.enqueue(item);
            return;
        }
        processOutbound(ctx, item);
        drainOutbound(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        inbound.snapshotAndClear();
        List<Outbound> dropped = outbound.snapshotAndClear();
        for (Outbound item : dropped) {
            item.promise.setSuccess();
        }
        tracker.unbind(ctx.channel());
        super.channelInactive(ctx);
    }

    private void processInbound(ChannelHandlerContext ctx, Object msg) throws Exception {
        Player player = tracker.player(ctx.channel());
        UUID id = player == null ? null : player.getUniqueId();
        PacketEvent event = dispatcher.dispatchNetty(player, id, PacketDirection.SERVERBOUND, msg, address(ctx));
        if (event.cancelled()) {
            return;
        }
        if (dispatcher.shouldHold(player, event.type(), event.direction())) {
            inbound.hold();
            dispatcher.runRegion(player, event, () -> ctx.channel().eventLoop().execute(() -> {
                inbound.release();
                if (!event.cancelled() && ctx.channel().isActive()) {
                    try {
                        super.channelRead(ctx, event.packet().handle());
                    } catch (Exception e) {
                        ctx.fireExceptionCaught(e);
                    }
                }
                try {
                    drainInbound(ctx);
                } catch (Exception e) {
                    ctx.fireExceptionCaught(e);
                }
            }));
            return;
        }
        dispatcher.observeRegion(player, event);
        super.channelRead(ctx, event.packet().handle());
    }

    private void drainInbound(ChannelHandlerContext ctx) throws Exception {
        Object next;
        while ((next = inbound.poll()) != null) {
            processInbound(ctx, next);
            if (inbound.held()) {
                return;
            }
        }
    }

    private void processOutbound(ChannelHandlerContext ctx, Outbound item) throws Exception {
        Player player = tracker.player(ctx.channel());
        UUID id = player == null ? null : player.getUniqueId();
        PacketEvent event = dispatcher.dispatchNetty(player, id, PacketDirection.CLIENTBOUND, item.msg, address(ctx));
        if (event.cancelled()) {
            item.promise.setSuccess();
            return;
        }
        if (dispatcher.shouldHold(player, event.type(), event.direction())) {
            outbound.hold();
            dispatcher.runRegion(player, event, () -> ctx.channel().eventLoop().execute(() -> {
                outbound.release();
                if (event.cancelled() || !ctx.channel().isActive()) {
                    item.promise.setSuccess();
                } else {
                    try {
                        super.write(ctx, event.packet().handle(), item.promise);
                    } catch (Exception e) {
                        item.promise.tryFailure(e);
                    }
                }
                try {
                    drainOutbound(ctx);
                } catch (Exception e) {
                    ctx.fireExceptionCaught(e);
                }
            }));
            return;
        }
        dispatcher.observeRegion(player, event);
        super.write(ctx, event.packet().handle(), item.promise);
    }

    private void drainOutbound(ChannelHandlerContext ctx) throws Exception {
        Outbound next;
        while ((next = outbound.poll()) != null) {
            processOutbound(ctx, next);
            if (outbound.held()) {
                return;
            }
        }
    }

    private static boolean skip(Object msg) {
        return msg == null || msg instanceof ByteBuf;
    }

    private static InetSocketAddress address(ChannelHandlerContext ctx) {
        if (ctx.channel().remoteAddress() instanceof InetSocketAddress inet) {
            return inet;
        }
        return null;
    }

    public static void inject(Channel channel, PacketChannelHandler handler) {
        if (channel == null || !channel.isOpen()) {
            return;
        }
        channel.eventLoop().execute(() -> {
            try {
                if (channel.pipeline().get(NAME) != null) {
                    return;
                }
                if (channel.pipeline().get("packet_handler") == null) {
                    return;
                }
                channel.pipeline().addBefore("packet_handler", NAME, handler);
            } catch (RuntimeException ignored) {
                // pipeline not ready
            }
        });
    }

    public static void eject(Channel channel) {
        if (channel == null) {
            return;
        }
        Runnable drop = () -> {
            try {
                if (channel.pipeline().get(NAME) != null) {
                    channel.pipeline().remove(NAME);
                }
            } catch (RuntimeException ignored) {
                // already gone
            }
        };
        if (channel.eventLoop().inEventLoop()) {
            drop.run();
        } else {
            channel.eventLoop().execute(drop);
        }
    }

    private record Outbound(Object msg, ChannelPromise promise) {
    }
}
