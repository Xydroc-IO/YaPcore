package com.yapcore.crossplay.bedrock;

import com.yapcore.crossplay.bedrock.bridge.*;
import com.yapcore.crossplay.bedrock.cloudburst.CloudburstSession;
import com.yapcore.crossplay.emote.EmoteAuthorityService;
import com.yapcore.crossplay.emote.EmoteClip;
import com.yapcore.crossplay.emote.EmoteJeRelay;
import com.yapcore.crossplay.floodgate.FloodgateAuth;
import com.yapcore.crossplay.form.FormService;
import com.yapcore.crossplay.movement.MovementAuthorityService;
import com.yapcore.crossplay.skin.SkinService;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.LongConsumer;

/**
 * Decodes Bedrock game batches → actions; builds login/spawn reply sequences.
 * Public facade — domain logic lives in {@code bridge.*} helpers.
 */
public final class BedrockGameplayBridge {

    public record GameAction(String type, String username, Map<String, String> payload) {
    }

    private final BedrockBridgeContext ctx;
    private final BedrockPacketDispatch dispatch;
    private final BedrockUiBridge ui;
    private final BedrockLoginFlow login;
    private final BedrockEmotePush emotePush;
    private final EmoteAuthorityService emotes;
    private final MovementAuthorityService movement;
    private final BedrockMovementPush movementPush;

    public BedrockGameplayBridge(BedrockSessionManager sessions,
                                 FloodgateAuth floodgate,
                                 SkinService skins,
                                 FormService forms) {
        this.ctx = new BedrockBridgeContext(sessions, floodgate, skins, forms);
        BedrockWorldPush world = new BedrockWorldPush(ctx);
        BedrockInventoryPush inventory = new BedrockInventoryPush(ctx, world);
        BedrockCommandHints commands = new BedrockCommandHints(ctx);
        this.ui = new BedrockUiBridge(ctx);
        this.login = new BedrockLoginFlow(ctx, world, inventory);
        this.dispatch = new BedrockPacketDispatch(ctx, login, world, inventory, commands, ui);
        this.emotePush = new BedrockEmotePush(ctx);
        this.ctx.emotePush = emotePush;
        this.ctx.gameplayBridge = this;
        this.emotes = EmoteAuthorityService.createDefault();
        this.emotes.setOnPlay(this::onEmotePlay);
        this.movement = MovementAuthorityService.createDefault();
        this.movementPush = new BedrockMovementPush(ctx, movement.table());
    }

    private void onEmotePlay(EmoteAuthorityService.PlayEvent event) {
        ClassLoader paper = null;
        if (ctx.paperWorld != null) {
            try {
                paper = ctx.paperWorld.liveLoaderPublic();
            } catch (Exception ignored) {
            }
        }
        EmoteJeRelay.broadcastViaPaper(paper, event);

        Long guid = emotePush.guidForUsername(event.username());
        Long runtime = emotePush.runtimeForUsername(event.username());
        if (runtime == null) {
            runtime = 1L;
        }
        String xuid = "";
        if (ctx.floodgate != null && event.username() != null) {
            var id = ctx.floodgate.get(event.username());
            if (id != null && id.xuid() != null) {
                xuid = id.xuid();
            }
        }
        long except = guid != null ? guid : -1L;
        emotePush.broadcastEmote(except, runtime, event.emoteId(), event.clip(), xuid);
    }

    public EmoteAuthorityService emotes() {
        return emotes;
    }

    public BedrockEmotePush emotePush() {
        return emotePush;
    }

    /**
     * Catalog-gated play used by HTTP / JE Tailor relay / translator.
     */
    public Optional<EmoteAuthorityService.PlayEvent> playEmote(
            UUID uuid, String username, String emoteId, String source) {
        return emotes.tryPlay(uuid, username, emoteId, source);
    }

    public Optional<EmoteClip> emoteClip(String emoteId) {
        return emotes.clips().byId(emoteId);
    }

