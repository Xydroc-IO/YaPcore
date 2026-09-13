package com.yapcore.presence.emote;

import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Active emote playback per player UUID. */
public final class PresenceEmoteStore {

    public record Active(String emoteId, EmoteClipLoader.Clip clip, long startNanos) {
        public double elapsedSeconds() {
            return (System.nanoTime() - startNanos) / 1_000_000_000.0;
        }

        public boolean finished() {
            if (clip.loop()) {
                return false;
            }
            return elapsedSeconds() > clip.lengthSeconds() + 0.05;
        }

        public float[] rotation(String bone) {
            double t = elapsedSeconds();
            if (clip.loop() && clip.lengthSeconds() > 0) {
                t = t % clip.lengthSeconds();
            }
            NavigableMap<Double, float[]> keys = resolveBoneKeys(clip, bone);
            return EmoteClipLoader.sample(keys, t);
        }
    }

    private static final Map<UUID, Active> ACTIVE = new ConcurrentHashMap<>();

    private PresenceEmoteStore() {
    }

    public static void play(UUID uuid, String emoteId) {
        if (uuid == null || emoteId == null) {
            return;
        }
        Optional<EmoteClipLoader.Clip> clip = EmoteClipLoader.byId(emoteId);
        if (clip.isEmpty()) {
            return;
        }
        ACTIVE.put(uuid, new Active(emoteId, clip.get(), System.nanoTime()));
    }

    public static Optional<Active> get(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        Active a = ACTIVE.get(uuid);
        if (a == null) {
            return Optional.empty();
        }
        if (a.finished()) {
            ACTIVE.remove(uuid, a);
            return Optional.empty();
        }
        return Optional.of(a);
    }

    public static void clear(UUID uuid) {
        if (uuid != null) {
            ACTIVE.remove(uuid);
        }
    }

    /** Map JE / Bedrock bone names onto clip keys (camelCase + hip→body). */
    static NavigableMap<Double, float[]> resolveBoneKeys(EmoteClipLoader.Clip clip, String bone) {
        if (clip == null || bone == null || bone.isBlank()) {
            return null;
        }
        Map<String, NavigableMap<Double, float[]>> bones = clip.boneRotations();
        NavigableMap<Double, float[]> keys = bones.get(bone);
        if (keys != null) {
            return keys;
        }
        String n = bone.toLowerCase(Locale.ROOT).replace("_", "");
        String alias = switch (n) {
            case "rightarm", "armright" -> "rightArm";
            case "leftarm", "armleft" -> "leftArm";
            case "rightleg", "legright" -> "rightLeg";
            case "leftleg", "legleft" -> "leftLeg";
            case "hip", "waist", "torso", "jacket", "root" -> "body";
            case "hat" -> "head";
            default -> bone;
        };
        keys = bones.get(alias);
        if (keys != null) {
            return keys;
        }
        // Last resort: case-insensitive scan
        for (Map.Entry<String, NavigableMap<Double, float[]>> e : bones.entrySet()) {
            if (e.getKey().equalsIgnoreCase(bone) || e.getKey().equalsIgnoreCase(alias)) {
                return e.getValue();
            }
            // hip tracks often hold the torso lean when body is empty
            if ("body".equals(alias) && "hip".equalsIgnoreCase(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }
}
