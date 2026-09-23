package com.yapcore.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * Third-person tool/weapon hold. Vanilla display leaves the blade as a sideways
 * wing; rewriting {@code ItemTransform} rotation breaks the grip (hand ends up
 * on the blade). Instead we keep vanilla display transforms and, after they
 * apply, pitch around the hand socket so the tip drops beside the leg.
 */
public final class HeldItemHang {
    /**
     * Post-display pitch around the hand. Negative tips the vanilla wing down
     * beside the hip; positive was tip-into-shoulder / blade-in-hand.
     */
    private static final float HAND_PITCH_DEGREES = -70.0f;

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private HeldItemHang() {}

    public static void begin(ArmedEntityRenderState state, ItemStack stack, HumanoidArm arm) {
        ACTIVE.set(shouldHang(state, stack, arm));
    }

    public static void end() {
        ACTIVE.set(Boolean.FALSE);
    }

    public static boolean isActive() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }

    /** Call after vanilla {@code ItemTransform.apply} so the grip stays in the hand. */
    public static void applyPoseHang(PoseStack.Pose pose) {
        if (!isActive() || pose == null) {
            return;
        }
        pose.rotate(Axis.XP.rotationDegrees(HAND_PITCH_DEGREES));
    }

    private static boolean shouldHang(ArmedEntityRenderState state, ItemStack stack, HumanoidArm arm) {
        if (stack == null || stack.isEmpty() || !isToolOrWeapon(stack)) {
            return false;
        }
        if (state != null && arm != null && state.ticksUsingItem(arm) > 0.0f) {
            return false;
        }
        return true;
    }

    private static boolean isToolOrWeapon(ItemStack stack) {
        return stack.has(DataComponents.WEAPON)
                || stack.has(DataComponents.TOOL)
                || stack.has(DataComponents.PIERCING_WEAPON)
                || stack.has(DataComponents.KINETIC_WEAPON);
    }
}
