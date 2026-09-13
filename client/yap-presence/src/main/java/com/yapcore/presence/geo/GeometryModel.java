package com.yapcore.presence.geo;

import java.util.ArrayList;
import java.util.List;

/** Ported Bedrock / yap.geometry bone+cube list for JE cube drawing. */
public final class GeometryModel {

    private final String identifier;
    private final int textureWidth;
    private final int textureHeight;
    private final List<Bone> bones;

    public GeometryModel(String identifier, int textureWidth, int textureHeight, List<Bone> bones) {
        this.identifier = identifier == null ? "" : identifier;
        this.textureWidth = textureWidth <= 0 ? 64 : textureWidth;
        this.textureHeight = textureHeight <= 0 ? 64 : textureHeight;
        this.bones = bones == null ? List.of() : List.copyOf(bones);
    }

    public String identifier() {
        return identifier;
    }

    public int textureWidth() {
        return textureWidth;
    }

    public int textureHeight() {
        return textureHeight;
    }

    public List<Bone> bones() {
        return bones;
    }

    public boolean hasCubes() {
        for (Bone bone : bones) {
            if (!bone.cubes().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public record Bone(
            String name,
            String parent,
            float[] pivot,
            float[] rotation,
            List<Cube> cubes) {
        public Bone {
            name = name == null ? "" : name;
            parent = parent == null ? "" : parent;
            pivot = pivot == null ? new float[]{0, 0, 0} : pivot.clone();
            rotation = rotation == null ? new float[]{0, 0, 0} : rotation.clone();
            cubes = cubes == null ? List.of() : List.copyOf(cubes);
        }
    }

    public record Cube(
            float[] origin,
            float[] size,
            float inflate,
            boolean mirror,
            float[] uvBox,
            PerFaceUv perFaceUv,
            float[] rotation,
            float[] pivot) {
        public Cube {
            origin = origin == null ? new float[]{0, 0, 0} : origin.clone();
            size = size == null ? new float[]{0, 0, 0} : size.clone();
            uvBox = uvBox == null ? null : uvBox.clone();
            rotation = rotation == null ? null : rotation.clone();
            pivot = pivot == null ? null : pivot.clone();
        }

        public boolean hasBoxUv() {
            return uvBox != null && uvBox.length >= 2;
        }
    }

    /** Optional per-face UV (north/east/south/west/up/down) with [u, v, u2, v2] or [u, v]. */
    public record PerFaceUv(
            float[] north,
            float[] east,
            float[] south,
            float[] west,
            float[] up,
            float[] down) {
        public PerFaceUv {
            north = copy(north);
            east = copy(east);
            south = copy(south);
            west = copy(west);
            up = copy(up);
            down = copy(down);
        }

        private static float[] copy(float[] a) {
            return a == null ? null : a.clone();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String identifier = "";
        private int textureWidth = 64;
        private int textureHeight = 64;
        private final List<Bone> bones = new ArrayList<>();

        public Builder identifier(String id) {
            this.identifier = id;
            return this;
        }

        public Builder textureSize(int w, int h) {
            this.textureWidth = w;
            this.textureHeight = h;
            return this;
        }

        public Builder addBone(Bone bone) {
            bones.add(bone);
            return this;
        }

        public GeometryModel build() {
            return new GeometryModel(identifier, textureWidth, textureHeight, bones);
        }
    }
}
