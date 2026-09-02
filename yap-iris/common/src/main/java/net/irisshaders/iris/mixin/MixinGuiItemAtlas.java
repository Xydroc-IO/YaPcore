package net.irisshaders.iris.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTexture;
import net.irisshaders.iris.vertices.ImmediateState;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiItemAtlas.class)
public class MixinGuiItemAtlas {
	@WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearColorAndDepthTextures(Lcom/mojang/blaze3d/textures/GpuTexture;Lorg/joml/Vector4fc;Lcom/mojang/blaze3d/textures/GpuTexture;D)V"))
	private void iris$endInit(CommandEncoder instance, GpuTexture colorTexture, Vector4fc clearColor, GpuTexture depthTexture, double clearDepth, Operation<Void> original) {
		ImmediateState.ALWAYS_REVERSE = true;
		original.call(instance, colorTexture, clearColor, depthTexture, clearDepth);
		ImmediateState.ALWAYS_REVERSE = false;
	}

	@WrapMethod(method = "drawToSlot")
	private void iris$beginDraw(int slotX, int slotY, boolean clear, ItemStackRenderState item, Operation<Void> original) {
		ImmediateState.ALWAYS_REVERSE = true;
		original.call(slotX, slotY, clear, item);
		ImmediateState.ALWAYS_REVERSE = false;
	}
}
