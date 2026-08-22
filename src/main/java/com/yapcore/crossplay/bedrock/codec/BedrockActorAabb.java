package com.yapcore.crossplay.bedrock.codec;
public final class BedrockActorAabb { private BedrockActorAabb(){}     /** Approx Bedrock AABB by entity identifier (player default 0.6×1.8). */
    public static float[] forActor(String actorType) {
        if (actorType == null) {
            return new float[]{0.6f, 1.8f};
        }
        String t = actorType.toLowerCase(java.util.Locale.ROOT);
        if (t.contains("player")) {
            return new float[]{0.6f, 1.8f};
        }
        if (t.contains("enderman")) {
            return new float[]{0.6f, 2.9f};
        }
        if (t.contains("iron_golem")) {
            return new float[]{1.4f, 2.7f};
        }
        if (t.contains("villager") || t.contains("zombie") || t.contains("skeleton")
                || t.contains("creeper") || t.contains("witch") || t.contains("pillager")) {
            return new float[]{0.6f, 1.95f};
        }
        if (t.contains("spider") || t.contains("cave_spider")) {
            return new float[]{1.4f, 0.9f};
        }
        if (t.contains("chicken") || t.contains("bat") || t.contains("parrot") || t.contains("bee")) {
            return new float[]{0.4f, 0.7f};
        }
        if (t.contains("cow") || t.contains("pig") || t.contains("sheep") || t.contains("wolf")
                || t.contains("fox") || t.contains("goat")) {
            return new float[]{0.9f, 0.9f};
        }
        if (t.contains("horse") || t.contains("donkey") || t.contains("mule") || t.contains("camel")) {
            return new float[]{1.4f, 1.6f};
        }
        if (t.contains("slime") || t.contains("magma")) {
            return new float[]{0.51f, 0.51f};
        }
        if (t.contains("ghast")) {
            return new float[]{4f, 4f};
        }
        if (t.contains("wither")) {
            return new float[]{0.9f, 3.5f};
        }
        if (t.contains("dragon")) {
            return new float[]{16f, 8f};
        }
        if (t.contains("armor_stand")) {
            return new float[]{0.5f, 1.975f};
        }
        if (t.contains("item") || t.contains("xp_orb") || t.contains("experience")) {
            return new float[]{0.25f, 0.25f};
        }
        return new float[]{0.6f, 1.8f};
    }

}
