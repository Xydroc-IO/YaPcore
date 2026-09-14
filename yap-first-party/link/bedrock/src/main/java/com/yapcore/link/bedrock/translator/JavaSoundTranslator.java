package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket;

/**
 * JE {@code level_event} / world sound → Bedrock LevelEvent / LevelSoundEvent.
 *
 * <p>Uses an explicit registry for common break/place/hurt/ambient ids, then heuristics.
 */
public final class JavaSoundTranslator {

    private static final Map<String, SoundEvent> BY_FRAGMENT = buildRegistry();

    private JavaSoundTranslator() {
    }

    private static Map<String, SoundEvent> buildRegistry() {
        Map<String, SoundEvent> m = new HashMap<>();
        // Dig / place
        put(m, "block.stone.break", SoundEvent.BREAK);
        put(m, "block.stone.place", SoundEvent.PLACE);
        put(m, "block.stone.hit", SoundEvent.HIT);
        put(m, "block.grass.break", SoundEvent.BREAK);
        put(m, "block.grass.place", SoundEvent.PLACE);
        put(m, "block.grass.hit", SoundEvent.HIT);
        put(m, "block.wood.break", SoundEvent.BREAK);
        put(m, "block.wood.place", SoundEvent.PLACE);
        put(m, "block.wood.hit", SoundEvent.HIT);
        put(m, "block.sand.break", SoundEvent.BREAK);
        put(m, "block.sand.place", SoundEvent.PLACE);
        put(m, "block.gravel.break", SoundEvent.BREAK);
        put(m, "block.gravel.place", SoundEvent.PLACE);
        put(m, "block.glass.break", SoundEvent.BREAK);
        put(m, "block.cloth.break", SoundEvent.BREAK);
        put(m, "block.metal.break", SoundEvent.BREAK);
        // Hurt / combat
        put(m, "entity.player.hurt", SoundEvent.HURT);
        put(m, "entity.generic.hurt", SoundEvent.HURT);
        put(m, "entity.zombie.hurt", SoundEvent.HURT);
        put(m, "entity.skeleton.hurt", SoundEvent.HURT);
        put(m, "entity.player.attack.strong", SoundEvent.ATTACK_STRONG);
        put(m, "entity.player.attack.weak", SoundEvent.ATTACK);
        put(m, "entity.player.attack.crit", SoundEvent.ATTACK_CRITICAL);
        put(m, "entity.player.attack.knockback", SoundEvent.ATTACK_NODAMAGE);
        put(m, "entity.player.attack.nodamage", SoundEvent.ATTACK_NODAMAGE);
        // Ambient / step
        put(m, "block.grass.step", SoundEvent.STEP);
        put(m, "block.stone.step", SoundEvent.STEP);
        put(m, "block.wood.step", SoundEvent.STEP);
        put(m, "ambient.cave", SoundEvent.AMBIENT);
        put(m, "weather.rain", SoundEvent.AMBIENT);
        put(m, "entity.experience_orb.pickup", SoundEvent.POP);
        put(m, "entity.item.pickup", SoundEvent.POP);
        return Map.copyOf(m);
    }

    private static void put(Map<String, SoundEvent> m, String key, SoundEvent event) {
        m.put(key, event);
        // Also index last path segment for je_sound_N fallbacks that only carry fragments.
        int dot = key.lastIndexOf('.');
        if (dot > 0 && dot + 1 < key.length()) {
            m.putIfAbsent(key.substring(dot + 1), event);
        }
    }

    /** JE ClientboundLevelEventPacket — event id + pos + data. */
    public static void onLevelEvent(LinkBedrockSession session, int eventId,
                                    double x, double y, double z, int data) {
        if (session == null || !session.isSentSpawnPacket()) {
            return;
        }
        Vector3f pos = Vector3f.from((float) x, (float) y, (float) z);
        if (eventId == 2001) {
            LevelEventPacket destroy = new LevelEventPacket();
            destroy.setType(LevelEvent.PARTICLE_DESTROY_BLOCK);
            destroy.setPosition(pos);
            int rt = data != 0 ? data : session.blockRuntimeAt((int) x, (int) y, (int) z);
            if (rt == 0) {
                rt = session.stoneRuntimeId();
            }
            destroy.setData(rt);
            session.sendUpstreamPacket(destroy);
            LevelSoundEventPacket sound = new LevelSoundEventPacket();
            sound.setSound(SoundEvent.BREAK);
            sound.setPosition(pos);
            sound.setExtraData(rt != 0 ? rt : 1);
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
            LevelEventPacket click = new LevelEventPacket();
            click.setType(eventId == 1001 ? LevelEvent.SOUND_CLICK_FAIL : LevelEvent.SOUND_CLICK);
            click.setPosition(pos);
            click.setData(data);
            session.sendUpstreamPacket(click);
        }
    }

    /** JE ClientboundSoundPacket — registry first, then name heuristics. */
    public static void onSound(LinkBedrockSession session, String soundId,
                               double x, double y, double z) {
        if (session == null || !session.isSentSpawnPacket() || soundId == null) {
            return;
        }
        String id = soundId.toLowerCase(Locale.ROOT);
        if (id.startsWith("minecraft:")) {
            id = id.substring("minecraft:".length());
        }
        SoundEvent event = BY_FRAGMENT.get(id);
        if (event == null) {
            // Try trailing fragment (e.g. entity.player.hurt → hurt)
            int dot = id.lastIndexOf('.');
            if (dot > 0) {
                event = BY_FRAGMENT.get(id.substring(dot + 1));
            }
        }
        if (event == null) {
            if (id.contains("break") || id.contains("dig")) {
                event = SoundEvent.BREAK;
            } else if (id.contains("place")) {
                event = SoundEvent.PLACE;
            } else if (id.contains("hurt") || id.contains("damage")) {
                event = SoundEvent.HURT;
            } else if (id.contains("attack")) {
                event = SoundEvent.ATTACK;
            } else if (id.contains("hit") || id.contains("step")) {
                event = SoundEvent.HIT;
            } else if (id.contains("ambient") || id.contains("rain")) {
                event = SoundEvent.AMBIENT;
            }
        }
        if (event == null) {
            return;
        }
        LevelSoundEventPacket packet = new LevelSoundEventPacket();
        packet.setSound(event);
        packet.setPosition(Vector3f.from((float) x, (float) y, (float) z));
        int extra = session.blockRuntimeAt((int) x, (int) y, (int) z);
        if (extra == 0) {
            extra = session.stoneRuntimeId();
        }
        packet.setExtraData(extra != 0 ? extra : 1);
        packet.setIdentifier(":");
        packet.setBabySound(false);
        packet.setRelativeVolumeDisabled(false);
        packet.setEntityUniqueId(-1L);
        session.sendUpstreamPacket(packet);
    }
}
