package com.yapcore.link.bedrock.session;

import com.yapcore.link.bedrock.codec.LinkCloudburstCodecs;
import com.yapcore.link.bedrock.crypto.BedrockEncryption;
import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
import com.yapcore.link.bedrock.floodgate.LinkFloodgateAuth;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.raknet.RakNetSessionManager;
import com.yapcore.link.bedrock.raknet.RakNetUnconnected;
import com.yapcore.link.bedrock.translator.BedrockActionTranslator;
import com.yapcore.link.bedrock.translator.BedrockCommandTranslator;
import com.yapcore.link.bedrock.translator.BedrockCombat;
import com.yapcore.link.bedrock.translator.BedrockFormBridge;
import com.yapcore.link.bedrock.translator.BedrockInventoryOpen;
import com.yapcore.link.bedrock.translator.BedrockInventoryTranslator;
import com.yapcore.link.bedrock.translator.BedrockMoveTranslator;
import com.yapcore.link.bedrock.translator.BedrockSetLocalPlayerAsInitializedTranslator;
import com.yapcore.link.bedrock.translator.ChatTranslator;
import com.yapcore.link.bedrock.translator.JavaBlockUpdateTranslator;
import com.yapcore.link.bedrock.translator.JavaCommandsTranslator;
import com.yapcore.link.bedrock.translator.JavaEntityCombatTranslator;
import com.yapcore.link.bedrock.translator.JavaEntityTranslator;
import com.yapcore.link.bedrock.translator.JavaInventoryTranslator;
import com.yapcore.link.bedrock.translator.JavaMoveTranslator;
import com.yapcore.link.bedrock.translator.JavaOpenScreenTranslator;
import com.yapcore.link.bedrock.translator.JavaPlayerListTranslator;
import com.yapcore.link.bedrock.translator.JavaSoundTranslator;
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
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.SecretKey;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientToServerHandshakePacket;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestChunkRadiusPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.common.util.VarInts;

/**
 * Link-native Bedrock UDP host: RakNet + Floodgate + encrypt + packs + Java downstream + join
 * + gameplay translators (Phases 3–6).
 *
 * <p>Phase 1 Done = packs COMPLETED. Phase 2 = {@link JavaDownstreamClient}. Phase 3 = join
 * until real {@code SetLocalPlayerAsInitialized} (0x71). Phases 4–6 = world/move/inv/entities/chat.
 */
public final class BedrockSessionHost {

    static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    static final int PACK_REFUSED = 1;
    static final int PACK_SEND_PACKS = 2;
    static final int PACK_HAVE_ALL = 3;
    static final int PACK_COMPLETED = 4;

    static final int ID_LOGIN = 0x01;
    static final int ID_CLIENT_TO_SERVER_HANDSHAKE = 0x04;
    static final int ID_RESOURCE_PACK_CLIENT_RESPONSE = 0x08;
    static final int ID_REQUEST_CHUNK_RADIUS = 0x45;
    static final int ID_SET_LOCAL_PLAYER_AS_INITIALIZED = 0x71;
    static final int ID_REQUEST_NETWORK_SETTINGS = 0xc1;

    public enum LoginPhase {
        NONE,
        AWAITING_ENCRYPTION,
        AWAITING_PACKS,
        AWAITING_STACK_COMPLETE,
        /** Phase 1 Done — packs COMPLETED; Phase 2/3 join in progress. */
        AWAITING_JOIN,
        JOINING,
        SPAWNED
    }

    final BedrockNativeConfig config;
    final long serverGuid;
    final RakNetSessionManager rakNet;
    final LinkFloodgateAuth floodgate;
    final Map<Long, ClientState> byGuid = new ConcurrentHashMap<>();
    final Map<String, Long> addrToGuid = new ConcurrentHashMap<>();

    EventLoopGroup group;
    final List<Channel> listens = new ArrayList<>();

    final BedrockSessionHostLogin loginLogic = new BedrockSessionHostLogin(this);
    final BedrockSessionHostPlay playLogic = new BedrockSessionHostPlay(this);

    public BedrockSessionHost(BedrockNativeConfig config) {
        this.config = config;
        this.serverGuid = ThreadLocalRandom.current().nextLong();
        this.rakNet = new RakNetSessionManager(serverGuid);
        this.floodgate = new LinkFloodgateAuth(true);
        this.rakNet.setGamePacketHandler(this::onGameBatch);
        this.rakNet.setDisconnectHandler(this::onDisconnect);
    }

