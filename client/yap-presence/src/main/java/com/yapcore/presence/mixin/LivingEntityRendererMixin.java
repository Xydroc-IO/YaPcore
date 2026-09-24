package com.yapcore.presence.mixin;

import com.yapcore.presence.PresenceSkinStore;
import com.yapcore.presence.emote.PresenceEmoteStore;
import com.yapcore.presence.render.PresenceGeometryLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * When a {@link com.yapcore.presence.PresenceSkin} is active, hide default PlayerModel parts
 * so custom geometry does not double-render. Also applies emote bone rotations to the vanilla
 * model when no custom geo is present.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<
        T extends net.minecraft.world.entity.LivingEntity,
        S extends LivingEntityRenderState,
        M extends EntityModel<? super S>> {

    @Shadow
    protected M model;

    @Unique
    private final ThreadLocal<Map<ModelPart, Boolean>> yap$savedVisibility = new ThreadLocal<>();

    @Unique
    private final ThreadLocal<Map<ModelPart, float[]>> yap$savedRots = new ThreadLocal<>();

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V",
                    shift = At.Shift.AFTER))
    private void yap$hideDefaultWhenPresence(
            S state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState camera,
            CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState avatar) || !(model instanceof PlayerModel playerModel)) {
            return;
        }
        Optional<UUID> uuid = PresenceGeometryLayer.resolveUuid(avatar);
        if (uuid.isEmpty()) {
            return;
        }
        boolean hasGeo = PresenceSkinStore.has(uuid.get())
                && PresenceSkinStore.get(uuid.get())
                        .map(PresenceGeometryLayer::shouldReplaceJavaModel)
                        .orElse(false);
        Optional<PresenceEmoteStore.Active> emote = PresenceEmoteStore.get(uuid.get());

        if (emote.isPresent() && !hasGeo) {
            Map<ModelPart, float[]> saved = new HashMap<>();
            // Apply hip lean onto body first, then body (if any) — wave/clap use hip heavily.
            applyPart(playerModel.body, "hip", emote.get(), saved);
            applyPart(playerModel.body, "body", emote.get(), saved);
            applyPart(playerModel.rightArm, "rightArm", emote.get(), saved);
            applyPart(playerModel.leftArm, "leftArm", emote.get(), saved);
            applyPart(playerModel.rightLeg, "rightLeg", emote.get(), saved);
            applyPart(playerModel.leftLeg, "leftLeg", emote.get(), saved);
            applyPart(playerModel.head, "head", emote.get(), saved);
            yap$savedRots.set(saved);
        }

        if (!hasGeo) {
            return;
        }
        Map<ModelPart, Boolean> saved = new HashMap<>();
        for (ModelPart part : model.allParts()) {
            saved.put(part, part.visible);
            part.visible = false;
        }
        yap$savedVisibility.set(saved);
    }

    @Unique
    private static void applyPart(
            ModelPart part, String bone, PresenceEmoteStore.Active emote, Map<ModelPart, float[]> saved) {
        if (part == null) {
            return;
        }
        float[] rot = emote.rotation(bone);
        if (rot[0] == 0f && rot[1] == 0f && rot[2] == 0f) {
            return;
        }
        saved.putIfAbsent(part, new float[] {part.xRot, part.yRot, part.zRot});
        part.xRot += (float) Math.toRadians(rot[0]);
        part.yRot += (float) Math.toRadians(rot[1]);
        part.zRot += (float) Math.toRadians(rot[2]);
    }

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("RETURN"))
    private void yap$restoreVisibility(
            S state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState camera,
            CallbackInfo ci) {
        Map<ModelPart, Boolean> saved = yap$savedVisibility.get();
        if (saved != null) {
            for (Map.Entry<ModelPart, Boolean> e : saved.entrySet()) {
                e.getKey().visible = e.getValue();
            }
            yap$savedVisibility.remove();
        }
        Map<ModelPart, float[]> rots = yap$savedRots.get();
        if (rots != null) {
            for (Map.Entry<ModelPart, float[]> e : rots.entrySet()) {
                float[] r = e.getValue();
                e.getKey().xRot = r[0];
                e.getKey().yRot = r[1];
                e.getKey().zRot = r[2];
            }
            yap$savedRots.remove();
        }
    }
}
