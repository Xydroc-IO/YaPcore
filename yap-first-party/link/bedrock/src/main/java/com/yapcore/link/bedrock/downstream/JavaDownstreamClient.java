package com.yapcore.link.bedrock.downstream;

import com.yapcore.protocol.McCodec;
import com.yapcore.protocol.McCompressionCodec;
import com.yapcore.protocol.McFrameCodec;
import com.yapcore.protocol.McOutboundPacketEncoder;
import com.yapcore.protocol.forwarding.ModernForwarding;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Netty TCP Java Edition client for one Bedrock session (Link → Folia/chassis).
 *
 * <p>Handshake as offline / online-mode=false using Floodgate username+UUID.
 * Injects Velocity modern {@code velocity:player_info} when {@code forwarding.secret} exists.
 * Progresses Login → Configuration → Play; notifies {@link Listener} for join + gameplay packets.
 */
public final class JavaDownstreamClient {

    static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    /** Paper pin / Link default JE protocol (26.2). */
    public static final int DEFAULT_PROTOCOL = 776;

    /**
     * View distance reported to Folia via Client Information when Bedrock has not yet
     * requested a radius. Must be high enough that Folia streams a full disk — the old
     * hardcoded {@code 8} produced the Bedrock gray fog wall after a few chunks.
     */
    public static final int DEFAULT_VIEW_DISTANCE = 32;

    /** Last view distance sent (or pending) on Client Information. */
    volatile int requestedViewDistance = DEFAULT_VIEW_DISTANCE;

    // Login clientbound (proto 776)
    static final int L_DISCONNECT = 0x00;
    static final int L_SUCCESS = 0x02;
    static final int L_COMPRESSION = 0x03;
    static final int L_CUSTOM_QUERY = 0x04;

    // Login serverbound
    static final int LS_START = 0x00;
    static final int LS_CUSTOM_QUERY_ANSWER = 0x02;
    static final int LS_ACKNOWLEDGED = 0x03;

    // Configuration clientbound (Paper / Folia 26.2 proto 776)
    static final int C_COOKIE_REQUEST = 0x00;
    static final int C_DISCONNECT = 0x02;
    static final int C_FINISH = 0x03;
    static final int C_KEEP_ALIVE = 0x04;
    static final int C_PING = 0x05;
    static final int C_RESOURCE_PACK_POP = 0x08;
    static final int C_RESOURCE_PACK_PUSH = 0x09;
    static final int C_REGISTRY_DATA = 0x07;
    static final int C_SELECT_KNOWN_PACKS = 0x0e;
    static final int C_CODE_OF_CONDUCT = 0x13;

    /**
     * Geyser {@code JavaSelectKnownPacksTranslator} — echo vanilla packs so Folia can omit
     * registry NBT. Empty response forces full NBT (dimension_type doubles, …) over the wire.
     */
    static final java.util.Set<String> KNOWN_PACK_IDS = java.util.Set.of(
            "core", "trade_rebalance", "redstone_experiments", "minecart_improvements");

    // Configuration serverbound
    static final int CS_CLIENT_INFORMATION = 0x00;
    static final int CS_COOKIE_RESPONSE = 0x01;
    static final int CS_FINISH = 0x03;
    static final int CS_KEEP_ALIVE = 0x04;
    static final int CS_PONG = 0x05;
    static final int CS_RESOURCE_PACK = 0x06;
    static final int CS_SELECT_KNOWN_PACKS = 0x07;
    static final int CS_ACCEPT_CODE_OF_CONDUCT = 0x09;

    /** Resource-pack status: SUCCESSFULLY_LOADED / ACCEPTED (Paper waits for both). */
    static final int RP_SUCCESSFULLY_LOADED = 0;
    static final int RP_ACCEPTED = 3;

    public enum Phase {
        CONNECTING, LOGIN, CONFIGURATION, PLAY, CLOSED
    }

    /** Parsed fields from clientbound Login (play) — proto 776 layout. */
    public record LoginPlayInfo(
            int entityId,
            int viewDistance,
            int dimensionType,
            String dimensionName) {
    }

    /** Parsed fields from clientbound Respawn — dimension change / portal. */
    public record RespawnInfo(int dimensionType, String dimensionName, byte dataKept) {
    }

    public interface Listener {
        void onLoginSuccess(UUID uuid, String username);

