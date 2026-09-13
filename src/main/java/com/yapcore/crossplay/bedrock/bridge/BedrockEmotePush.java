package com.yapcore.crossplay.bedrock.bridge;

import com.yapcore.crossplay.bedrock.BedrockSessionManager;
import com.yapcore.crossplay.bedrock.cloudburst.CloudburstSession;
import com.yapcore.crossplay.emote.EmoteClip;
import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.data.EmoteFlag;
import org.cloudburstmc.protocol.bedrock.packet.EmoteListPacket;
import org.cloudburstmc.protocol.bedrock.packet.EmotePacket;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/** Outbound Bedrock emote packets (Cloudburst encode). */
public final class BedrockEmotePush {

    private final BedrockBridgeContext ctx;

    public BedrockEmotePush(BedrockBridgeContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Broadcast {@link EmotePacket} to all Bedrock sessions except {@code exceptGuid}
     * (pass negative to include everyone).
     */
    public void broadcastEmote(long exceptGuid, long runtimeEntityId, String emoteId, EmoteClip clip, String xuid) {
        if (emoteId == null || emoteId.isBlank()) {
            return;
        }
        for (Long guid : ctx.sessions.allGuids()) {
            if (exceptGuid >= 0L && exceptGuid == guid) {
                continue;
            }
            sendEmote(guid, runtimeEntityId, emoteId, clip, xuid);
        }
    }

    public void sendEmote(long guid, long runtimeEntityId, String emoteId, EmoteClip clip, String xuid) {
        CloudburstSession cb = ctx.getCloudburst(guid);
        if (cb == null) {
            return;
        }
        try {
            EmotePacket pkt = new EmotePacket();
            pkt.setRuntimeEntityId(runtimeEntityId);
            pkt.setEmoteId(emoteId);
            pkt.setXuid(xuid == null ? "" : xuid);
            pkt.setPlatformId("");
            pkt.setEmoteDuration(clip != null ? clip.durationTicks() : 40);
            pkt.getFlags().add(EmoteFlag.SERVER_SIDE);
            ByteBuf encoded = cb.encode(pkt);
            ctx.send(guid, encoded);
        } catch (Exception e) {
            BedrockBridgeContext.LOG.log(Level.FINE, "EmotePacket encode failed guid=" + guid, e);
        }
    }

    /** Offer frozen catalog emote UUIDs to a joining Bedrock client. */
    public void sendCatalogEmoteList(long guid, long runtimeEntityId, Iterable<UUID> emoteIds) {
        CloudburstSession cb = ctx.getCloudburst(guid);
        if (cb == null || emoteIds == null) {
            return;
        }
        try {
            EmoteListPacket list = new EmoteListPacket();
            list.setRuntimeEntityId(runtimeEntityId);
            for (UUID id : emoteIds) {
                if (id != null) {
                    list.getPieceIds().add(id);
                }
            }
            ctx.send(guid, cb.encode(list));
        } catch (Exception e) {
            BedrockBridgeContext.LOG.log(Level.FINE, "EmoteListPacket encode failed guid=" + guid, e);
        }
    }

    public static List<UUID> parseEmoteUuids(Iterable<String> ids) {
        List<UUID> out = new ArrayList<>();
        if (ids == null) {
            return out;
        }
        for (String s : ids) {
            if (s == null || s.isBlank()) {
                continue;
            }
            try {
                out.add(UUID.fromString(s.trim()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return out;
    }

    public Long runtimeForUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        BedrockSessionManager.BedrockSession s = ctx.sessions.byUsername(username);
        if (s == null) {
            return null;
        }
        return ctx.runtimeByGuid.get(s.guid());
    }

    public Long guidForUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        BedrockSessionManager.BedrockSession s = ctx.sessions.byUsername(username);
        return s == null ? null : s.guid();
    }
}
