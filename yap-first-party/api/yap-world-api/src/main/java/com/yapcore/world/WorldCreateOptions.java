package com.yapcore.world;

import java.util.Locale;
import java.util.Objects;

/**
 * Options for creating a new Minecraft world (Multiverse-class).
 * Type / environment strings are case-insensitive Bukkit names
 * ({@code NORMAL}, {@code FLAT}, {@code LARGE_BIOMES}, {@code AMPLIFIED};
 * {@code NORMAL}/{@code NETHER}/{@code THE_END}).
 */
public final class WorldCreateOptions {

    public static final WorldCreateOptions DEFAULTS = new WorldCreateOptions(
            "NORMAL", "NORMAL", null, null, true);

    private final String type;
    private final String environment;
    private final Long seed;
    private final String generator;
    private final boolean generateStructures;

    public WorldCreateOptions(String type, String environment, Long seed,
                              String generator, boolean generateStructures) {
        this.type = normalizeType(type);
        this.environment = normalizeEnvironment(environment);
        this.seed = seed;
        this.generator = blankToNull(generator);
        this.generateStructures = generateStructures;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String type() {
        return type;
    }

    public String environment() {
        return environment;
    }

    /** {@code null} = let the server pick a random seed. */
    public Long seed() {
        return seed;
    }

    /** Custom chunk generator plugin id, or {@code null} for vanilla. */
    public String generator() {
        return generator;
    }

    public boolean generateStructures() {
        return generateStructures;
    }

    public static String normalizeType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "NORMAL";
        }
        String t = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (t) {
            case "FLAT", "SUPERFLAT", "SUPER_FLAT" -> "FLAT";
            case "LARGEBIOMES", "LARGE_BIOME", "LARGE_BIOMES" -> "LARGE_BIOMES";
            case "AMPLIFIED", "AMP" -> "AMPLIFIED";
            case "NORMAL", "DEFAULT" -> "NORMAL";
            default -> t;
        };
    }

    public static String normalizeEnvironment(String raw) {
        if (raw == null || raw.isBlank()) {
            return "NORMAL";
        }
        String e = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (e) {
            case "OVERWORLD", "WORLD", "DIM0" -> "NORMAL";
            case "NETHER", "DIM-1", "HELL" -> "NETHER";
            case "END", "THE_END", "THEEND", "DIM1" -> "THE_END";
            case "NORMAL" -> "NORMAL";
            default -> e;
        };
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @Override
    public String toString() {
        return "WorldCreateOptions{type=" + type
                + ", env=" + environment
                + ", seed=" + seed
                + ", generator=" + generator
                + ", structures=" + generateStructures + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorldCreateOptions that)) {
            return false;
        }
        return generateStructures == that.generateStructures
                && Objects.equals(type, that.type)
                && Objects.equals(environment, that.environment)
                && Objects.equals(seed, that.seed)
                && Objects.equals(generator, that.generator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, environment, seed, generator, generateStructures);
    }

    public static final class Builder {
        private String type = "NORMAL";
        private String environment = "NORMAL";
        private Long seed;
        private String generator;
        private boolean generateStructures = true;

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        public Builder seed(Long seed) {
            this.seed = seed;
            return this;
        }

        public Builder generator(String generator) {
            this.generator = generator;
            return this;
        }

        public Builder generateStructures(boolean generateStructures) {
            this.generateStructures = generateStructures;
            return this;
        }

        public WorldCreateOptions build() {
            return new WorldCreateOptions(type, environment, seed, generator, generateStructures);
        }
    }
}