        void onConfigurationComplete();

        void onLoginPlay(LoginPlayInfo info);

        default void onRespawn(RespawnInfo info) {}

        void onLevelChunk(int chunkX, int chunkZ, ByteBuf payload);

        void onDisconnect(String reason);

        default void onBlockUpdate(int x, int y, int z, int blockState) {}

        default void onSectionBlocksUpdate(int sectionX, int sectionY, int sectionZ) {}

        /** Section multi-block change with packed cell list (x,y,z,jeState). */
        default void onSectionBlocksUpdate(int sectionX, int sectionY, int sectionZ,
                                           int[] cells) {
            onSectionBlocksUpdate(sectionX, sectionY, sectionZ);
        }

        default void onPlayerPosition(double x, double y, double z, float yaw, float pitch, int teleportId) {}

        default void onEntityMove(int entityId, double x, double y, double z,
                                  float yaw, float pitch, boolean teleport) {}

        /**
         * JE teleport_entity with relative flags (proto 26.2 PositionMoveRotation + Relative set).
         * {@code relatives} bitmask: X=1 Y=2 Z=4 Y_ROT=8 X_ROT=16.
         */
        default void onEntityTeleport(int entityId, double x, double y, double z,
                                      float yaw, float pitch, int relatives, boolean onGround) {
            onEntityMove(entityId, x, y, z, yaw, pitch, true);
        }

        /**
         * Relative JE move_entity_* deltas. yaw/pitch are NaN when unchanged;
         * {@code rotationOnly} when only look changed.
         */
        default void onEntityRelativeMove(int entityId, double dx, double dy, double dz,
                                          float yaw, float pitch, boolean rotationOnly,
                                          boolean onGround) {}

        default void onAddEntity(int entityId, UUID uuid, String typeKey,
                                 double x, double y, double z, float yaw, float pitch) {}

        default void onRemoveEntities(int[] entityIds) {}

        default void onContainerSetContent(int windowId, int slotCount) {}

        default void onContainerSetContent(int windowId, JeItemStackCodec.Stack[] stacks) {
            onContainerSetContent(windowId, stacks != null ? stacks.length : 0);
        }

        default void onContainerSetSlot(int windowId, int slot) {}

        default void onContainerSetSlot(int windowId, int slot, JeItemStackCodec.Stack stack) {
            onContainerSetSlot(windowId, slot);
        }

        default void onOpenScreen(int windowId, int menuTypeId) {}

        default void onCommands(java.util.List<String> literalNames) {}

        /** Full JE Brigadier {@code commands} tree (preferred over literal scrape). */
        default void onCommandsTree(JavaCommandsTree.Parsed tree) {
            if (tree != null) {
                onCommands(tree.rootLiteralNames());
            }
        }

        default void onHurtAnimation(int entityId, float yaw) {}

        default void onSetHealth(float health, int food, float saturation) {}

        default void onSystemChat(String plain) {}

        default void onActionBar(String plain) {}

        default void onTitle(String plain) {}

        default void onSubtitle(String plain) {}

        default void onTitleTimes(int fadeInTicks, int stayTicks, int fadeOutTicks) {}

        default void onClearTitles(boolean reset) {}

        default void onBossEvent(UUID bossId, int action, String title, float pct, int color) {}

        default void onSetObjective(String objectiveId, int mode, String displayName, String criteria) {}

        default void onSetDisplayObjective(int position, String objectiveId) {}

        default void onSetScore(String owner, String objective, int score) {}

        default void onResetScore(String owner, String objective) {}

        default void onPlayerChat(String source, String plain) {}

        default void onPlayerInfoAdd(UUID uuid, String name) {}

        default void onPlayerInfoRemove(UUID uuid) {}

        default void onSetEntityData(int entityId) {}

        /** Optional JE custom name from set_entity_data (index 2). */
        default void onEntityCustomName(int entityId, String plainName, boolean visible) {}

        /** Living health from set_entity_data / update_attributes when known. */
        default void onEntityHealth(int entityId, float health) {}

        /** JE entity_event — used for op permission level (status 24–28) and death (3). */
        default void onEntityEvent(int entityId, int status) {}

        default void onEntityMotion(int entityId, double mx, double my, double mz) {}

        default void onUpdateAttributes(int entityId, float health) {}

