package com.yapcore.visuals.mixin;

import com.yapcore.visuals.HeldItemHang;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * When {@link HeldItemHang} is active, rewrite display-transform rotation
 * inside {@link ItemTransform#apply} after the grip translation.
 */
@Mixin(ItemTransform.class)
public abstract class ItemTransformMixin {

    @ModifyArgs(
            method = "apply",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Quaternionf;rotationXYZ(FFF)Lorg/joml/Quaternionf;"
            )
    )
    private void yap$hangToolRotation(Args args) {
        HeldItemHang.applyRotation(args);
    }
}
