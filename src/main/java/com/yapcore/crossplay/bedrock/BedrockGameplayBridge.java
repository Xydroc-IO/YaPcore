package com.yapcore.crossplay.bedrock;

import com.yapcore.crossplay.bedrock.bridge.*;
import com.yapcore.crossplay.floodgate.FloodgateAuth;
import com.yapcore.crossplay.form.FormService;
import com.yapcore.crossplay.skin.SkinService;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    public BedrockGameplayBridge(BedrockSessionManager sessions,
                                 FloodgateAuth floodgate,
                                 SkinService skins,
                                 FormService forms) {
        this.ctx = new BedrockBridgeContext(sessions, floodgate, skins, forms);
        BedrockWorldPush world = new BedrockWorldPush(ctx);
        BedrockInventoryPush inventory = new BedrockInventoryPush(ctx, world);
        BedrockCommandHints commands = new BedrockCommandHints(ctx);
        BedrockUiBridge ui = new BedrockUiBridge(ctx);
        BedrockLoginFlow login = new BedrockLoginFlow(ctx, world, inventory);
        this.dispatch = new BedrockPacketDispatch(ctx, login, world, inventory, commands, ui);
    }

    public void setResourcePackOfferSupplier(java.util.function.Supplier<java.util.Optional<com.yapcore.resourcepack.ResourcePackOffer>> supplier) {
        ctx.resourcePackOffer = supplier != null ? supplier : java.util.Optional::empty;
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

    public List<GameAction> onGameBatch(long guid, String address, ByteBuf batch) {
        List<GameAction> actions = new ArrayList<>();
        while (batch.isReadable()) {
            try {
                int len = BedrockPacketCodec.readUnsignedVarInt(batch);
                if (len <= 0 || batch.readableBytes() < len) {
                    break;
                }
                ByteBuf pkt = batch.readSlice(len);
                BedrockPacketCodec.Decoded decoded = BedrockPacketCodec.decode(pkt);
                dispatch.handlePacket(guid, address, decoded, actions);
            } catch (Exception e) {
                BedrockBridgeContext.LOG.fine("BE batch parse: " + e.getMessage());
                break;
            }
        }
        return actions;
    }

    public ByteBuf encodeChatToClient(String source, String message) {
        return BedrockPacketCodec.textChat(source, message);
    }

    public ByteBuf encodeBlockUpdate(int x, int y, int z, int runtimeId) {
        return BedrockPacketCodec.updateBlock(x, y, z, runtimeId, 0, 0);
    }

    public void onDisconnect(long guid) {
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
        ctx.sessions.close(guid);
        ctx.chunkRadius.remove(guid);
        ctx.columns.clear(guid);
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
