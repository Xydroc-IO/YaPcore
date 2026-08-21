package com.yapcore.vehicles.api;

import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Describes display entities attached to a vehicle chassis (local space).
 */
public final class VehicleVisual {

    public enum Kind {
        ITEM,
        BLOCK
    }

    /** Used for runtime mods (tire scale, glass brightness). */
    public enum Role {
        FRAME,
        WHEEL,
        GLASS,
        INTERIOR
    }

    private final Kind kind;
    private final Material material;
    private final Vector offset;
    private final Vector scale;
    private final float yawOffset;
    private final float pitchOffset;
    private final Role role;
    private final int customModelData;
    private final Consumer<Display> customizer;

    private VehicleVisual(Builder b) {
        this.kind = b.kind;
        this.material = b.material;
        this.offset = b.offset.clone();
        this.scale = b.scale.clone();
        this.yawOffset = b.yawOffset;
        this.pitchOffset = b.pitchOffset;
        this.role = b.role;
        this.customModelData = b.customModelData;
        this.customizer = b.customizer;
    }

    public Kind kind() {
        return kind;
    }

    public Material material() {
        return material;
    }

    public Vector offset() {
        return offset.clone();
    }

    public Vector scale() {
        return scale.clone();
    }

    public float yawOffset() {
        return yawOffset;
    }

    public float pitchOffset() {
        return pitchOffset;
    }

    public Role role() {
        return role;
    }

    /** Resource-pack CustomModelData for ITEM visuals (0 = none). */
    public int customModelData() {
        return customModelData;
    }

    public Consumer<Display> customizer() {
        return customizer;
    }

    public static Builder item(Material material) {
        return new Builder(Kind.ITEM, material);
    }

    public static Builder block(Material material) {
        return new Builder(Kind.BLOCK, material);
    }

    /** Apply transformation after spawn (local offset baked into teleport + transform). */
    public void applyTransform(Display display) {
        applyTransform(display, 1.0);
    }

    /** @param scaleMul extra multiplier (tire size upgrades). */
    public void applyTransform(Display display, double scaleMul) {
        double mul = role == Role.WHEEL ? scaleMul : 1.0;
        float sx = (float) (scale.getX() * mul);
        float sy = (float) (scale.getY() * mul);
        float sz = (float) (scale.getZ() * mul);
        Transformation t = new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f((float) Math.toRadians(pitchOffset), 1, 0, 0),
                new Vector3f(sx, sy, sz),
                new AxisAngle4f((float) Math.toRadians(yawOffset), 0, 1, 0)
        );
        display.setTransformation(t);
        display.setInterpolationDuration(1);
        display.setTeleportDuration(1);
        if (display instanceof ItemDisplay itemDisplay && kind == Kind.ITEM) {
            ItemStack stack = new ItemStack(material);
            if (customModelData > 0) {
                stack.editMeta(meta -> meta.setCustomModelData(customModelData));
            }
            itemDisplay.setItemStack(stack);
            itemDisplay.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        }
        if (customizer != null) {
            customizer.accept(display);
        }
    }

    public static final class Builder {
        private final Kind kind;
        private final Material material;
        private Vector offset = new Vector(0, 0.5, 0);
        private Vector scale = new Vector(1, 1, 1);
        private float yawOffset;
        private float pitchOffset;
        private Role role = Role.FRAME;
        private int customModelData;
        private Consumer<Display> customizer;

        private Builder(Kind kind, Material material) {
            this.kind = Objects.requireNonNull(kind);
            this.material = Objects.requireNonNull(material);
        }

        public Builder offset(double x, double y, double z) {
            this.offset = new Vector(x, y, z);
            return this;
        }

        public Builder offset(Vector offset) {
            this.offset = Objects.requireNonNull(offset).clone();
            return this;
        }

        public Builder scale(double s) {
            this.scale = new Vector(s, s, s);
            return this;
        }

        public Builder scale(double x, double y, double z) {
            this.scale = new Vector(x, y, z);
            return this;
        }

        public Builder yawOffset(float deg) {
            this.yawOffset = deg;
            return this;
        }

        public Builder pitchOffset(float deg) {
            this.pitchOffset = deg;
            return this;
        }

        public Builder role(Role role) {
            this.role = Objects.requireNonNull(role);
            return this;
        }

        /** High-res resource-pack model (CustomModelData on paper/item). */
        public Builder customModelData(int cmd) {
            this.customModelData = Math.max(0, cmd);
            return this;
        }

        public Builder customize(Consumer<Display> customizer) {
            this.customizer = customizer;
            return this;
        }

        public VehicleVisual build() {
            return new VehicleVisual(this);
        }
    }

    public static List<VehicleVisual> list(VehicleVisual... visuals) {
        List<VehicleVisual> out = new ArrayList<>();
        for (VehicleVisual v : visuals) {
            out.add(Objects.requireNonNull(v));
        }
        return List.copyOf(out);
    }
}
