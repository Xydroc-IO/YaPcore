package net.irisshaders.iris.mixin;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.compat.sodium.SodiumWorldKick;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft_PipelineManagement {
	/**
	 * Should run before the Minecraft.level field is updated after disconnecting from a server or leaving a singleplayer world
	 */
	@Inject(method = "clearClientLevel", at = @At("HEAD"))
	public void iris$trackLastDimensionOnLeave(Screen arg, CallbackInfo ci) {
		Iris.lastDimension = Iris.getCurrentDimension();
		SodiumWorldKick.onLeave();
	}

	/**
	 * Should run before the Minecraft.level field is updated after receiving a login or respawn packet
	 * NB: Not on leave, another inject is used for that
	 */
	@Inject(method = "setLevel", at = @At("HEAD"))
	private void iris$trackLastDimensionOnLevelChange(ClientLevel clientLevel, CallbackInfo ci) {
		Iris.lastDimension = Iris.getCurrentDimension();
	}

	@Inject(method = "setLevel", at = @At("RETURN"))
	private void iris$kickAfterSetLevel(ClientLevel clientLevel, CallbackInfo ci) {
		// Covers portals / world switches that attach a level without a full Sodium setLevel race.
		SodiumWorldKick.onLevelAttached(clientLevel);
	}

	/**
	 * Injects before LevelExtractor / GameRenderer receive the new level.
	 * <p>
	 * Always destroy and re-prepare the Iris pipeline when a level attaches or
	 * clears. Same-dimension joins used to reuse the title-screen pipeline
	 * (often Vanilla while the pack was still loading, or an Iris pipeline whose
	 * {@code initializedBlockIds} had already fired), so Sodium's
	 * {@code initRenderer} on {@code LevelExtractor.setLevel} built meshes
	 * against the wrong vertex format / missing block-ID maps. Opening Video
	 * Settings later forced a Sodium {@code reload()} once Iris was ready —
	 * join never did. Destroying here also bumps
	 * {@code versionCounterForSodiumShaderReload}.
	 * <p>
	 * This must run before Sodium's {@code LevelExtractor.setLevel} RETURN
	 * hook calls {@code SodiumWorldRenderer.setLevel} / {@code initRenderer}.
	 * <p>
	 * See: <a href="https://github.com/IrisShaders/Iris/issues/1330">Issue 1330</a>
	 */
	@Inject(method = "updateLevelInEngines", at = @At("HEAD"))
	private void iris$resetPipeline(@Nullable ClientLevel level, CallbackInfo ci) {
		Iris.logger.info("Reloading pipeline on level {}: {} => {}",
			level == null ? "unload" : "attach",
			Iris.lastDimension, Iris.getCurrentDimension());
		Iris.getPipelineManager().destroyPipeline();

		// NB: Create the pipeline immediately so it is ready by the time Sodium
		// initializes its world renderer (LevelExtractor.setLevel RETURN).
		if (level != null) {
			Iris.getPipelineManager().preparePipeline(Iris.getCurrentDimension());
			SodiumWorldKick.demandRefresh();
		}
	}
}
