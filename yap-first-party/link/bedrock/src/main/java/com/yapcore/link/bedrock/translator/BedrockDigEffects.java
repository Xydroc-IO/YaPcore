package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.session.LinkBedrockSession;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket;

/**
 * Bedrock dig crack animation + break/place/hit sounds (Geyser {@code BlockBreakHandler} lite).
 */
public final class BedrockDigEffects {

    /** ~1 second break bar for generic stone-ish dig when hardness is unknown. */
    private static final int DEFAULT_BREAK_TICKS = 20;

    private BedrockDigEffects() {
    }

    public static void startBreak(LinkBedrockSession session, int x, int y, int z) {
        if (session == null) {
            return;
        }
        Vector3f pos = Vector3f.from(x, y, z);
        LevelEventPacket start = new LevelEventPacket();
        start.setType(LevelEvent.BLOCK_START_BREAK);
        start.setPosition(pos);
        start.setData(65535 / DEFAULT_BREAK_TICKS);
        session.sendUpstreamPacket(start);

        int blockRt = digBlockRuntime(session);
        LevelEventPacket crack = new LevelEventPacket();
        crack.setType(LevelEvent.PARTICLE_CRACK_BLOCK);
        crack.setPosition(pos.add(0.5f, 0.5f, 0.5f));
        crack.setData(blockRt);
        session.sendUpstreamPacket(crack);

        hitSound(session, x, y, z, blockRt);
    }

    public static void continueBreak(LinkBedrockSession session, int x, int y, int z) {
        if (session == null) {
            return;
        }
        Vector3f pos = Vector3f.from(x, y, z);
        LevelEventPacket update = new LevelEventPacket();
        update.setType(LevelEvent.BLOCK_UPDATE_BREAK);
        update.setPosition(pos);
        update.setData(65535 / DEFAULT_BREAK_TICKS);
        session.sendUpstreamPacket(update);

        int blockRt = digBlockRuntime(session);
        LevelEventPacket crack = new LevelEventPacket();
        crack.setType(LevelEvent.PARTICLE_CRACK_BLOCK);
        crack.setPosition(pos.add(0.5f, 0.5f, 0.5f));
        crack.setData(blockRt);
        session.sendUpstreamPacket(crack);

        hitSound(session, x, y, z, blockRt);
    }

    public static void stopBreak(LinkBedrockSession session, int x, int y, int z, boolean destroyed) {
        if (session == null) {
            return;
        }
        Vector3f pos = Vector3f.from(x, y, z);
        LevelEventPacket stop = new LevelEventPacket();
        stop.setType(LevelEvent.BLOCK_STOP_BREAK);
        stop.setPosition(pos);
        stop.setData(0);
        session.sendUpstreamPacket(stop);

        if (destroyed) {
            int blockRt = digBlockRuntime(session);
            LevelEventPacket destroy = new LevelEventPacket();
            destroy.setType(LevelEvent.PARTICLE_DESTROY_BLOCK);
            destroy.setPosition(pos.add(0.5f, 0.5f, 0.5f));
            destroy.setData(blockRt);
            session.sendUpstreamPacket(destroy);
            // SoundEvent.BREAK with block runtime = dig break; extraData=-1 plays item-break.
            sound(session, SoundEvent.BREAK, x, y, z, blockRt);
        }
    }

    public static void placeSound(LinkBedrockSession session, int x, int y, int z) {
        sound(session, SoundEvent.PLACE, x, y, z, digBlockRuntime(session));
    }

    private static void hitSound(LinkBedrockSession session, int x, int y, int z, int blockRuntime) {
        sound(session, SoundEvent.HIT, x, y, z, blockRuntime);
    }

    private static int digBlockRuntime(LinkBedrockSession session) {
        return resolveDigExtraData(session.stoneRuntimeId(), session.airRuntimeId());
    }

    /**
     * LevelSoundEvent extraData for dig hit/break/place.
     * Must never be {@code -1} — that plays the Bedrock item/tool-break cue.
     */
    public static int resolveDigExtraData(int stoneRuntimeId, int airRuntimeId) {
        if (stoneRuntimeId != 0) {
            return stoneRuntimeId;
        }
        return Math.max(1, airRuntimeId);
    }

    private static void sound(LinkBedrockSession session, SoundEvent event, int x, int y, int z,
                              int blockRuntime) {
        LevelSoundEventPacket packet = new LevelSoundEventPacket();
        packet.setSound(event);
        packet.setPosition(Vector3f.from(x + 0.5f, y + 0.5f, z + 0.5f));
        packet.setExtraData(blockRuntime);
        packet.setIdentifier(":");
        packet.setBabySound(false);
        packet.setRelativeVolumeDisabled(false);
        packet.setEntityUniqueId(-1L);
        session.sendUpstreamPacket(packet);
    }
}