    public synchronized void start() throws InterruptedException {
        if (!listens.isEmpty()) {
            return;
        }
        BedrockJoinProbe.startHome(config.linkHome());
        group = new NioEventLoopGroup(2);
        Exception firstFailure = null;
        for (InetSocketAddress bind : config.bindAddresses()) {
            try {
                Bootstrap b = new Bootstrap();
                b.group(group)
                        .channel(NioDatagramChannel.class)
                        .option(ChannelOption.SO_BROADCAST, true)
                        .option(ChannelOption.SO_REUSEADDR, true)
                        .handler(new ChannelInitializer<NioDatagramChannel>() {
                            @Override
                            protected void initChannel(NioDatagramChannel ch) {
                                ch.pipeline().addLast(new ListenHandler());
                            }
                        });
                Channel ch = b.bind(bind).sync().channel();
                listens.add(ch);
                LOG.info("Bedrock native UDP " + bind + " — Phase 2/3 session host");
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Bedrock native bind failed on " + bind + ": " + e.getMessage(), e);
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        if (listens.isEmpty()) {
            stop();
            if (firstFailure instanceof InterruptedException ie) {
                throw ie;
            }
            throw new IllegalStateException(
                    "Bedrock native bind failed for all addresses: " + config.bindAddresses(),
                    firstFailure);
        }
        LOG.info("Bedrock native mode on — javaBackend=" + config.javaBackend()
                + " floodgateKey=" + config.floodgateKeyPath());
    }

    public synchronized void stop() {
        for (ClientState state : byGuid.values()) {
            LinkBedrockSession join = state.joinSession;
            state.joinSession = null;
            if (join != null) {
                join.closeFromBedrock("host_stop");
            }
            closeDownstream(state);
        }
        for (Channel listen : listens) {
            listen.close().syncUninterruptibly();
        }
        listens.clear();
        byGuid.clear();
        addrToGuid.clear();
        if (group != null) {
            if (!group.shutdownGracefully(0, 1, java.util.concurrent.TimeUnit.SECONDS)
                    .awaitUninterruptibly(2, java.util.concurrent.TimeUnit.SECONDS)) {
                LOG.warning("Bedrock session host EventLoopGroup shutdown timed out");
            }
            group = null;
        }
        LOG.info("Bedrock native session host stopped");
    }

    public InetSocketAddress javaBackend() {
        return config.javaBackend();
    }

    private void onDisconnect(RakNetSessionManager.RakNetPeer peer) {
        Long guid = addrToGuid.remove(peer.address().toString());
        if (guid == null) {
            guid = peer.state().clientGuid();
        }
        ClientState state = byGuid.remove(guid);
        if (state != null) {
            // Prefer session helper: closes JE + drains buffered chunks + finishes probe.
            LinkBedrockSession join = state.joinSession;
            state.joinSession = null;
            if (join != null) {
                join.closeFromBedrock("disconnect");
            } else {
                closeDownstream(state);
                if (guid != null && BedrockJoinProbe.isActive(guid)) {
                    BedrockJoinProbe.finish(guid, "disconnect");
                }
            }
            // state.downstream may still be set if joinSession was null
            closeDownstream(state);
            LOG.info("BE native session end user=" + state.username
                    + " guid=" + Long.toHexString(guid)
                    + " phase=" + state.phase);
        } else if (guid != null && BedrockJoinProbe.isActive(guid)) {
            // Abrupt drop after state already cleared — still flush probe.
            BedrockJoinProbe.finish(guid, "disconnect_orphan");
        }
    }

    private void onGameBatch(RakNetSessionManager.RakNetPeer peer, ByteBuf batch) {
        long guid = peer.state().clientGuid();
        if (guid == 0L) {
            guid = peer.address().hashCode();
        }
        addrToGuid.put(peer.address().toString(), guid);
        if (BedrockJoinProbe.isActive(guid)) {
            BedrockJoinProbe.noteUdpIn(guid, batch.readableBytes());
        }
        String address = peer.address().toString();
        ClientState state = byGuid.computeIfAbsent(guid, g -> new ClientState(g, peer));
        state.peer = peer;

        while (batch.isReadable()) {
            try {
                int len = readUnsignedVarInt(batch);
                if (len <= 0 || batch.readableBytes() < len) {
                    break;
                }
                ByteBuf pkt = batch.readSlice(len);
                ByteBuf body = pkt.duplicate();
                int id = readUnsignedVarInt(body);
                if (BedrockJoinProbe.isActive(guid)) {
                    BedrockJoinProbe.noteC2S(guid, id, len);
                }
                playLogic.handlePacket(state, address, id, body);
            } catch (Exception e) {
                LOG.info("BE native batch parse fail: " + e.getMessage());
                break;
            }
        }
        batch.release();
    }













    void closeDownstream(ClientState state) {
        if (state == null) {
            return;
        }
        JavaDownstreamClient down = state.downstream;
        state.downstream = null;
        if (down != null) {
            down.close();
        }
    }

    void sendPacket(ClientState state, BedrockPacket packet) {
        sendPackets(state, List.of(packet));
    }

    void sendPackets(ClientState state, List<? extends BedrockPacket> packets) {
        if (state.codec == null || state.peer == null || packets == null || packets.isEmpty()) {
            return;
        }
        // Prefer the socket the client connected to (:19132 vs shared :25565).
        Channel ch = state.peer.listenChannel();
        if (ch == null || !ch.isActive()) {
            ch = edgeChannel();
        }
        if (ch == null) {
            return;
        }
        for (BedrockPacket packet : packets) {
            if (packet == null) {
                continue;
            }
            int packetId = -1;
            try {
                packetId = state.codec.packetId(packet.getClass());
            } catch (Exception ignored) {
                // leave -1
            }
            ByteBuf encoded;
            try {
                encoded = state.codec.encode(packet);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "BE encode fail id=0x" + Integer.toHexString(packetId)
                        + " " + packet.getClass().getSimpleName()
                        + " user=" + state.username + ": " + e.getMessage(), e);
                if (BedrockJoinProbe.isActive(state.guid)) {
                    BedrockJoinProbe.noteEvent(state.guid,
                            "be_encode_fail " + packet.getClass().getSimpleName()
                                    + " " + e.getMessage());
                }
                // LevelChunk encode failure is fatal for the Bedrock peer — close JE so Folia
                // does not keep a ghost after the client crashes on a bad column.
                if (packet instanceof LevelChunkPacket) {
                    LinkBedrockSession join = state.joinSession;
                    if (join != null) {
                        join.closeFromBedrock("level_chunk_encode_fail");
                    } else {
                        closeDownstream(state);
                        if (BedrockJoinProbe.isActive(state.guid)) {
                            BedrockJoinProbe.finish(state.guid, "level_chunk_encode_fail");
                        }
                    }
                    return;
                }
                continue;
            }
            if (BedrockJoinProbe.isActive(state.guid)) {
                BedrockJoinProbe.noteS2C(state.guid, packetId,
                        packet.getClass().getSimpleName(), encoded.readableBytes());
            }
            ByteBuf batch = Unpooled.buffer(encoded.readableBytes() + 8);
            writeUnsignedVarInt(batch, encoded.readableBytes());
            batch.writeBytes(encoded);
            encoded.release();
            for (ByteBuf framed : rakNet.encapsulateGameDatagrams(state.peer, batch)) {
                if (BedrockJoinProbe.isActive(state.guid)) {
                    BedrockJoinProbe.noteUdpOut(state.guid, framed.readableBytes());
                }
                ch.writeAndFlush(new DatagramPacket(framed, state.peer.address()));
            }
            batch.release();
        }
    }

    Channel edgeChannel() {
        for (Channel c : listens) {
            if (c != null && c.isActive()) {
                return c;
            }
        }
        return null;
    }



    static int readUnsignedVarInt(ByteBuf in) {
        return (int) VarInts.readUnsignedInt(in);
    }

    static void writeUnsignedVarInt(ByteBuf out, int value) {
        VarInts.writeUnsignedInt(out, value);
    }

    private final class ListenHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
            InetSocketAddress client = msg.sender();
            ByteBuf content = msg.content();

            if (RakNetUnconnected.isUnconnectedPing(content)) {
                replyUnconnectedPong(ctx, client, content);
                return;
            }

            // Pin replies to this listen socket before handle() may emit game batches.
            rakNet.peer(client).setListenChannel(ctx.channel());
            List<ByteBuf> replies = rakNet.handle(client, content);
            for (ByteBuf reply : replies) {
                ctx.writeAndFlush(new DatagramPacket(reply, client));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOG.log(Level.FINE, "bedrock native listen", cause);
        }
    }