    public MovementAuthorityService movement() {
        return movement;
    }

    public BedrockMovementPush movementPush() {
        return movementPush;
    }

    public BedrockUiBridge ui() {
        return ui;
    }

    public void setResourcePackOfferSupplier(java.util.function.Function<String, java.util.Optional<com.yapcore.resourcepack.ResourcePackOffer>> supplier) {
        ctx.resourcePackOffer = supplier != null ? supplier : a -> java.util.Optional.empty();
    }

    public void setJoinEmitter(java.util.function.Consumer<GameAction> emitJoin) {
        ctx.emitJoin = emitJoin != null ? emitJoin : a -> {
        };
    }

    public void setPaperWorld(BedrockPaperWorldSync paperWorld) {
        ctx.paperWorld = paperWorld;
        ctx.inventory.attachPaper(paperWorld);
        ctx.inventory.attachContainers(ctx.containers);
        ctx.containers.attachPaper(paperWorld);
        ctx.containers.attachInventory(ctx.inventory);
    }

    public BedrockEntityTracker entities() {
        return ctx.entities;
    }

    public BedrockContainerBridge containers() {
        return ctx.containers;
    }

    public void setOutbound(BiConsumer<Long, List<ByteBuf>> outbound) {
        ctx.outbound = outbound != null ? outbound : (g, p) -> { };
        ctx.entities.setBroadcast((except, packets) -> {
            for (Long guid : ctx.sessions.allGuids()) {
                if (except != null && except >= 0 && except.equals(guid)) {
                    continue;
                }
                ctx.outbound.accept(guid, copyPackets(packets));
            }
        });
        ctx.containers.setSender((username, pkt) -> {
            BedrockSessionManager.BedrockSession s = ctx.sessions.byUsername(username);
            if (s != null) {
                ctx.outbound.accept(s.guid(), List.of(pkt));
            } else {
                pkt.release();
            }
        });
    }

    /** Called by DualStackGateway so RakNetPeer can arm compressor-in-header. */
    public void setCompressionArmed(LongConsumer compressionArmed) {
        ctx.compressionArmed = compressionArmed;
    }

    /**
     * Geyser {@code BedrockPeer.enableEncryption} — arm AES on the RakNet peer after
     * ServerToClientHandshake is sent.
     */
    public void setEncryptionEnabled(java.util.function.BiConsumer<Long, javax.crypto.SecretKey> encryptionEnabled) {
        ctx.encryptionEnabled = encryptionEnabled;
    }

