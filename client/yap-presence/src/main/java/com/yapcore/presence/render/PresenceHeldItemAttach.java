package com.yapcore.presence.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yapcore.presence.PresenceSkin;
import com.yapcore.presence.PresenceSkinStore;
import com.yapcore.presence.geo.GeometryModel;
import com.yapcore.presence.geo.GeometryModel.Bone;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Presence geo keeps Bedrock Y-up arms (hand at −Y from the arm pivot). Vanilla
 * {@code ItemInHandLayer} seats items on Java +Y (offset Z=−10 after −90°X).
 * Those are opposite ends of the limb — map the Bedrock {@code rightItem}/
 * {@code leftItem} pivot through the same −90°X/+180°Y frame so the hilt meets
 * the cuff and the blade tips the same way as vanilla.
 *
 * <p>After −90°X then 180°Y, arm-local {@code (dx,dy,dz)} lands at translate
 * {@code (−dx, −dz, −dy)}. For standard humanoid {@code (−1,−7,1)} that is
 * {@code (1, −1, 7)} instead of vanilla {@code (±1, 2, −10)}.
 */
public final class PresenceHeldItemAttach {
    private static final float VANILLA_X = 1.0f;
    private static final float VANILLA_Y = 2.0f;
    private static final float VANILLA_Z = -10.0f;

    private static final ThreadLocal<float[]> CORRECTIVE = new ThreadLocal<>();

    private PresenceHeldItemAttach() {}

    public static void begin(ArmedEntityRenderState state, HumanoidArm arm) {
        CORRECTIVE.set(null);
        if (!(state instanceof AvatarRenderState avatar)) {
            return;
        }
        Optional<UUID> uuid = PresenceGeometryLayer.resolveUuid(avatar);
        if (uuid.isEmpty()) {
            return;
        }
        Optional<PresenceSkin> skin = PresenceSkinStore.get(uuid.get());
        if (skin.isEmpty() || !skin.get().hasRenderableGeometry()) {
            return;
        }
        GeometryModel geo = skin.get().geometry();
        boolean left = arm == HumanoidArm.LEFT;
        Bone itemBone = findBone(geo, left ? "leftitem" : "rightitem");
        Bone armBone = findBone(geo, left ? "leftarm" : "rightarm");
        if (armBone == null) {
            return;
        }
        float[] ap = armBone.pivot();
        float[] ip = itemBone != null
                ? itemBone.pivot()
                : new float[] {ap[0], ap[1] - 7f, ap[2]};
        float dx = ip[0] - ap[0];
        float dy = ip[1] - ap[1];
        float dz = ip[2] - ap[2];
        // Wanted post-rotate translate (pixels): (−dx, −dz, −dy), with X mirrored for left.
        float wantX = left ? dx : -dx;
        float wantY = -dz;
        float wantZ = -dy;
        float vanX = left ? -VANILLA_X : VANILLA_X;
        // Corrective added after vanilla translate.
        CORRECTIVE.set(new float[] {
                (wantX - vanX) / 16f,
                (wantY - VANILLA_Y) / 16f,
                (wantZ - VANILLA_Z) / 16f
        });
    }

    public static void end() {
        CORRECTIVE.remove();
    }

    /** After vanilla (±1, 2, −10)/16 — pull socket onto Bedrock item locator. */
    public static void afterHandTranslate(PoseStack pose) {
        float[] c = CORRECTIVE.get();
        if (c == null) {
            return;
        }
        pose.translate(c[0], c[1], c[2]);
    }

    private static Bone findBone(GeometryModel geo, String want) {
        for (Bone b : geo.bones()) {
            String n = b.name().toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
            if (n.equals(want)) {
                return b;
            }
        }
        return null;
    }
}
