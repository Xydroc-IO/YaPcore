package com.yapcore.visuals;

import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Third-person tool/weapon hang: tip beside the leg, grip still in the hand.
 * Used by {@code ItemInHandLayerMixin} + {@code ItemTransformMixin}.
 */
public final class HeldItemHang {
    /** Tip down beside the leg. */
    private static final float PITCH_RAD = (float) Math.toRadians(-30.0);
    /** Soft clearance from the body; sign follows vanilla left/right roll. */
    private static final float ROLL_RAD = (float) Math.toRadians(20.0);

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

    public static void applyRotation(Args args) {
        if (!isActive()) {
            return;
        }
        float z = args.get(2);
        args.set(0, PITCH_RAD);
        args.set(2, Math.copySign(ROLL_RAD, z == 0.0f ? 1.0f : z));
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