        /** JE set_equipment slot: 0 main, 1 off, 2–5 armor, 6 body. */
        default void onSetEquipment(int entityId, int slot, JeItemStackCodec.Stack stack) {}

        /** Floodgate / plugin channel payload (e.g. {@code floodgate:form}). */
        default void onCustomPayload(String channel, byte[] data) {}

        /** Backend asked Link to soft-transfer this Bedrock player (YaPPortals / BungeeCord Connect). */
        default void onBungeeConnect(String targetServer) {}

        default void onLevelEvent(int eventId, double x, double y, double z, int data) {}

        default void onSound(String soundId, double x, double y, double z) {}
    }

    final InetSocketAddress backend;
    final String username;
    final UUID playerId;
    final String clientAddress;
    final Path linkHome;
    final int protocolVersion;
    final Listener listener;
    final EventLoopGroup group;

    volatile Channel channel;
    volatile Phase phase = Phase.CONNECTING;
    final AtomicBoolean closed = new AtomicBoolean(false);
    byte[] forwardingSecret;
    McCompressionCodec.Decoder compDec;
    volatile int javaEntityId = -1;
    volatile int lastAcceptedTeleportId = Integer.MIN_VALUE;
    public volatile int lastContainerStateId;
    final JeBlockRegistry blockRegistry = new JeBlockRegistry();
    final JavaDownstreamLogin login = new JavaDownstreamLogin(this);
    final JavaDownstreamPlay play = new JavaDownstreamPlay(this);


    public JavaDownstreamClient(
            EventLoopGroup group,
            InetSocketAddress backend,
            String username,
            UUID playerId,
            String clientAddress,
            Path linkHome,
            Listener listener) {
        this(group, backend, username, playerId, clientAddress, linkHome, DEFAULT_PROTOCOL, listener);
    }

    public JavaDownstreamClient(
            EventLoopGroup group,
            InetSocketAddress backend,
            String username,
            UUID playerId,
            String clientAddress,
            Path linkHome,
            int protocolVersion,
            Listener listener) {
        this.group = group;
        this.backend = backend;
        this.username = username;
        this.playerId = playerId != null ? playerId : McCodec.offlineUuid(username);
        this.clientAddress = clientAddress != null ? clientAddress : "127.0.0.1";
        this.linkHome = linkHome;
        this.protocolVersion = protocolVersion > 0 ? protocolVersion : DEFAULT_PROTOCOL;
        this.listener = listener;
        this.forwardingSecret = JavaDownstreamNbt.loadForwardingSecret(linkHome);
    }

    public Phase phase() {
        return phase;
    }

    public Channel channel() {
        return channel;
    }

    public int javaEntityId() {
        return javaEntityId;
    }

    public JeBlockRegistry blockRegistry() {
        return blockRegistry;
    }

    /** Tell Folia the Bedrock client finished loading — unlocks chunk stream. */
    public void sendPlayerLoaded() {
        writePlay(JavaPlayWire.playerLoaded());
        LOG.info("JE ServerboundPlayerLoaded user=" + username);
    }

