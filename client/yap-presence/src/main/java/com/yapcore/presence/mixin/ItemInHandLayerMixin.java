package com.yapcore.presence.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Held-item Bedrock remap removed — Presence uses the Java player model for avatars
 * ({@link com.yapcore.presence.render.PresenceGeometryLayer#RENDER_BEDROCK_GEOMETRY} is off),
 * so stock {@link ItemInHandLayer} attach is correct. Mixin kept registered so older
 * clients do not break on a missing class if the json still lists it.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    // Intentionally empty while Bedrock geo rendering is disabled.
}
