package com.yapcore.crossplay.bedrock.bridge;

import com.yapcore.crossplay.bedrock.*;
import com.yapcore.crossplay.bedrock.cloudburst.CloudburstCodecIndex;
import com.yapcore.crossplay.bedrock.cloudburst.CloudburstPackets;
import com.yapcore.crossplay.bedrock.cloudburst.CloudburstSession;
import com.yapcore.crossplay.bedrock.geyserport.BedrockRequestChunkRadiusTranslator;
import com.yapcore.crossplay.bedrock.geyserport.BedrockSetLocalPlayerAsInitializedTranslator;
import com.yapcore.crossplay.bedrock.geyserport.LoginEncryptionUtils;
import com.yapcore.crossplay.bedrock.geyserport.YapGeyserSession;
import com.yapcore.crossplay.floodgate.FloodgateAuth;
import io.netty.buffer.ByteBuf;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thin Bedrock login/pack handshake wrapper.
 *
 * <p>Join state machine lives in {@link YapGeyserSession} (native Geyser join-path port).
 * This class: Floodgate auth → encrypt → empty pack handshake → {@link YapGeyserSession#join}.
 *
 * <p>See {@code docs/geyser-join-reference/NATIVE_PORT.md}.
 */
public final class BedrockLoginFlow {

    private static final int PACK_REFUSED = 1;
    private static final int PACK_SEND_PACKS = 2;
    private static final int PACK_HAVE_ALL = 3;
    private static final int PACK_COMPLETED = 4;

    private static final long PACK_HANDSHAKE_TIMEOUT_MS = 8_000L;

    private final BedrockBridgeContext ctx;
    private final ConcurrentHashMap<Long, YapGeyserSession> yapSessions = new ConcurrentHashMap<>();

    public BedrockLoginFlow(BedrockBridgeContext ctx, BedrockWorldPush world, BedrockInventoryPush inventory) {
        this.ctx = ctx;
        // world/inventory retained via ctx for post-join push; join path is YapGeyserSession.
    }

    YapGeyserSession session(long guid) {
        return yapSessions.get(guid);
    }

    public void removeSession(long guid) {
        yapSessions.remove(guid);
    }

    void sendNetworkSettings(long guid) {
        BedrockBridgeContext.LOG.info("BE network settings → guid=" + Long.toHexString(guid)
                + " (Geyser zlib threshold=512)");
        CloudburstSession cb = ctx.getCloudburst(guid);
        if (cb == null) {
            Integer pending = ctx.pendingProtocol.get(guid);
            if (pending != null && pending >= CloudburstCodecIndex.MIN_MODERN) {
                cb = ctx.openCloudburst(guid, pending);
            }
        }
        if (cb != null) {
            ctx.sendPacket(guid, CloudburstPackets.networkSettingsGeyser());
        } else {
            ctx.send(guid, BedrockPacketCodec.networkSettingsGeyser());
        }
        markCompressionHeader(guid);
    }

    void markCompressionHeader(long guid) {
        if (ctx.compressionArmed != null) {
            ctx.compressionArmed.accept(guid);
        }
    }

    void handleChunkRadius(long guid, ByteBuf body, List<BedrockGameplayBridge.GameAction> actions, String user) {
        int requested = 8;
        try {
            requested = Math.max(2, Math.min(16, BedrockPacketCodec.readUnsignedVarInt(body.duplicate())));
        } catch (Exception ignored) {
            // default
        }
        handleChunkRadius(guid, requested, actions, user);
    }

    void handleChunkRadius(long guid, int requestedRadius, List<BedrockGameplayBridge.GameAction> actions, String user) {
        YapGeyserSession session = yapSessions.get(guid);
        if (session != null) {
            BedrockRequestChunkRadiusTranslator.translate(session, requestedRadius);
            return;
        }
        // Pre-session race: store only.
        ctx.chunkRadius.put(guid, Math.max(2, Math.min(32, requestedRadius)));
        BedrockBridgeContext.LOG.info(
                "BE REQUEST_CHUNK_RADIUS pre-session store " + user + " requested=" + requestedRadius);
    }

    void beginLogin(long guid, String address, ByteBuf body, List<BedrockGameplayBridge.GameAction> actions) {
        BedrockBridgeContext.LoginPhase existingPhase = ctx.loginPhase.get(guid);
        if (existingPhase != null || ctx.sessions.get(guid) != null) {
            BedrockBridgeContext.LOG.info(
                    "BE ignore duplicate Login guid=" + Long.toHexString(guid)
                            + " phase=" + existingPhase
                            + " (already in join — RakNet retransmit / race)");
            return;
        }
        FloodgateAuth.Identity identity = ctx.floodgate.authenticate(body, address);
        int proto = ctx.pendingProtocol.getOrDefault(guid, identity.protocol());
        if (proto <= 0) {
            proto = identity.protocol() > 0 ? identity.protocol() : 712;
        }
        CloudburstSession cb = null;
        if (proto >= CloudburstCodecIndex.MIN_MODERN) {
            cb = ctx.openCloudburst(guid, proto);
        }
        long runtime = ctx.runtimeIds.getAndIncrement();
        ctx.sessions.open(guid, identity.username(), proto, address);
        BedrockJoinProbe.start(guid, identity.username(), proto, address);
        BedrockJoinProbe.noteEvent(guid, "login_begin floodgate_ok");
        ctx.runtimeByGuid.put(guid, runtime);
        ctx.skins.registerDefault(identity.username(), identity.javaUuid());
        ctx.entities.addPlayer(runtime, runtime, identity.javaUuid(), identity.username(),
                8.5f, 65.62f, -7.5f, false);
        ByteBuf announce = BedrockPacketCodec.addPlayer(
                identity.javaUuid(), identity.username(), runtime, 8.5f, 65.62f, -7.5f, 0f, 0f);
        for (Long other : ctx.sessions.allGuids()) {
            if (!other.equals(guid)) {
                if (ctx.getCloudburst(other) != null) {
                    ctx.sendPacket(other, CloudburstPackets.addPlayer(
                            identity.javaUuid(), identity.username(), runtime, 8.5f, 65.62f, -7.5f, 0f, 0f));
                } else {
                    ctx.send(other, announce.retainedDuplicate());
                }
            }
        }
        announce.release();

        YapGeyserSession yap = YapGeyserSession.open(
                ctx, guid, runtime, identity.username(), identity.javaUuid(), proto, cb);
        yapSessions.put(guid, yap);

        // Defer JOIN (crossplay + welcome form) until after StartGame.
        ctx.pendingJoin.put(guid, Map.of(
                "protocol", Integer.toString(proto),
                "xuid", identity.xuid(),
                "uuid", identity.javaUuid().toString(),
                "floodgate", "true",
                "runtimeId", Long.toString(runtime)
        ));

        if (!LoginEncryptionUtils.encryptPlayerConnection(ctx, guid, identity, proto)) {
            BedrockBridgeContext.LOG.warning(
                    "BE encryption handshake failed for " + identity.username()
                            + " — aborting login (Geyser always encrypts, incl. offline Floodgate)");
            yapSessions.remove(guid);
            return;
        }
        BedrockBridgeContext.LOG.info("BE login " + identity.username() + " xuid=" + identity.xuid()
                + " uuid=" + identity.javaUuid()
                + " pack=empty-handshake (no CDN) proto=" + proto + " enc=on path=yap-geyser-session");
        schedulePackHandshakeTimeout(guid);
    }

    void onEncryptionAcknowledged(long guid) {
        LoginEncryptionUtils.sendLoginSuccessAndEmptyPacks(ctx, guid, protocolOf(guid));
    }

    void handlePackClientResponse(long guid, ByteBuf body, List<BedrockGameplayBridge.GameAction> actions) {
        int proto = protocolOf(guid);
        int status = decodePackResponseStatus(body, proto);
        handlePackClientResponseStatus(guid, status, actions);
    }

    void handlePackClientResponseStatus(long guid, int status, List<BedrockGameplayBridge.GameAction> actions) {
        BedrockBridgeContext.LoginPhase phase = ctx.loginPhase.get(guid);
        if (phase == BedrockBridgeContext.LoginPhase.SPAWNED
                || phase == BedrockBridgeContext.LoginPhase.AWAITING_CLIENT_INIT
                || phase == BedrockBridgeContext.LoginPhase.BOOTSTRAPPING) {
            return;
        }
        int proto = protocolOf(guid);
        BedrockBridgeContext.LOG.info("BE pack response guid=" + Long.toHexString(guid)
                + " status=" + status + " phase=" + phase + " proto=" + proto);

        if (phase == BedrockBridgeContext.LoginPhase.AWAITING_STACK_COMPLETE) {
            if (status == PACK_COMPLETED || status < 0) {
                join(guid, actions);
            }
            return;
        }

        if (phase != BedrockBridgeContext.LoginPhase.AWAITING_PACKS) {
            BedrockBridgeContext.LOG.info("BE pack response ignored (phase=" + phase + ") guid="
                    + Long.toHexString(guid) + " status=" + status);
            if (status == PACK_COMPLETED) {
                join(guid, actions);
            }
            return;
        }

        if (status == PACK_REFUSED || status == PACK_SEND_PACKS || status == PACK_HAVE_ALL || status < 0) {
            if (status == PACK_SEND_PACKS) {
                BedrockBridgeContext.LOG.info(
                        "BE pack SEND_PACKS — Phase-1 empty stack (no CDN chunk delivery)");
            }
            ctx.pendingPack.remove(guid);
            sendStackAndAwait(guid);
            return;
        }

        if (status == PACK_COMPLETED) {
            // Geyser UpstreamPacketHandler COMPLETED → connect/join directly.
            join(guid, actions);
            return;
        }

        sendStackAndAwait(guid);
    }

    /** Floodgate auth already done — pack COMPLETED → {@link YapGeyserSession#join}. */
    void join(long guid, List<BedrockGameplayBridge.GameAction> actions) {
        YapGeyserSession session = yapSessions.get(guid);
        if (session == null) {
            BedrockBridgeContext.LOG.warning(
                    "BE join without YapGeyserSession guid=" + Long.toHexString(guid));
            return;
        }
        session.join(actions);
    }

    private static int decodePackResponseStatus(ByteBuf body, int protocol) {
        if (body == null || !body.isReadable()) {
            return -1;
        }
        ByteBuf b = body.duplicate();
        try {
            if (protocol >= 2168) {
                int wire = BedrockPacketCodec.readUnsignedVarInt(b);
                if (b.isReadable()) {
                    BedrockPacketCodec.readString(b);
                }
                return wire + 1;
            }
            return b.readUnsignedByte();
        } catch (Exception e) {
            return -1;
        }
    }

    private void sendStackAndAwait(long guid) {
        int proto = protocolOf(guid);
        CloudburstSession cb = ctx.getCloudburst(guid);
        if (cb != null) {
            ctx.sendPacket(guid, CloudburstPackets.resourcePackStackEmpty());
        } else {
            ctx.send(guid, BedrockPacketCodec.resourcePackStackEmpty(proto));
        }
        ctx.loginPhase.put(guid, BedrockBridgeContext.LoginPhase.AWAITING_STACK_COMPLETE);
        BedrockBridgeContext.LOG.info("BE ResourcePackStack empty → await COMPLETED guid="
                + Long.toHexString(guid));
    }

    private void schedulePackHandshakeTimeout(long guid) {
        final long g = guid;
        Thread.ofVirtual().name("yap-be-pack-timeout-" + Long.toHexString(guid)).start(() -> {
            try {
                Thread.sleep(PACK_HANDSHAKE_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            BedrockBridgeContext.LoginPhase phase = ctx.loginPhase.get(g);
            if (phase == null
                    || phase == BedrockBridgeContext.LoginPhase.SPAWNED
                    || phase == BedrockBridgeContext.LoginPhase.AWAITING_CLIENT_INIT
                    || phase == BedrockBridgeContext.LoginPhase.BOOTSTRAPPING) {
                return;
            }
            if (ctx.sessions.get(g) == null) {
                return;
            }
            BedrockBridgeContext.LOG.warning("BE pack handshake timeout guid=" + Long.toHexString(g)
                    + " phase=" + phase + " — empty stack + join()");
            ctx.pendingPack.remove(g);
            if (phase == BedrockBridgeContext.LoginPhase.AWAITING_ENCRYPTION) {
                LoginEncryptionUtils.sendLoginSuccessAndEmptyPacks(ctx, g, protocolOf(g));
                try {
                    Thread.sleep(1_500L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                phase = ctx.loginPhase.get(g);
            }
            if (phase == BedrockBridgeContext.LoginPhase.AWAITING_PACKS) {
                sendStackAndAwait(g);
                try {
                    Thread.sleep(1_500L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            BedrockBridgeContext.LoginPhase after = ctx.loginPhase.get(g);
            if (after != BedrockBridgeContext.LoginPhase.SPAWNED
                    && after != BedrockBridgeContext.LoginPhase.AWAITING_CLIENT_INIT
                    && after != BedrockBridgeContext.LoginPhase.BOOTSTRAPPING
                    && ctx.sessions.get(g) != null) {
                join(g, null);
            }
        });
    }

    void finishLogin(long guid, List<BedrockGameplayBridge.GameAction> actions) {
        join(guid, actions);
    }

    void onClientInitialized(long guid, List<BedrockGameplayBridge.GameAction> actions) {
        YapGeyserSession session = yapSessions.get(guid);
        if (session != null) {
            BedrockSetLocalPlayerAsInitializedTranslator.translate(session, actions);
            return;
        }
        BedrockBridgeContext.LOG.warning(
                "BE SetLocalPlayerAsInitialized without YapGeyserSession guid="
                        + Long.toHexString(guid));
    }

    void sendSpawnSequence(long guid, BedrockSessionManager.BedrockSession session) {
        if (session == null) {
            return;
        }
        BedrockBridgeContext.LoginPhase phase = ctx.loginPhase.get(guid);
        if (phase != BedrockBridgeContext.LoginPhase.SPAWNED
                && phase != BedrockBridgeContext.LoginPhase.BOOTSTRAPPING
                && phase != BedrockBridgeContext.LoginPhase.AWAITING_CLIENT_INIT) {
            join(guid, null);
        }
    }

    private int protocolOf(long guid) {
        BedrockSessionManager.BedrockSession sess = ctx.sessions.get(guid);
        return sess != null ? sess.protocol() : 0;
    }
}
