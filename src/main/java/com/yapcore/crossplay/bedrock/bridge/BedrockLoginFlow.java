package com.yapcore.crossplay.bedrock.bridge;

import com.yapcore.crossplay.bedrock.*;
import com.yapcore.crossplay.bedrock.codec.BedrockUiCodec;
import com.yapcore.crossplay.floodgate.FloodgateAuth;
import com.yapcore.resourcepack.ResourcePackOffer;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Login, spawn sequence, network settings, and chunk radius handling. */
public final class BedrockLoginFlow {

    /** ResourcePackClientResponse status (wiki.vg / Cloudburst). */
    private static final int PACK_REFUSED = 1;
    private static final int PACK_SEND_PACKS = 2;
    private static final int PACK_HAVE_ALL = 3;
    private static final int PACK_COMPLETED = 4;

    /** If client never answers ResourcePackClientResponse (common on bad CDN / mid-login forms). */
    private static final long PACK_HANDSHAKE_TIMEOUT_MS = 8_000L;

    private final BedrockBridgeContext ctx;
    private final BedrockWorldPush world;
    private final BedrockInventoryPush inventory;

    public BedrockLoginFlow(BedrockBridgeContext ctx, BedrockWorldPush world, BedrockInventoryPush inventory) {
        this.ctx = ctx;
        this.world = world;
        this.inventory = inventory;
    }

    void sendNetworkSettings(long guid) {
        BedrockBridgeContext.LOG.info("BE network settings → guid=" + Long.toHexString(guid));
        ctx.send(guid, BedrockPacketCodec.networkSettingsUncompressed());
        markCompressionHeader(guid);
    }

    void markCompressionHeader(long guid) {
        if (ctx.compressionArmed != null) {
            ctx.compressionArmed.accept(guid);
        }
    }

    void handleChunkRadius(long guid, ByteBuf body, List<BedrockGameplayBridge.GameAction> actions, String user) {
        int radius = 8;
        try {
            radius = Math.max(2, Math.min(16, BedrockPacketCodec.readUnsignedVarInt(body.duplicate())));
        } catch (Exception ignored) {
            // default
        }
        ctx.chunkRadius.put(guid, radius);
        ctx.columns.setRadius(guid, radius);
        List<ByteBuf> out = new ArrayList<>();
        out.add(BedrockPacketCodec.chunkRadiusUpdated(radius));
        double[] spawn = world.paperSpawnOrDefault();
        int sx = (int) Math.floor(spawn[0]);
        int sy = (int) Math.floor(spawn[1]);
        int sz = (int) Math.floor(spawn[2]);
        out.add(BedrockPacketCodec.networkChunkPublisherUpdate(sx, sy, sz, radius * 16));
        for (BedrockColumnStreamer.Column c : ctx.columns.initialRing(guid, sx, sz, 2)) {
            out.add(world.chunkFor(c.cx(), c.cz()));
        }
        ctx.send(guid, out);
        world.mirrorPaperPlayers();
        actions.add(new BedrockGameplayBridge.GameAction("MOVE", user, Map.of("chunkRadius", Integer.toString(radius))));
    }

