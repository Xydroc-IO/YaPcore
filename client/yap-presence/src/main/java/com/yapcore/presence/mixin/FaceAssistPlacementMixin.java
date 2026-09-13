package com.yapcore.presence.mixin;

import com.yapcore.presence.movement.FaceAssist;
import com.yapcore.presence.movement.MovementProfileStore;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Phase 4 / movement catalog {@code player.placement.face_assist}:
 * when the MOVEMENT profile enables face assist, snap glancing block hits
 * toward the dominant look-axis face (Bedrock-style placement assist).
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class FaceAssistPlacementMixin {

    @ModifyVariable(method = "useItemOn", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockHitResult yap$faceAssist(BlockHitResult hit, LocalPlayer player, InteractionHand hand) {
        if (hit == null || player == null || !MovementProfileStore.get().faceAssist) {
            return hit;
        }
        return FaceAssist.adjust(player, hit);
    }
}
