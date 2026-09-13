package com.yapcore.crossplay.bedrock.parity;

import java.util.Objects;

/** Pinned Bedrock protocol/resource band for parity extracts. */
public final class ParityBand {

    public static final String DEFAULT = "band_26_50";
    public static final String RESOURCE_ROOT = "protocol/bedrock/parity/";

    private final String id;

    public ParityBand(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("parity band required");
        }
        String t = id.trim();
        if (!t.startsWith("band_")) {
            throw new IllegalArgumentException("parity band must start with band_: " + t);
        }
        this.id = t;
    }

    public static ParityBand of(String id) {
        return new ParityBand(id == null || id.isBlank() ? DEFAULT : id);
    }

    public String id() {
        return id;
    }

    public String resourcePrefix() {
        return RESOURCE_ROOT + id + "/";
    }

    public String catalogPath(String name) {
        return resourcePrefix() + "catalogs/" + name;
    }

    public String fixturePath(String relative) {
        return resourcePrefix() + "fixtures/" + relative;
    }

    public String convertedPath(String relative) {
        return resourcePrefix() + "converted/" + relative;
    }

    public String provenancePath() {
        return resourcePrefix() + "provenance/manifest.v1.json";
    }

    /** Cloudburst palette folder sibling under protocol/bedrock/cloudburst/. */
    public String cloudburstPaletteDir() {
        return "protocol/bedrock/cloudburst/" + id + "/";
    }

    public String paletteSuffix() {
        // band_26_50 → 26_50
        return id.substring("band_".length());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ParityBand b && id.equals(b.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return id;
    }
}