    void beginLogin(long guid, String address, ByteBuf body, List<BedrockGameplayBridge.GameAction> actions) {
        FloodgateAuth.Identity identity = ctx.floodgate.authenticate(body, address);
        int proto = ctx.pendingProtocol.getOrDefault(guid, identity.protocol());
        if (proto <= 0) {
            proto = identity.protocol() > 0 ? identity.protocol() : 712;
        }
        long runtime = ctx.runtimeIds.getAndIncrement();
        ctx.sessions.open(guid, identity.username(), proto, address);
        ctx.runtimeByGuid.put(guid, runtime);
        ctx.skins.registerDefault(identity.username(), identity.javaUuid());
        ctx.entities.addPlayer(runtime, runtime, identity.javaUuid(), identity.username(),
                8.5f, 65.62f, -7.5f, false);
        ByteBuf announce = BedrockPacketCodec.addPlayer(
                identity.javaUuid(), identity.username(), runtime, 8.5f, 65.62f, -7.5f, 0f, 0f);
        for (Long other : ctx.sessions.allGuids()) {
            if (!other.equals(guid)) {
                ctx.send(other, announce.retainedDuplicate());
            }
        }
        announce.release();

        // Defer JOIN (crossplay + welcome form) until after StartGame — ModalFormRequest
        // mid-pack handshake disconnects cracked / modern Bedrock clients.
        ctx.pendingJoin.put(guid, Map.of(
                "protocol", Integer.toString(proto),
                "xuid", identity.xuid(),
                "uuid", identity.javaUuid().toString(),
                "floodgate", "true",
                "runtimeId", Long.toString(runtime)
        ));

        // Strict Bedrock handshake: LOGIN_SUCCESS + ResourcePacksInfo only.
        // StartGame waits for ResourcePackClientResponse (see handlePackClientResponse).
        List<ByteBuf> out = new ArrayList<>();
        out.add(BedrockPacketCodec.playStatus(BedrockPacketCodec.PlayStatus.LOGIN_SUCCESS));
        BedrockBridgeContext.PendingPack pending = resolvePendingPack(address);
        if (pending != null) {
            ctx.pendingPack.put(guid, pending);
            out.add(BedrockPacketCodec.resourcePacksInfoOffer(
                    pending.packId(), pending.version(), pending.sizeBytes(), pending.cdnUrl(), pending.forced()));
        } else {
            ctx.pendingPack.remove(guid);
            out.add(BedrockPacketCodec.resourcePacksInfoEmpty());
        }
        ctx.loginPhase.put(guid, BedrockBridgeContext.LoginPhase.AWAITING_PACKS);
        ctx.send(guid, out);
        BedrockBridgeContext.LOG.info("BE login " + identity.username() + " xuid=" + identity.xuid()
                + " uuid=" + identity.javaUuid()
                + (pending != null ? " pack=" + pending.cdnUrl() : " pack=none"));
        schedulePackHandshakeTimeout(guid);
    }

    /**
     * ResourcePackClientResponse → ResourcePackStack → StartGame (once).
     * Never re-sends StartGame after {@link BedrockBridgeContext.LoginPhase#SPAWNED}.
     */
    void handlePackClientResponse(long guid, ByteBuf body, List<BedrockGameplayBridge.GameAction> actions) {
        BedrockBridgeContext.LoginPhase phase = ctx.loginPhase.get(guid);
        if (phase == BedrockBridgeContext.LoginPhase.SPAWNED) {
            return;
        }
        int status = -1;
        if (body != null && body.isReadable()) {
            status = body.readUnsignedByte();
        }
        BedrockBridgeContext.LOG.info("BE pack response guid=" + Long.toHexString(guid)
                + " status=" + status + " phase=" + phase);

        if (status == PACK_REFUSED) {
            BedrockBridgeContext.PendingPack pending = ctx.pendingPack.get(guid);
            if (pending != null && pending.forced()) {
                BedrockBridgeContext.LOG.warning("BE pack refused (forced) — closing " + Long.toHexString(guid));
                ctx.send(guid, BedrockPacketCodec.playStatus(BedrockPacketCodec.PlayStatus.LOGIN_FAILED_SERVER));
                ctx.sessions.close(guid);
                ctx.loginPhase.remove(guid);
                ctx.pendingPack.remove(guid);
                ctx.pendingJoin.remove(guid);
                return;
            }
            // Declined optional pack — continue with empty stack.
            ctx.pendingPack.remove(guid);
            sendStackAndAwait(guid, null);
            return;
        }

        if (status == PACK_SEND_PACKS) {
            // Client wants chunked delivery (CDN failed / no CDN). We only support CDN
            // .mcpack URLs — skip the pack and continue so join still works.
            BedrockBridgeContext.LOG.warning("BE pack SEND_PACKS (CDN miss or no chunk support) — continuing without pack");
            ctx.pendingPack.remove(guid);
            sendStackAndAwait(guid, null);
            return;
        }

        if (status == PACK_HAVE_ALL) {
            sendStackAndAwait(guid, ctx.pendingPack.get(guid));
            return;
        }

        if (status == PACK_COMPLETED) {
            finishLogin(guid, actions);
            return;
        }

        // Unknown / empty status — still advance so clients are not stuck.
        if (phase == BedrockBridgeContext.LoginPhase.AWAITING_PACKS) {
            sendStackAndAwait(guid, ctx.pendingPack.get(guid));
        } else {
            finishLogin(guid, actions);
        }
    }

