package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket;

/**
 * JE {@code level_event} / world sound → Bedrock LevelEvent / LevelSoundEvent (minimal).
 *
 * <p>JE level_event 2001 = block break particles+sound; 2000-series world events.
 */
public final class JavaSoundTranslator {

    private JavaSoundTranslator() {
    }

    /** JE ClientboundLevelEventPacket — event id + pos + data. */
    public static void onLevelEvent(LinkBedrockSession session, int eventId,
                                    double x, double y, double z, int data) {
        if (session == null || !session.isSentSpawnPacket()) {
            return;
        }
        Vector3f pos = Vector3f.from((float) x, (float) y, (float) z);
        if (eventId == 2001) {
            // Block break: particles + break sound
            LevelEventPacket destroy = new LevelEventPacket();
            destroy.setType(LevelEvent.PARTICLE_DESTROY_BLOCK);
            destroy.setPosition(pos);
            destroy.setData(data != 0 ? data : session.airRuntimeId());
            session.sendUpstreamPacket(destroy);
            LevelSoundEventPacket sound = new LevelSoundEventPacket();
            sound.setSound(SoundEvent.BREAK);
            sound.setPosition(pos);
            // JE level_event data is often a JE block state id; when missing use solid BE runtime.
            // extraData=-1 makes SoundEvent.BREAK play the item-break / tool-snap cue.
            int extra = data != 0 ? data : session.stoneRuntimeId();
            sound.setExtraData(extra != 0 ? extra : 1);
            sound.setIdentifier(":");
            sound.setBabySound(false);
            sound.setRelativeVolumeDisabled(false);
            sound.setEntityUniqueId(-1L);
            session.sendUpstreamPacket(sound);
            BedrockJoinProbe.noteEvent(session.guid(), "java_level_event→be BREAK @"
                    + (int) x + "," + (int) y + "," + (int) z);
            return;
        }
        if (eventId == 1000 || eventId == 1001 || eventId == 1030 || eventId == 1031) {
            // click / door-ish — map to click
            LevelEventPacket click = new LevelEventPacket();
            click.setType(eventId == 1001 ? LevelEvent.SOUND_CLICK_FAIL : LevelEvent.SOUND_CLICK);
            click.setPosition(pos);
            click.setData(data);
            session.sendUpstreamPacket(click);
        }
    }

    /** JE ClientboundSoundPacket — best-effort place/break/hit by name fragment. */
    public static void onSound(LinkBedrockSession session, String soundId,
                               double x, double y, double z) {
        if (session == null || !session.isSentSpawnPacket() || soundId == null) {
            return;
        }
        String id = soundId.toLowerCase();
        SoundEvent event = null;
        if (id.contains("break") || id.contains("dig")) {
            event = SoundEvent.BREAK;
        } else if (id.contains("place")) {
            event = SoundEvent.PLACE;
        } else if (id.contains("hit") || id.contains("step")) {
            event = SoundEvent.HIT;
        }
        if (event == null) {
            return;
        }
        LevelSoundEventPacket packet = new LevelSoundEventPacket();
        packet.setSound(event);
        packet.setPosition(Vector3f.from((float) x, (float) y, (float) z));
        int extra = session.stoneRuntimeId();
        packet.setExtraData(extra != 0 ? extra : 1);
        packet.setIdentifier(":");
        packet.setBabySound(false);
        packet.setRelativeVolumeDisabled(false);
        packet.setEntityUniqueId(-1L);
        session.sendUpstreamPacket(packet);
    }
}