    public void connect() {
        if (closed.get()) {
            return;
        }
        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .option(io.netty.channel.ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast("frame-dec", new McFrameCodec.Decoder())
                                .addLast("frame-enc", new McOutboundPacketEncoder())
                                .addLast("handler", new Handler());
                    }
                });
        LOG.info("JE downstream connect → " + backend
                + " user=" + username + " uuid=" + playerId
                + " proto=" + protocolVersion
                + " velocity=" + (forwardingSecret != null && forwardingSecret.length > 0));
        b.connect(backend).addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                LOG.warning("JE downstream connect fail " + backend + ": " + f.cause());
                fail("Could not connect to Java backend " + backend);
                return;
            }
            channel = f.channel();
            phase = Phase.LOGIN;
            login.sendHandshake();
            login.sendLoginStart();
        });
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        phase = Phase.CLOSED;
        Channel ch = channel;
        if (ch != null) {
            ch.close();
        }
    }

    public void sendMovePosRot(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        sendMovePosRot(x, y, z, yaw, pitch, onGround, false);
    }

    public void sendMovePosRot(double x, double y, double z, float yaw, float pitch,
                               boolean onGround, boolean horizontalCollision) {
        writePlay(JavaPlayWire.movePosRot(x, y, z, yaw, pitch, onGround, horizontalCollision));
    }

    /** Proto 776+ — Geyser sends after every PlayerAuthInput once SPAWNED. */
    public void sendClientTickEnd() {
        writePlay(JavaPlayWire.clientTickEnd());
    }

    /** Proto 776+ player_input — send before move packets (Geyser InputCache order). */
    public void sendPlayerInput(boolean forward, boolean backward, boolean left, boolean right,
                                boolean jump, boolean shift, boolean sprint) {
        writePlay(JavaPlayWire.playerInput(forward, backward, left, right, jump, shift, sprint));
    }

    public void sendAcceptTeleport(int teleportId) {
        writePlay(JavaPlayWire.acceptTeleport(teleportId));
    }

    public void sendSetCarriedItem(int hotbarSlot) {
        writePlay(JavaPlayWire.setCarriedItem(hotbarSlot));
    }

    public void sendContainerClick(int windowId, int stateId, int slot, int button, int mode,
                                   Object ignoredCarried) {
        writePlay(JavaPlayInventoryWire.containerClick(windowId, stateId, slot, button, mode));
    }

    public void sendContainerClose(int windowId) {
        writePlay(JavaPlayInventoryWire.containerClose(windowId));
    }

    public void sendRenameItem(String name) {
        writePlay(JavaPlayInventoryWire.renameItem(name));
    }

    public void sendCustomPayload(String channel, byte[] data) {
        writePlay(JavaPlayInventoryWire.customPayload(channel, data));
    }

    public void sendPlayerAction(int status, int x, int y, int z, int face, int sequence) {
        writePlay(JavaPlayWire.playerAction(status, x, y, z, face, sequence));
    }

    public void sendUseItemOn(int x, int y, int z, int face,
                              float cx, float cy, float cz, boolean inside, int hand, int sequence) {
        writePlay(JavaPlayWire.useItemOn(x, y, z, face, cx, cy, cz, inside, hand, sequence));
    }

    public void sendInteractAttack(int entityId, boolean sneaking) {
        writePlay(JavaPlayWire.interactAttack(entityId, sneaking));
    }

    public void sendClientCommandRespawn() {
        writePlay(JavaPlayWire.clientCommandRespawn());
    }

    /**
     * Geyser {@code sendJavaClientSettings}: update Folia's per-player chunk send radius.
     * Call after Bedrock {@code RequestChunkRadius} or when server view is known.
     */
    public void sendClientInformationView(int viewDistance) {
        int view = Math.max(2, Math.min(32, viewDistance));
        this.requestedViewDistance = view;
        writePlay(JavaPlayWire.clientInformation(view));
        LOG.info("JE Client Information (play) view=" + view + " user=" + username);
    }

    public void sendSwingArm(int hand) {
        writePlay(JavaPlayWire.swingArm(hand));
    }

    public void sendChatCommand(String commandWithoutSlash) {
        writePlay(JavaPlayWire.chatCommand(commandWithoutSlash));
    }

    public void sendChatMessage(String message) {
        writePlay(JavaPlayWire.chatMessage(message));
    }

    void writePlay(ByteBuf packet) {
        Channel ch = channel;
        if (ch == null || !ch.isActive() || phase != Phase.PLAY || packet == null) {
            if (packet != null) {
                packet.release();
            }
            return;
        }
        ch.writeAndFlush(packet);
    }

    void fail(String reason) {
        close();
        if (listener != null) {
            listener.onDisconnect(reason);
        }
    }


    private final class Handler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof ByteBuf buf)) {
                return;
            }
            try {
                switch (phase) {
                    case LOGIN -> login.handleLogin(ctx, buf);
                    case CONFIGURATION -> login.handleConfiguration(ctx, buf);
                    case PLAY -> play.handlePlay(ctx, buf);
                    default -> buf.release();
                }
            } catch (Exception e) {
                // handlePlay already releases in its finally — never double-release
                // (that masked real parse errors as "refCnt: 0, decrement: 1" / IC-41).
                if (buf.refCnt() > 0) {
                    buf.release();
                }
                LOG.log(Level.WARNING, "JE downstream packet error phase=" + phase, e);
                fail("Java downstream error: " + e.getMessage());
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (!closed.get()) {
                fail("Java backend closed");
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOG.log(Level.FINE, "JE downstream exception", cause);
            fail(cause.getMessage() != null ? cause.getMessage() : "exception");
        }
    }




}
