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
        actions.add(new BedrockGameplayBridge.GameAction("JOIN", identity.username(), Map.of(
                "protocol", Integer.toString(proto),
                "xuid", identity.xuid(),
                "uuid", identity.javaUuid().toString(),
                "floodgate", "true",
                "runtimeId", Long.toString(runtime)
        )));
        List<ByteBuf> out = new ArrayList<>();
        out.add(BedrockPacketCodec.playStatus(BedrockPacketCodec.PlayStatus.LOGIN_SUCCESS));
        appendResourcePackPackets(out);
        double[] spawn = world.paperSpawnOrDefault();
        int sx = (int) Math.floor(spawn[0]);
        int sy = (int) Math.floor(spawn[1]);
        int sz = (int) Math.floor(spawn[2]);
        out.add(BedrockPacketCodec.startGame(runtime, runtime, "YaPcore",
                sx, sy, sz, identity.javaUuid()));
        out.add(BedrockPacketCodec.updateAttributesDefault(runtime));
        out.add(BedrockPacketCodec.setTime(1000));
        out.add(BedrockPacketCodec.setDifficulty(1));
        out.add(BedrockPacketCodec.setCommandsEnabled(true));
        out.add(BedrockPacketCodec.availableCommandsRich());
        out.add(BedrockPacketCodec.creativeContentFull());
        out.add(BedrockNbtDumps.availableEntityIdentifiers());
        out.add(BedrockNbtDumps.biomeDefinitionList());
        out.add(BedrockPacketCodec.playerListAddSelf(identity.javaUuid(), runtime, identity.username()));
        out.add(BedrockPacketCodec.playStatus(BedrockPacketCodec.PlayStatus.PLAYER_SPAWN));
        out.add(BedrockPacketCodec.chunkRadiusUpdated(8));
        out.add(BedrockPacketCodec.networkChunkPublisherUpdate(sx, sy, sz, 128));
        ctx.columns.setRadius(guid, 8);
        List<BedrockColumnStreamer.Column> ring = ctx.columns.initialRing(guid, sx, sz, 2);
        for (BedrockColumnStreamer.Column c : ring) {
            out.add(BedrockPacketCodec.levelChunkFlat(c.cx(), c.cz()));
        }
        ctx.inventory.ensure(identity.username());
        out.add(BedrockPacketCodec.inventoryContent(0, ctx.inventory.storageNetworkIds(identity.username())));
        out.addAll(ctx.entities.snapshotPackets(runtime));
        ctx.send(guid, out);
        final long g = guid;
        Thread.ofVirtual().name("yap-be-paper-chunks-" + identity.username()).start(() -> {
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
            final String user = identity.username();
            final UUID uuid = identity.javaUuid();
            final double ix = spawn[0];
            final double iy = spawn[1] + 0.1;
            final double iz = spawn[2];
            Thread.ofVirtual().name("yap-be-paper-inject-" + user).start(() -> {
                boolean ok = sync.injectPlayer(user, uuid, ix, iy, iz);
                if (!ok) {
                    sync.injectBedrockPlayer(uuid, user);
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
        BedrockBridgeContext.LOG.info("BE login " + identity.username() + " xuid=" + identity.xuid()
                + " uuid=" + identity.javaUuid());
    }

    void sendSpawnSequence(long guid, BedrockSessionManager.BedrockSession session) {
        if (session == null) {
            return;
        }
        long runtime = ctx.runtimeIds.getAndIncrement();
        UUID uuid = ctx.floodgate.uuidFor(session.username());
        int radius = ctx.chunkRadius.getOrDefault(guid, 8);
        double[] spawn = world.paperSpawnOrDefault();
        int sx = (int) Math.floor(spawn[0]);
        int sy = (int) Math.floor(spawn[1]);
        int sz = (int) Math.floor(spawn[2]);
        List<ByteBuf> out = new ArrayList<>();
        out.add(BedrockPacketCodec.startGame(runtime, runtime, "YaPcore", sx, sy, sz, uuid));
        out.add(BedrockPacketCodec.playStatus(BedrockPacketCodec.PlayStatus.PLAYER_SPAWN));
        out.add(BedrockPacketCodec.chunkRadiusUpdated(radius));
        out.add(BedrockPacketCodec.networkChunkPublisherUpdate(sx, sy, sz, radius * 16));
        ctx.columns.setRadius(guid, radius);
        List<BedrockColumnStreamer.Column> ring = ctx.columns.initialRing(guid, sx, sz, 2);
        for (BedrockColumnStreamer.Column c : ring) {
            out.add(BedrockPacketCodec.levelChunkFlat(c.cx(), c.cz()));
        }
        out.addAll(ctx.entities.snapshotPackets());
        ctx.send(guid, out);
        final long g = guid;
        Thread.ofVirtual().name("yap-be-paper-chunks-spawn-" + guid).start(() -> {
            try {
                List<ByteBuf> paperChunks = new ArrayList<>();
                for (BedrockColumnStreamer.Column c : ring) {
                    paperChunks.add(world.chunkFor(c.cx(), c.cz()));
                }
                if (!paperChunks.isEmpty()) {
                    ctx.send(g, paperChunks);
                }
            } catch (Exception e) {
                BedrockBridgeContext.LOG.fine("spawn paper chunks: " + e.getMessage());
            }
        });
        world.mirrorPaperPlayers();
    }

    private void appendResourcePackPackets(List<ByteBuf> out) {
        Optional<ResourcePackOffer> offer = ctx.resourcePackOffer.get();
        if (offer.isEmpty()) {
            out.add(BedrockPacketCodec.resourcePacksInfoEmpty());
            out.add(BedrockPacketCodec.resourcePackStackEmpty());
            return;
        }
        ResourcePackOffer o = offer.get();
        UUID packUuid = BedrockUiCodec.parsePackUuid(o.packId());
        out.add(BedrockPacketCodec.resourcePacksInfoOffer(packUuid, "0.0.0", 0L, o.url(), o.forced()));
        out.add(BedrockPacketCodec.resourcePackStackOffer(packUuid, "0.0.0", o.forced()));
    }
}
