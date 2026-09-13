package com.yapcore.crossplay.bedrock.bridge;

import com.yapcore.crossplay.bedrock.*;
import com.yapcore.crossplay.bedrock.cloudburst.CloudburstCodecIndex;
import com.yapcore.crossplay.bedrock.cloudburst.CloudburstSession;
import com.yapcore.crossplay.floodgate.FloodgateAuth;
import com.yapcore.crossplay.form.FormService;
import com.yapcore.crossplay.skin.SkinService;
import io.netty.buffer.ByteBuf;

import com.yapcore.resourcepack.ResourcePackOffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.logging.Logger;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

/** Shared mutable state for Bedrock gameplay bridge helpers. */
public final class BedrockBridgeContext {

    public static final Logger LOG = Logger.getLogger("YaPcore.BedrockBridge");

    public final BedrockSessionManager sessions;
    public final FloodgateAuth floodgate;
    public final SkinService skins;
    public final FormService forms;
    public final BedrockEntityTracker entities = new BedrockEntityTracker();
    public final BedrockInventoryAuthority inventory = new BedrockInventoryAuthority();
    public final BedrockContainerBridge containers = new BedrockContainerBridge();
    public final AtomicLong runtimeIds = new AtomicLong(1);
    public final Map<Long, Integer> chunkRadius = new ConcurrentHashMap<>();
    public final Map<Long, Long> runtimeByGuid = new ConcurrentHashMap<>();
    public final Map<Long, Integer> pendingProtocol = new ConcurrentHashMap<>();
    public final Map<Long, CloudburstSession> cloudburstSessions = new ConcurrentHashMap<>();
    public final BedrockColumnStreamer columns = new BedrockColumnStreamer();
    public final ConcurrentHashMap<String, Long> inventoryFingerprint = new ConcurrentHashMap<>();
    /** Per-guid Bedrock login/pack handshake phase. */
    public final ConcurrentHashMap<Long, LoginPhase> loginPhase = new ConcurrentHashMap<>();
    /** Pack pending for ResourcePackStack after HAVE_ALL_PACKS (null = empty stack). */
    public final ConcurrentHashMap<Long, PendingPack> pendingPack = new ConcurrentHashMap<>();
    /** JOIN payload deferred until StartGame (forms/crossplay must not run mid-pack handshake). */
    public final ConcurrentHashMap<Long, Map<String, String>> pendingJoin = new ConcurrentHashMap<>();

    /** Phase 2 outbound emote relay (set by BedrockGameplayBridge). */
    public volatile BedrockEmotePush emotePush;
    /** Parent facade for post-init catalog pushes (movement + emote list). */
    public volatile com.yapcore.crossplay.bedrock.BedrockGameplayBridge gameplayBridge;

    public BiConsumer<Long, List<ByteBuf>> outbound = (guid, packets) -> {
    };
    public volatile BedrockPaperWorldSync paperWorld;
    public LongConsumer compressionArmed;
    /**
     * Geyser {@code enableEncryption} after ServerToClientHandshake is flushed.
     * Args: (guid, AES SecretKey).
     */
    public volatile java.util.function.BiConsumer<Long, javax.crypto.SecretKey> encryptionEnabled;
    /**
     * Bedrock CDN offer; arg is client address string (e.g. {@code /127.0.0.1:12345}).
     * Empty → no pack / empty ResourcePacksInfo.
     */
    public volatile Function<String, Optional<ResourcePackOffer>> resourcePackOffer = a -> Optional.empty();
    /** Async JOIN emit when finishLogin runs outside a game-batch actions list (timeout path). */
    public volatile Consumer<com.yapcore.crossplay.bedrock.BedrockGameplayBridge.GameAction> emitJoin =
            a -> {
            };

    public enum LoginPhase {
        /** ServerToClientHandshake + enableEncryption done; LOGIN_SUCCESS about to / just sent. */
        AWAITING_ENCRYPTION,
        AWAITING_PACKS,
        AWAITING_STACK_COMPLETE,
        /** connect() packets sent; waiting SetLocalPlayerAsInitialized (Geyser match). */
        AWAITING_CLIENT_INIT,
        /** Unused on modern connect()-only path; kept for enum stability. */
        BOOTSTRAPPING,
        /** Marked ready after SetLocalPlayerAsInitialized. */
        SPAWNED
    }

    /**
     * After C2S SetLocalPlayerAsInitialized. Publisher (updateChunkPosition) only then.
     */
    public final ConcurrentHashMap<Long, Boolean> clientInitialized = new ConcurrentHashMap<>();

    /**
     * One-shot post-{@code ChunkRadiusUpdated} chunk fill (Geyser {@code ChunkUtils.sendEmptyChunks}
     * or Paper hashed columns via {@code JavaLoginTranslator}).
     */
    public final ConcurrentHashMap<Long, Boolean> startConfigEmptyFillSent = new ConcurrentHashMap<>();

    /** Last chunk column key for publisher-only updates ({@code (cx<<32)^cz}). */
    public final ConcurrentHashMap<Long, Long> lastPublisherChunk = new ConcurrentHashMap<>();

    /** True only after {@link LoginPhase#SPAWNED} — base gate for post-join world push. */
    public boolean isSpawnFullyReady(long guid) {
        return loginPhase.get(guid) == LoginPhase.SPAWNED;
    }

