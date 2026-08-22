package com.yapcore.protocol.gateway;

import com.yapcore.client.ClientEdition;
import com.yapcore.client.ClientSession;
import com.yapcore.config.ServerConfig;
import com.yapcore.crossplay.bedrock.BedrockPacketCodec;
import com.yapcore.crossplay.bedrock.BedrockSessionManager;
import com.yapcore.crossplay.floodgate.FloodgateAuth;
import com.yapcore.crossplay.raknet.RakNetReliability;
import com.yapcore.crossplay.raknet.RakNetSessionManager;
import com.yapcore.crossplay.raknet.RakNetUnconnected;
import com.yapcore.model.GameEvent;
import com.yapcore.network.TrafficCop;
import com.yapcore.protocol.DualStackGateway;
import com.yapcore.protocol.ProtocolVersionRegistry;
import com.yapcore.resourcepack.ResourcePackOffer;
import com.yapcore.util.ThreadMetrics;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.CharsetUtil;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Bedrock Edition UDP listener and RakNet / text-protocol handler.
 */
public final class BedrockUdpBoot {

    private static final Logger LOG = Logger.getLogger("YaPcore.Gateway");

    private BedrockUdpBoot() {
    }

    public static void start(DualStackGateway gateway) {
        ServerConfig config = gateway.config();
        if (!config.isBedrockEnabled()) {
            return;
        }
        EventLoopGroup group = new NioEventLoopGroup(2);
        Bootstrap udp = new Bootstrap();
        udp.group(group)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, true)
                .option(ChannelOption.SO_REUSEADDR, true)
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(NioDatagramChannel ch) {
                        ch.pipeline().addLast(new BedrockUdpHandler(gateway));
                    }
                });
        int bedrockPort = config.effectiveBedrockPort();
        try {
            Channel channel = udp.bind(
                    config.getBindHost().equals("0.0.0.0") ? "0.0.0.0" : config.getBindHost(),
                    bedrockPort).sync().channel();
            gateway.setBedrockTransport(group, channel);
            if (config.isSharedListenPort()) {
                LOG.info("Bedrock Edition UDP on :" + bedrockPort
                        + " (shared with Java TCP — one join port)");
            } else {
                LOG.info("Bedrock Edition UDP listener on :" + bedrockPort
                        + " (backwards-compat=" + gateway.getProtocols().isLenient() + ")");
            }
            ThreadMetrics.record("Gateway", "bedrock-online");
        } catch (Exception e) {
            LOG.warning("Bedrock UDP port " + bedrockPort + " bind failed: " + e.getMessage());
            group.shutdownGracefully();
        }
    }

    public static void shutdown(DualStackGateway gateway) {
        Channel channel = gateway.bedrockChannel();
        if (channel != null) {
            channel.close();
        }
        EventLoopGroup group = gateway.bedrockGroup();
        if (group != null) {
            group.shutdownGracefully();
            gateway.clearBedrockTransport();
        }
    }

    /**
     * Bedrock UDP: RakNet reliability + BE gameplay codecs + text JOIN fallback (Geyser 4.G1–G4).
     */
    static final class BedrockUdpHandler extends SimpleChannelInboundHandler<DatagramPacket> {

        private final DualStackGateway gateway;
        private final ServerConfig config;
        private final ProtocolVersionRegistry protocols;
        private final TrafficCop trafficCop;
        private final BedrockSessionManager bedrockSessions;
        private final FloodgateAuth floodgateAuth;

        private final long serverGuid = java.util.concurrent.ThreadLocalRandom.current().nextLong();
        private final RakNetSessionManager rakNet = new RakNetSessionManager(serverGuid);
        private final ConcurrentHashMap<Long, InetSocketAddress> guidToAddr = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Long> addrToGuid = new ConcurrentHashMap<>();

        BedrockUdpHandler(DualStackGateway gateway) {
            this.gateway = gateway;
            this.config = gateway.config();
            this.protocols = gateway.getProtocols();
            this.trafficCop = gateway.trafficCop();
            this.bedrockSessions = gateway.bedrockSessions();
            this.floodgateAuth = gateway.floodgateAuth();
            gateway.setRakNetSessions(rakNet);
            gateway.bedrockBridge().setCompressionArmed(guid -> {
                InetSocketAddress addr = guidToAddr.get(guid);
                if (addr != null) {
                    rakNet.peer(addr).setGameCompressionHeader(true);
                }
            });
            gateway.bedrockBridge().setOutbound((guid, packets) -> {
                InetSocketAddress addr = guidToAddr.get(guid);
                Channel bedrockChannel = gateway.bedrockChannel();
                if (addr == null || bedrockChannel == null) {
                    LOG.warning(
                            "BE outbound drop guid=" + Long.toHexString(guid)
                                    + " addr=" + addr + " ch=" + (bedrockChannel != null));
                    for (ByteBuf pkt : packets) {
                        pkt.release();
                    }
                    return;
                }
                RakNetSessionManager.RakNetPeer peer = rakNet.peer(addr);
                for (ByteBuf pkt : packets) {
                    ByteBuf batch = Unpooled.buffer(pkt.readableBytes() + 8);
                    BedrockPacketCodec.writeUnsignedVarInt(batch, pkt.readableBytes());
                    batch.writeBytes(pkt);
                    pkt.release();
                    for (ByteBuf framed : rakNet.encapsulateGameDatagrams(peer, batch)) {
                        bedrockChannel.writeAndFlush(new DatagramPacket(framed, addr));
                    }
                    batch.release();
                }
            });
            gateway.formService().setSender((user, buf) -> {
                BedrockSessionManager.BedrockSession s = bedrockSessions.byUsername(user);
                if (s == null) {
                    buf.release();
                    return;
                }
                InetSocketAddress addr = guidToAddr.get(s.guid());
                Channel bedrockChannel = gateway.bedrockChannel();
                if (addr == null || bedrockChannel == null) {
                    buf.release();
                    return;
                }
                ByteBuf batch = Unpooled.buffer(buf.readableBytes() + 8);
                BedrockPacketCodec.writeUnsignedVarInt(batch, buf.readableBytes());
                batch.writeBytes(buf);
                buf.release();
                for (ByteBuf framed : rakNet.encapsulateGameDatagrams(rakNet.peer(addr), batch)) {
                    bedrockChannel.writeAndFlush(new DatagramPacket(framed, addr));
                }
                batch.release();
            });
            rakNet.setGamePacketHandler((peer, gameBatch) -> {
                long guid = peer.state().clientGuid();
                if (guid == 0L) {
                    guid = peer.address().hashCode();
                }
                guidToAddr.put(guid, peer.address());
                addrToGuid.put(peer.address().toString(), guid);
                var actions = gateway.bedrockBridge().onGameBatch(guid, peer.address().toString(), gameBatch);
                gameBatch.release();
                for (var action : actions) {
                    applyBedrockAction(action.type(), action.username(), action.payload(), peer.address());
                }
            });
            rakNet.setDisconnectHandler(peer -> {
                long guid = peer.state().clientGuid();
                if (guid == 0L) {
                    guid = peer.address().hashCode();
                }
                var session = bedrockSessions.get(guid);
                String user = session != null ? session.username() : null;
                gateway.bedrockBridge().onDisconnect(guid);
                if (user != null) {
                    gateway.getClients().get(user).ifPresent(s -> {
                        if (gateway.crossplay() != null) {
                            gateway.crossplay().leave(s);
                        }
                        gateway.getClients().unregister(s);
                    });
                    trafficCop.ingest(new GameEvent(GameEvent.Type.CLIENT_LEAVE, user, Map.of(
                            "edition", "BEDROCK"
                    )));
                }
                guidToAddr.remove(guid);
                addrToGuid.remove(peer.address().toString());
            });
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
            ByteBuf content = packet.content();
            InetSocketAddress sender = packet.sender();

            if (config.isProtocolGeyserEnabled() && RakNetUnconnected.isUnconnectedPing(content)) {
                long pingTime = 0L;
                try {
                    pingTime = RakNetUnconnected.readPingTime(content);
                } catch (Exception ignored) {
                    pingTime = System.currentTimeMillis();
                }
                String motd = "MCPE;" + config.getMotd().replace(';', ' ') + ";"
                        + protocols.recommended(ClientEdition.BEDROCK).protocolId() + ";"
                        + protocols.recommended(ClientEdition.BEDROCK).minecraftVersion() + ";"
                        + gateway.getClients().size() + ";"
                        + config.getMaxPlayers() + ";"
                        + Long.toUnsignedString(serverGuid) + ";"
                        + "YaPcore;Survival;";
                ByteBuf pong = RakNetUnconnected.buildPong(pingTime, serverGuid, motd);
                ctx.writeAndFlush(new DatagramPacket(pong, sender));
                ThreadMetrics.bump("Gateway", "bedrock-ping");
                return;
            }

            if (config.isProtocolGeyserEnabled()) {
                int id = content.isReadable() ? content.getUnsignedByte(content.readerIndex()) : -1;
                if (id == RakNetReliability.ID_OPEN_CONNECTION_REQUEST_1
                        || id == RakNetReliability.ID_OPEN_CONNECTION_REQUEST_2
                        || RakNetReliability.isFrameSet(id)
                        || id == RakNetReliability.ID_ACK
                        || id == RakNetReliability.ID_NACK) {
                    ByteBuf copy = content.retainedDuplicate();
                    try {
                        for (ByteBuf reply : rakNet.handle(sender, copy)) {
                            ctx.writeAndFlush(new DatagramPacket(reply, sender));
                        }
                    } finally {
                        copy.release();
                    }
                    ThreadMetrics.bump("Gateway", "bedrock-raknet");
                    return;
                }
            }

            String raw = content.toString(CharsetUtil.UTF_8).trim();
            if (raw.isEmpty()) {
                return;
            }
            String[] parts = raw.split("\\|");
            String cmd = parts[0].trim().toUpperCase();
            if ("JOIN".equals(cmd)) {
                String user = parts.length > 1 ? parts[1] : "BedrockPlayer";
                int proto = parts.length > 2 ? GatewayParse.parseInt(parts[2],
                        protocols.recommended(ClientEdition.BEDROCK).protocolId())
                        : protocols.recommended(ClientEdition.BEDROCK).protocolId();
                long guid = sender.hashCode() * 31L + user.hashCode();
                FloodgateAuth.Identity identity = floodgateAuth.register(new FloodgateAuth.Identity(
                        user,
                        FloodgateAuth.uuidFromXuid(user + "|" + sender).toString().replace("-", "").substring(0, 16),
                        FloodgateAuth.uuidFromXuid(user + "|" + sender),
                        proto,
                        false,
                        ""));
                bedrockSessions.open(guid, user, proto, sender.toString());
                gateway.skinService().registerDefault(user, identity.javaUuid());
                guidToAddr.put(guid, sender);
                ClientSession session = gateway.acceptClient(user, ClientEdition.BEDROCK, proto, sender);
                if (session != null) {
                    String reply = "OK|BEDROCK|" + session.getProtocol().minecraftVersion()
                            + "|pack=" + session.getActiveOffer().map(ResourcePackOffer::url).orElse("none")
                            + "|geyser=yap|floodgate=" + identity.javaUuid();
                    ctx.writeAndFlush(new DatagramPacket(
                            Unpooled.copiedBuffer(reply, StandardCharsets.UTF_8), sender));
                }
            } else if ("PACK".equals(cmd)) {
                String user = parts.length > 1 ? parts[1] : "BedrockPlayer";
                String state = parts.length > 2 ? parts[2] : "LOADED";
                try {
                    gateway.handlePackStatus(user, ClientSession.ResourcePackState.valueOf(state.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    gateway.handlePackStatus(user, ClientSession.ResourcePackState.FAILED);
                }
            } else if ("LEAVE".equals(cmd)) {
                String user = parts.length > 1 ? parts[1] : "BedrockPlayer";
                BedrockSessionManager.BedrockSession bs = bedrockSessions.byUsername(user);
                if (bs != null) {
                    gateway.bedrockBridge().onDisconnect(bs.guid());
                }
                gateway.getClients().get(user).ifPresent(s -> {
                    if (gateway.crossplay() != null) {
                        gateway.crossplay().leave(s);
                    }
                    gateway.getClients().unregister(s);
                });
                trafficCop.ingest(new GameEvent(GameEvent.Type.CLIENT_LEAVE, user, Map.of(
                        "edition", "BEDROCK"
                )));
            } else if ("MOVE".equals(cmd) || "CHAT".equals(cmd) || "INTERACT".equals(cmd)
                    || "BREAK".equals(cmd) || "PLACE".equals(cmd) || "ATTACK".equals(cmd)
                    || "INV".equals(cmd) || "HOTBAR".equals(cmd)
                    || "FORM".equals(cmd) || "SKIN".equals(cmd)) {
                String user = parts.length > 1 ? parts[1] : "BedrockPlayer";
                if ("FORM".equals(cmd) && parts.length > 2) {
                    gateway.formService().sendSimple(user, "YaPcore", parts[2], "OK", "Cancel");
                    return;
                }
                gateway.getClients().get(user).ifPresent(s -> {
                    if (gateway.crossplay() != null) {
                        java.util.concurrent.ConcurrentHashMap<String, String> map =
                                new java.util.concurrent.ConcurrentHashMap<>();
                        for (int i = 2; i + 1 < parts.length; i += 2) {
                            map.put(parts[i], parts[i + 1]);
                        }
                        gateway.crossplay().handleAction(s, cmd, map);
                    }
                });
            }
        }

        private void applyBedrockAction(String type, String user, Map<String, String> payload,
                                        InetSocketAddress sender) {
            if ("JOIN".equalsIgnoreCase(type)) {
                int proto = GatewayParse.parseInt(payload.getOrDefault("protocol",
                        Integer.toString(protocols.recommended(ClientEdition.BEDROCK).protocolId())),
                        protocols.recommended(ClientEdition.BEDROCK).protocolId());
                gateway.acceptClient(user, ClientEdition.BEDROCK, proto, sender);
                return;
            }
            gateway.getClients().get(user).ifPresent(s -> {
                if (gateway.crossplay() != null) {
                    gateway.crossplay().handleAction(s, type, payload);
                }
            });
        }
    }
}
