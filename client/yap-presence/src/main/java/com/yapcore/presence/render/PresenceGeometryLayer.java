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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Feature/layer that draws Bedrock-ported cubes for a player when {@link PresenceSkin} exists.
 * Applies Phase 2 emote bone rotations from converted {@code yap.emote/1} clips.
 *
 * <p>Persona attachments often live on bones that are not vanilla parts (hair, cape bones,
 * accessories). Those follow the parent chain until a mapped part is found.
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
        Map<String, Bone> byName = indexBones(geo);

        for (Bone bone : geo.bones()) {
            if (bone.cubes().isEmpty()) {
                continue;
            }
            poseStack.pushPose();
            applyBoneChain(poseStack, model, bone, byName);
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

    private static Map<String, Bone> indexBones(GeometryModel geo) {
        Map<String, Bone> map = new HashMap<>();
        for (Bone bone : geo.bones()) {
            map.put(bone.name().toLowerCase(Locale.ROOT), bone);
        }
        return map;
    }

    /**
     * Walk parent chain (root → leaf). The first bone that maps to a vanilla {@link ModelPart}
     * takes that part's transform; further mapped bones that resolve to the <em>same</em> part
     * are skipped (avoid double-applying body/head). Unmapped bones use Bedrock pivot+rotation.
     */
    private static void applyBoneChain(
            PoseStack poseStack, PlayerModel model, Bone bone, Map<String, Bone> byName) {
        ListPath path = new ListPath();
        Bone cur = bone;
        int guard = 0;
        while (cur != null && guard++ < 32) {
            path.prepend(cur);
            if (cur.parent() == null || cur.parent().isBlank()) {
                break;
            }
            cur = byName.get(cur.parent().toLowerCase(Locale.ROOT));
        }
        ModelPart lastMapped = null;
        for (Bone b : path.bones) {
            ModelPart part = mapBone(model, b.name());
            if (part != null) {
                if (part != lastMapped) {
                    part.translateAndRotate(poseStack);
                    lastMapped = part;
                }
                continue;
            }
            float[] pivot = b.pivot();
            poseStack.translate(pivot[0] / 16f, pivot[1] / 16f, pivot[2] / 16f);
            float[] rot = b.rotation();
            if (rot[0] != 0f || rot[1] != 0f || rot[2] != 0f) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(rot[2]));
                poseStack.mulPose(Axis.YP.rotationDegrees(rot[1]));
                poseStack.mulPose(Axis.XP.rotationDegrees(rot[0]));
            }
        }
    }

    private static final class ListPath {
        final java.util.ArrayList<Bone> bones = new java.util.ArrayList<>();

        void prepend(Bone b) {
            bones.add(0, b);
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
        String n = boneName.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
        // Strip common persona prefixes
        if (n.startsWith("persona")) {
            n = n.substring("persona".length());
        }
        return switch (n) {
            case "head", "hat", "helmet", "hair", "face", "cape", "leftface", "rightface" -> model.head;
            case "body", "waist", "torso", "jacket", "chest", "hips", "belt", "root", "hip" -> model.body;
            case "leftarm", "armleft", "leftsleeve", "leftitem" -> model.leftArm;
            case "rightarm", "armright", "rightsleeve", "rightitem" -> model.rightArm;
            case "leftleg", "legleft", "leftpants", "leftboot" -> model.leftLeg;
            case "rightleg", "legright", "rightpants", "rightboot" -> model.rightLeg;
            default -> {
                if (n.contains("hair") || n.contains("hat") || n.contains("ear") || n.contains("face")) {
                    yield model.head;
                }
                if (n.contains("body") || n.contains("torso") || n.contains("jacket") || n.contains("shirt")) {
                    yield model.body;
                }
                yield null;
            }
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
