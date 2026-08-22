package com.yapcore.link;

import com.yapcore.link.api.event.PingEvent;
import com.yapcore.link.backend.BackendMonitor;
import com.yapcore.link.protocol.McCodec;
import com.yapcore.link.status.ServerStatus;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;

/** Status ping passthrough and synthetic MOTD. */
final class ClientSessionStatusPing {

    private final ClientSession session;

    ClientSessionStatusPing(ClientSession session) {
        this.session = session;
    }

    void handleStatus(ChannelHandlerContext ctx, ByteBuf buf) {
        int packetId = McCodec.readVarInt(buf);
        if (packetId == 0x00) {
            buf.release();
            ByteBuf resp = Unpooled.buffer();
            McCodec.writeVarInt(resp, 0x00);
            McCodec.writeString(resp, statusJson());
            ctx.writeAndFlush(resp);
        } else if (packetId == 0x01) {
            long payload = buf.readLong();
            buf.release();
            ByteBuf pong = Unpooled.buffer();
            McCodec.writeVarInt(pong, 0x01);
            pong.writeLong(payload);
            ctx.writeAndFlush(pong);
        } else {
            buf.release();
        }
    }

    String statusJson() {
        LinkConfig cfg = session.server.config();
        BackendMonitor mon = session.server.backendMonitor();
        String json;
        if (cfg.pingPassthrough()) {
            if (session.forcedServerName != null) {
                BackendMonitor.Snapshot snap = mon.snapshot(session.forcedServerName);
                if (snap.up() && snap.status() != null) {
                    json = snap.status().rawJson();
                } else {
                    json = fallbackStatus(cfg, mon);
                }
            } else {
                json = fallbackStatus(cfg, mon);
            }
        } else {
            json = ServerStatus.synthetic(
                    cfg.motd(),
                    session.server.playerHub().onlineCount(),
                    cfg.maxPlayers(),
                    session.protocolVersion > 0 ? session.protocolVersion : 776,
                    "YaP Link"
            ).rawJson();
        }
        PingEvent ping = new PingEvent(session.protocolVersion, json);
        session.server.plugins().eventBus().fire(ping);
        return ping.statusJson();
    }

    private String fallbackStatus(LinkConfig cfg, BackendMonitor mon) {
        ServerStatus agg = mon.aggregateStatus();
        if (cfg.aggregatePlayerCount() && agg.online() >= 0) {
            int online = sumOnline(mon);
            int max = Math.max(cfg.maxPlayers(), agg.max());
            return agg.toStatusJson(online, max);
        }
        if (agg.rawJson() != null && !agg.rawJson().isBlank()) {
            return agg.rawJson();
        }
        return ServerStatus.synthetic(
                cfg.motd(),
                session.server.playerHub().onlineCount(),
                cfg.maxPlayers(),
                session.protocolVersion > 0 ? session.protocolVersion : 776,
                "YaP Link"
        ).rawJson();
    }

    private static int sumOnline(BackendMonitor mon) {
        int n = 0;
        for (var e : mon.allSnapshots().entrySet()) {
            if (e.getValue().up() && e.getValue().status() != null) {
                n += e.getValue().status().online();
            }
        }
        return n;
    }
}
