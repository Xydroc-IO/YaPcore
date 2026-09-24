package net.irisshaders.iris.mixin;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.compat.sodium.SodiumWorldKick;
import net.irisshaders.iris.gl.shader.ShaderCompileException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {
	@Inject(method = "handleLogin", at = @At("TAIL"))
	private void iris$showUpdateMessage(ClientboundLoginPacket a, CallbackInfo ci) {
		// Every server join / proxy switch — silent VS-equivalent refresh (no kick spam).
		SodiumWorldKick.demandRefresh();
		Minecraft mc = Minecraft.getInstance();

		if (mc.player == null) {
			return;
		}

		Iris.getUpdateChecker().getUpdateMessage().ifPresent(msg ->
			mc.player.sendSystemMessage(msg));

		Iris.getStoredError().ifPresent(e ->
			mc.player.sendSystemMessage(Component.translatable(e instanceof ShaderCompileException ? "iris.load.failure.shader" : "iris.load.failure.generic").append(Component.literal("Copy Info").withStyle(arg -> arg.withUnderlined(true).withColor(ChatFormatting.BLUE).withClickEvent(new ClickEvent.CopyToClipboard(e.getMessage())).withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.copy.click")))))));

		if (Iris.loadedIncompatiblePack()) {
			mc.gui.hud.setTimes(10, 70, 140);
			Iris.logger.warn("Incompatible pack for DH!");
			mc.player.sendSystemMessage(Component.literal("This pack doesn't have DH support.").withStyle(ChatFormatting.BOLD, ChatFormatting.RED));
			mc.player.sendSystemMessage(Component.literal("Distant Horizons (DH) chunks won't show up. This isn't a bug, get another shader.").withStyle(ChatFormatting.RED));
		}
	}

	@Inject(method = "handleRespawn", at = @At("TAIL"))
	private void iris$kickAfterRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
		// Velocity lobby→survival / portals often respawn in the same dimension
		// (same ClientLevel). Re-arm silent VS finish + delayed retries.
		SodiumWorldKick.demandRefresh();
	}
}
