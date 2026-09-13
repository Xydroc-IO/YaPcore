package com.yapcore.presence.mixin;

import com.yapcore.presence.movement.MovementProfile;
import com.yapcore.presence.movement.MovementProfileStore;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client prediction aligned to Bedrock catalog movement constants
 * ({@code movement.v1.json} via {@code yap:presence} MOVEMENT).
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMovementMixin {

    // jumpFromGround is declared on LivingEntity; Mixin will not resolve it on LocalPlayer.
    // Hook the invokevirtual call site inside LocalPlayer.aiStep instead.
    @Inject(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;jumpFromGround()V",
                    shift = At.Shift.AFTER))
    private void yap$catalogJumpImpulse(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        MovementProfile p = MovementProfileStore.get();
        Vec3 v = self.getDeltaMovement();
        self.setDeltaMovement(v.x, p.jumpImpulse, v.z);
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void yap$catalogGravityDragSprintSneak(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (self.getAbilities().flying || self.isPassenger()) {
            return;
        }
        MovementProfile p = MovementProfileStore.get();
        Vec3 v = self.getDeltaMovement();
        double x = v.x;
        double y = v.y;
        double z = v.z;

        // Air drag (Bedrock player.movement.drag) on horizontal when airborne.
        if (!self.onGround()) {
            double keep = 1.0 - p.drag;
            x *= keep;
            z *= keep;
            // Gravity: JE already applies ~0.08; pin toward catalog when close.
            y -= (p.gravity - 0.08);
        }

        // Sprint / sneak horizontal ratios relative to JE defaults (1.3 / 0.3).
        // Server attrs set base speed; refine client predict when ratios differ from JE.
        if (self.isSprinting() && !self.isCrouching() && Math.abs(p.sprintMultiplier - 1.3) > 1e-6) {
            double scale = p.sprintMultiplier / 1.3;
            x *= scale;
            z *= scale;
        } else if (self.isCrouching() && Math.abs(p.sneakMultiplier - 0.3) > 1e-6) {
            double scale = p.sneakMultiplier / 0.3;
            x *= scale;
            z *= scale;
        }

        self.setDeltaMovement(x, y, z);
    }
}