    public boolean isClientInitialized(long guid) {
        return Boolean.TRUE.equals(clientInitialized.get(guid));
    }

    /** Spawn coords for deferred chunk/inventory push after client init. */
    public record PendingSpawn(long runtime, String user, UUID uuid, int sx, int sy, int sz, double[] spawn) {
    }

    public final ConcurrentHashMap<Long, PendingSpawn> pendingSpawn = new ConcurrentHashMap<>();

    public record PendingPack(UUID packId, String version, boolean forced, String cdnUrl, long sizeBytes) {
    }

    public BedrockBridgeContext(BedrockSessionManager sessions,
                                FloodgateAuth floodgate,
                                SkinService skins,
                                FormService forms) {
        this.sessions = sessions;
        this.floodgate = floodgate;
        this.skins = skins;
        this.forms = forms;
        BedrockJoinProbe.bindContext(this);
    }

    public void send(long guid, List<ByteBuf> packets) {
        // S2C game packets are recorded in sendPacket (Cloudburst). Raw ByteBuf-only
        // sends (legacy hand-roll) are recorded here when no Cloudburst encode ran.
        if (BedrockJoinProbe.isActive(guid) && packets != null && !RAW_S2C_SKIP.get()) {
            for (ByteBuf p : packets) {
                if (p != null) {
                    noteRawS2C(guid, p);
                }
            }
        }
        outbound.accept(guid, packets);
    }

    public void send(long guid, ByteBuf packet) {
        if (BedrockJoinProbe.isActive(guid) && packet != null && !RAW_S2C_SKIP.get()) {
            noteRawS2C(guid, packet);
        }
        outbound.accept(guid, List.of(packet));
    }

    private static final ThreadLocal<Boolean> RAW_S2C_SKIP = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static void noteRawS2C(long guid, ByteBuf packet) {
        int id = -1;
        try {
            ByteBuf d = packet.duplicate();
            id = com.yapcore.crossplay.bedrock.BedrockPacketCodec.readUnsignedVarInt(d);
        } catch (Exception ignored) {
            // not a readable game packet
        }
        BedrockJoinProbe.noteS2C(guid, id, "rawByteBuf", packet.readableBytes());
    }

    public CloudburstSession openCloudburst(long guid, int proto) {
        if (proto < CloudburstCodecIndex.MIN_MODERN) {
            return null;
        }
        return cloudburstSessions.compute(guid, (g, existing) -> {
            if (existing != null && existing.protocol() == proto) {
                return existing;
            }
            return CloudburstSession.create(proto);
        });
    }

    public void closeCloudburst(long guid) {
        cloudburstSessions.remove(guid);
    }

    public CloudburstSession getCloudburst(long guid) {
        return cloudburstSessions.get(guid);
    }

    /**
     * Encode and send a Cloudburst packet when a modern session exists.
     * Hand-roll fallback is the caller's responsibility for proto &lt; 2168.
     */
    public void sendPacket(long guid, BedrockPacket packet) {
        if (packet == null) {
            return;
        }
        CloudburstSession session = cloudburstSessions.get(guid);
        if (session == null) {
            LOG.fine("BE sendPacket without CloudburstSession guid=" + Long.toHexString(guid)
                    + " type=" + packet.getPacketType());
            return;
        }
        int packetId = -1;
        try {
            packetId = session.codec().getPacketDefinition(packet.getClass()).getId();
        } catch (Exception ignored) {
            // leave -1
        }
        ByteBuf buf = session.encode(packet);
        if (BedrockJoinProbe.isActive(guid)) {
            BedrockJoinProbe.noteS2CPacket(guid, packet, packetId, buf.readableBytes());
        }
        try {
            // LevelChunk (and similar) hold retained payloads — release after encode.
            if (packet instanceof io.netty.util.ReferenceCounted rc) {
                rc.release();
            }
        } catch (Exception ignored) {
            // not reference-counted
        }
        RAW_S2C_SKIP.set(Boolean.TRUE);
        try {
            send(guid, buf);
        } finally {
            RAW_S2C_SKIP.set(Boolean.FALSE);
        }
    }

    public void sendPackets(long guid, List<? extends BedrockPacket> packets) {
        if (packets == null || packets.isEmpty()) {
            return;
        }
        CloudburstSession session = cloudburstSessions.get(guid);
        if (session == null) {
            return;
        }
        List<ByteBuf> out = new ArrayList<>(packets.size());
        for (BedrockPacket packet : packets) {
            if (packet == null) {
                continue;
            }
            int packetId = -1;
            try {
                packetId = session.codec().getPacketDefinition(packet.getClass()).getId();
            } catch (Exception ignored) {
                // leave -1
            }
            ByteBuf buf = session.encode(packet);
            if (BedrockJoinProbe.isActive(guid)) {
                BedrockJoinProbe.noteS2CPacket(guid, packet, packetId, buf.readableBytes());
            }
            out.add(buf);
            try {
                if (packet instanceof io.netty.util.ReferenceCounted rc) {
                    rc.release();
                }
            } catch (Exception ignored) {
                // not reference-counted
            }
        }
        if (!out.isEmpty()) {
            RAW_S2C_SKIP.set(Boolean.TRUE);
            try {
                send(guid, out);
            } finally {
                RAW_S2C_SKIP.set(Boolean.FALSE);
            }
        }
    }
}