    private void replyUnconnectedPong(ChannelHandlerContext ctx,
                                      InetSocketAddress client,
                                      ByteBuf ping) {
        long pingTime;
        try {
            pingTime = RakNetUnconnected.readPingTime(ping);
        } catch (Exception ignored) {
            pingTime = System.currentTimeMillis();
        }
        int edgePort = edgePort(ctx.channel());
        String motd = RakNetUnconnected.buildMotd(
                config.motd(),
                config.motdProtocol(),
                config.motdVersion(),
                Math.max(0, config.onlineCount().getAsInt()),
                config.maxPlayers(),
                serverGuid,
                config.motdSub(),
                edgePort,
                edgePort);
        ByteBuf pong = RakNetUnconnected.buildPong(pingTime, serverGuid, motd);
        ctx.writeAndFlush(new DatagramPacket(pong, client));
    }

    private static int edgePort(Channel channel) {
        if (channel != null && channel.localAddress() instanceof InetSocketAddress local) {
            return local.getPort();
        }
        return 19132;
    }

    static final class ClientState {
        final long guid;
        volatile RakNetSessionManager.RakNetPeer peer;
        volatile int protocol = 2169;
        volatile String username = "BedrockPlayer";
        volatile LinkFloodgateAuth.Identity identity;
        volatile LinkCloudburstCodecs.Session codec;
        volatile LoginPhase phase = LoginPhase.NONE;
        volatile LinkBedrockSession joinSession;
        volatile JavaDownstreamClient downstream;

        ClientState(long guid, RakNetSessionManager.RakNetPeer peer) {
            this.guid = guid;
            this.peer = peer;
        }
    }
}
