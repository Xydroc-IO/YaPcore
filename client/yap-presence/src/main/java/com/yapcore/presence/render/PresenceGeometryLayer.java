package com.yapcore.presence.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yapcore.presence.PresenceSkin;
import com.yapcore.presence.PresenceSkinStore;
import com.yapcore.presence.PresenceTextureCache;
import com.yapcore.presence.emote.PresenceEmoteStore;
import com.yapcore.presence.geo.GeometryModel;
import com.yapcore.presence.geo.GeometryModel.Bone;
import com.yapcore.presence.geo.GeometryModel.Cube;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Feature/layer that draws Bedrock-ported cubes for a player when {@link PresenceSkin} exists.
 * Applies Phase 2 emote bone rotations from converted {@code yap.emote/1} clips.
 */
public final class PresenceGeometryLayer
        extends RenderLayer<AvatarRenderState, PlayerModel> {

    public PresenceGeometryLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int lightCoords,
            AvatarRenderState state,
            float limbSwing,
            float limbSwingAmount) {
        if (state == null || state.isInvisible) {
            return;
        }
        Optional<UUID> uuidOpt = resolveUuid(state);
        if (uuidOpt.isEmpty()) {
            return;
        }
        UUID uuid = uuidOpt.get();
        Optional<PresenceSkin> skinOpt = PresenceSkinStore.get(uuid);
        if (skinOpt.isEmpty() || !skinOpt.get().hasRenderableGeometry()) {
            return;
        }
        PresenceSkin skin = skinOpt.get();
        PresenceTextureCache.ensureDownloaded(uuid, skin.skinPngUrl());
        Identifier tex = PresenceTextureCache.getIfReady(uuid);
        if (tex == null) {
            if (state.skin != null && state.skin.body() != null) {
                tex = state.skin.body().texturePath();
            } else {
                return;
            }
        }

        GeometryModel geo = skin.geometry();
        PlayerModel model = getParentModel();
        int overlay = OverlayTexture.NO_OVERLAY;
        Identifier texture = tex;
        Optional<PresenceEmoteStore.Active> emote = PresenceEmoteStore.get(uuid);

        for (Bone bone : geo.bones()) {
            if (bone.cubes().isEmpty()) {
                continue;
            }
            poseStack.pushPose();
            ModelPart part = mapBone(model, bone.name());
            if (part != null) {
                part.translateAndRotate(poseStack);
            } else {
                float[] pivot = bone.pivot();
                poseStack.translate(pivot[0] / 16f, pivot[1] / 16f, pivot[2] / 16f);
            }
            emote.ifPresent(a -> applyEmoteRotation(poseStack, a, bone.name()));
            float[] bonePivot = bone.pivot();
            collector.submitCustomGeometry(
                    poseStack,
                    RenderTypes.entityCutout(texture),
                    (pose, buffer) -> {
                        for (Cube cube : bone.cubes()) {
                            CubeMesh.drawCube(
                                    pose, buffer, cube,
                                    geo.textureWidth(), geo.textureHeight(),
                                    lightCoords, overlay, bonePivot);
                        }
                    });
            poseStack.popPose();
        }
    }

    static void applyEmoteRotation(PoseStack poseStack, PresenceEmoteStore.Active emote, String boneName) {
        if (poseStack == null || emote == null || boneName == null) {
            return;
        }
        float[] rot = emote.rotation(boneName);
        if (isZero(rot)) {
            return;
        }
        poseStack.mulPose(Axis.ZP.rotationDegrees(rot[2]));
        poseStack.mulPose(Axis.YP.rotationDegrees(rot[1]));
        poseStack.mulPose(Axis.XP.rotationDegrees(rot[0]));
    }

    private static boolean isZero(float[] rot) {
        return rot == null || (rot[0] == 0f && rot[1] == 0f && rot[2] == 0f);
    }

    private static ModelPart mapBone(PlayerModel model, String boneName) {
        if (boneName == null || boneName.isBlank() || model == null) {
            return null;
        }
        String n = boneName.toLowerCase(Locale.ROOT).replace("_", "");
        return switch (n) {
            case "head", "hat" -> model.head;
            case "body", "waist", "torso", "jacket" -> model.body;
            case "leftarm", "armleft" -> model.leftArm;
            case "rightarm", "armright" -> model.rightArm;
            case "leftleg", "legleft" -> model.leftLeg;
            case "rightleg", "legright" -> model.rightLeg;
            default -> null;
        };
    }

    public static Optional<UUID> resolveUuid(AvatarRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return Optional.empty();
        }
        Entity entity = mc.level.getEntity(state.id);
        if (entity instanceof Player player) {
            return Optional.of(player.getUUID());
        }
        if (mc.player != null && mc.player.getId() == state.id) {
            return Optional.of(mc.player.getUUID());
        }
        return Optional.empty();
    }
}