    private void sendStackAndAwait(long guid, BedrockBridgeContext.PendingPack pending) {
        List<ByteBuf> out = new ArrayList<>();
        if (pending != null) {
            out.add(BedrockPacketCodec.resourcePackStackOffer(
                    pending.packId(), pending.version(), pending.forced()));
        } else {
            out.add(BedrockPacketCodec.resourcePackStackEmpty());
        }
        ctx.loginPhase.put(guid, BedrockBridgeContext.LoginPhase.AWAITING_STACK_COMPLETE);
        ctx.send(guid, out);
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
            if (phase == null || phase == BedrockBridgeContext.LoginPhase.SPAWNED) {
                return;
            }
            if (ctx.sessions.get(g) == null) {
                return;
            }
            BedrockBridgeContext.LOG.warning("BE pack handshake timeout guid=" + Long.toHexString(g)
                    + " phase=" + phase + " — continuing without pack");
            ctx.pendingPack.remove(g);
            if (phase == BedrockBridgeContext.LoginPhase.AWAITING_PACKS) {
                sendStackAndAwait(g, null);
                try {
                    Thread.sleep(1_500L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (ctx.loginPhase.get(g) != BedrockBridgeContext.LoginPhase.SPAWNED
                    && ctx.sessions.get(g) != null) {
                finishLogin(g, null);
            }
        });
    }

    /** One-shot StartGame + world bootstrap. Safe to call multiple times. */
    void finishLogin(long guid, List<BedrockGameplayBridge.GameAction> actions) {
        BedrockBridgeContext.LoginPhase prev = ctx.loginPhase.put(guid, BedrockBridgeContext.LoginPhase.SPAWNED);
        if (prev == BedrockBridgeContext.LoginPhase.SPAWNED) {
            return;
        }
        BedrockSessionManager.BedrockSession session = ctx.sessions.get(guid);
        if (session == null) {
            return;
        }
        Long runtimeObj = ctx.runtimeByGuid.get(guid);
        if (runtimeObj == null) {
            return;
        }
        long runtime = runtimeObj;
        UUID uuid = ctx.floodgate.uuidFor(session.username());
        String user = session.username();
        double[] spawn = world.paperSpawnOrDefault();
        int sx = (int) Math.floor(spawn[0]);
        int sy = (int) Math.floor(spawn[1]);
        int sz = (int) Math.floor(spawn[2]);

        List<ByteBuf> out = new ArrayList<>();
        out.add(BedrockPacketCodec.startGame(runtime, runtime, "YaPcore", sx, sy, sz, uuid));
        out.add(BedrockPacketCodec.updateAttributesDefault(runtime));
        out.add(BedrockPacketCodec.setTime(1000));
        out.add(BedrockPacketCodec.setDifficulty(1));
        out.add(BedrockPacketCodec.setCommandsEnabled(true));
        out.add(BedrockPacketCodec.availableCommandsRich());
        out.add(BedrockPacketCodec.creativeContentFull());
        out.add(BedrockNbtDumps.availableEntityIdentifiers());
        out.add(BedrockNbtDumps.biomeDefinitionList());
        out.add(BedrockPacketCodec.playerListAddSelf(uuid, runtime, user));
        out.add(BedrockPacketCodec.playStatus(BedrockPacketCodec.PlayStatus.PLAYER_SPAWN));
        out.add(BedrockPacketCodec.chunkRadiusUpdated(8));
        out.add(BedrockPacketCodec.networkChunkPublisherUpdate(sx, sy, sz, 128));
        ctx.columns.setRadius(guid, 8);
        List<BedrockColumnStreamer.Column> ring = ctx.columns.initialRing(guid, sx, sz, 2);
        for (BedrockColumnStreamer.Column c : ring) {
            out.add(BedrockPacketCodec.levelChunkFlat(c.cx(), c.cz()));
        }
        ctx.inventory.ensure(user);
        out.add(BedrockPacketCodec.inventoryContent(0, ctx.inventory.storageNetworkIds(user)));
        out.addAll(ctx.entities.snapshotPackets(runtime));
        ctx.send(guid, out);

        Map<String, String> joinPayload = ctx.pendingJoin.remove(guid);
        if (joinPayload != null) {
            BedrockGameplayBridge.GameAction join =
                    new BedrockGameplayBridge.GameAction("JOIN", user, joinPayload);
            if (actions != null) {
                actions.add(join);
            } else {
                ctx.emitJoin.accept(join);
            }
        }

        final long g = guid;
        Thread.ofVirtual().name("yap-be-paper-chunks-" + user).start(() -> {
            try {
                List<ByteBuf> paperChunks = new ArrayList<>();
                for (BedrockColumnStreamer.Column c : ring) {
                    paperChunks.add(world.chunkFor(c.cx(), c.cz()));
                }
                if (!paperChunks.isEmpty()) {
                    ctx.send(g, paperChunks);
                }
            } catch (Exception e) {
                BedrockBridgeContext.LOG.fine("paper chunk warm: " + e.getMessage());
            }
        });
        BedrockPaperWorldSync sync = ctx.paperWorld;
        if (sync != null && sync.isEnabled()) {
            final UUID u = uuid;
            final double ix = spawn[0];
            final double iy = spawn[1] + 0.1;
            final double iz = spawn[2];
            Thread.ofVirtual().name("yap-be-paper-inject-" + user).start(() -> {
                boolean ok = sync.injectPlayer(user, u, ix, iy, iz);
                if (!ok) {
                    sync.injectBedrockPlayer(u, user);
                    return;
                }
                try {
                    Object player = sync.findOnlinePlayer(user);
                    ctx.skins.applyToPaperPlayer(user, player);
                } catch (Throwable e) {
                    BedrockBridgeContext.LOG.fine("skin→Paper: " + e.getMessage());
                }
                int[][] paperStacks = sync.snapshotInventoryStacksLiveOnly(user, 36);
                if (paperStacks != null) {
                    ctx.inventory.seedStorage(user, paperStacks[0], paperStacks[1]);
                    ctx.send(g, BedrockPacketCodec.inventoryContent(0, paperStacks[0], paperStacks[1]));
                } else {
                    int[] paperInv = sync.snapshotInventoryNetworkIds(user, 36);
                    if (paperInv != null) {
                        ctx.send(g, BedrockPacketCodec.inventoryContent(0, paperInv));
                    }
                }
                BedrockBridgeContext.LOG.info("BE→Paper player online " + user);
            });
        }
        world.mirrorPaperPlayers();
        ctx.pendingPack.remove(guid);
        BedrockBridgeContext.LOG.info("BE spawn ready " + user);
    }

    /** Kept for callers; never allocates a second runtime / StartGame. */
    void sendSpawnSequence(long guid, BedrockSessionManager.BedrockSession session) {
        if (session == null) {
            return;
        }
        if (ctx.loginPhase.get(guid) != BedrockBridgeContext.LoginPhase.SPAWNED) {
            finishLogin(guid, null);
        }
    }

    private BedrockBridgeContext.PendingPack resolvePendingPack(String clientAddress) {
        Optional<ResourcePackOffer> offer = ctx.resourcePackOffer.apply(
                clientAddress == null ? "" : clientAddress);
        if (offer.isEmpty() || !isBedrockCompatiblePack(offer.get())) {
            return null;
        }
        ResourcePackOffer o = offer.get();
        UUID packUuid = BedrockUiCodec.parsePackUuid(o.packId());
        return new BedrockBridgeContext.PendingPack(
                packUuid, "1.0.0", o.forced(), o.url(), Math.max(0L, o.sizeBytes()));
    }

    /** Bedrock needs .mcpack (or CDN that serves BE content), not Java Edition zips. */
    private static boolean isBedrockCompatiblePack(ResourcePackOffer o) {
        if (o == null || o.url() == null || o.url().isBlank()) {
            return false;
        }
        String u = o.url().toLowerCase();
        return u.contains(".mcpack") || o.bedrockCompatible();
    }
}