    public List<GameAction> onGameBatch(long guid, String address, ByteBuf batch) {
        List<GameAction> actions = new ArrayList<>();
        CloudburstSession cb = ctx.getCloudburst(guid);
        StringBuilder idLog = null;
        BedrockBridgeContext.LoginPhase phase = ctx.loginPhase.get(guid);
        boolean logIds = phase == BedrockBridgeContext.LoginPhase.AWAITING_CLIENT_INIT
                || phase == BedrockBridgeContext.LoginPhase.AWAITING_ENCRYPTION
                || phase == BedrockBridgeContext.LoginPhase.AWAITING_PACKS
                || phase == BedrockBridgeContext.LoginPhase.AWAITING_STACK_COMPLETE;
        if (logIds) {
            idLog = new StringBuilder();
        }
        while (batch.isReadable()) {
            try {
                int len = BedrockPacketCodec.readUnsignedVarInt(batch);
                if (len <= 0 || batch.readableBytes() < len) {
                    break;
                }
                ByteBuf pkt = batch.readSlice(len);
                if (cb != null) {
                    ByteBuf slice = pkt.duplicate();
                    int id = BedrockPacketCodec.readUnsignedVarInt(slice);
                    if (BedrockJoinProbe.isActive(guid)) {
                        BedrockJoinProbe.noteC2S(guid, id, len);
                    }
                    if (idLog != null) {
                        if (idLog.length() > 0) {
                            idLog.append(',');
                        }
                        idLog.append("0x").append(Integer.toHexString(id));
                        if (id == BedrockPacketIds.SET_LOCAL_PLAYER_AS_INITIALIZED.id) {
                            idLog.append("=SetLocalPlayerAsInitialized");
                        }
                    }
                    // Keep raw body for Login floodgate parse (re-encode loses wire layout).
                    if (id == BedrockPacketIds.LOGIN.id) {
                        dispatch.handlePacket(guid, address, new BedrockPacketCodec.Decoded(id, slice), actions);
                    } else {
                        org.cloudburstmc.protocol.bedrock.packet.BedrockPacket packet = cb.decode(id, slice);
                        dispatch.handleCloudburstPacket(guid, address, packet, actions);
                    }
                } else {
                    BedrockPacketCodec.Decoded decoded = BedrockPacketCodec.decode(pkt);
                    if (BedrockJoinProbe.isActive(guid)) {
                        BedrockJoinProbe.noteC2S(guid, decoded.id(), len);
                    }
                    if (idLog != null) {
                        if (idLog.length() > 0) {
                            idLog.append(',');
                        }
                        idLog.append("0x").append(Integer.toHexString(decoded.id()));
                    }
                    dispatch.handlePacket(guid, address, decoded, actions);
                    cb = ctx.getCloudburst(guid);
                }
            } catch (Exception e) {
                BedrockBridgeContext.LOG.info("BE batch parse fail: " + e.getMessage()
                        + (idLog != null && idLog.length() > 0 ? " idsSoFar=[" + idLog + "]" : ""));
                break;
            }
        }
        if (idLog != null && idLog.length() > 0) {
            BedrockBridgeContext.LOG.info("BE inbound packet ids guid=" + Long.toHexString(guid)
                    + " phase=" + phase + " ids=[" + idLog + "]");
        }
        return actions;
    }

    public ByteBuf encodeChatToClient(String source, String message) {
        return BedrockPacketCodec.textChat(source, message);
    }

    public ByteBuf encodeBlockUpdate(int x, int y, int z, int runtimeId) {
        return BedrockPacketCodec.updateBlock(x, y, z, runtimeId, 0, 0);
    }

    /** Send a single game packet to a Bedrock session by GUID. */
    public void sendToGuid(long guid, ByteBuf packet) {
        if (packet == null) {
            return;
        }
        ctx.send(guid, packet);
    }

    public void onDisconnect(long guid) {
        BedrockJoinProbe.finish(guid, "raknet_client_disconnect_or_close");
        Long runtime = ctx.runtimeByGuid.remove(guid);
        BedrockSessionManager.BedrockSession session = ctx.sessions.get(guid);
        if (session != null) {
            ctx.entities.removeByName(session.username());
            BedrockPaperWorldSync sync = ctx.paperWorld;
            if (sync != null) {
                sync.ejectPlayer(session.username());
                sync.ejectBedrockPlayer(session.username());
            }
        } else if (runtime != null) {
            ctx.entities.remove(runtime);
        }
        login.removeSession(guid);
        ctx.sessions.close(guid);
        ctx.closeCloudburst(guid);
        ctx.chunkRadius.remove(guid);
        ctx.columns.clear(guid);
        ctx.loginPhase.remove(guid);
        ctx.clientInitialized.remove(guid);
        ctx.startConfigEmptyFillSent.remove(guid);
        ctx.lastPublisherChunk.remove(guid);
        ctx.pendingPack.remove(guid);
        ctx.pendingJoin.remove(guid);
        ctx.pendingSpawn.remove(guid);
        if (session != null && session.username() != null) {
            ctx.inventoryFingerprint.remove(session.username().toLowerCase());
        }
    }

    private static List<ByteBuf> copyPackets(List<ByteBuf> packets) {
        List<ByteBuf> copy = new ArrayList<>(packets.size());
        for (ByteBuf p : packets) {
            copy.add(p.retainedDuplicate());
        }
        return copy;
    }
}
