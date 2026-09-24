package net.irisshaders.iris.compat.sodium;

import net.caffeinemc.mods.sodium.api.config.option.OptionBinding;
import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.config.structure.Config;
import net.caffeinemc.mods.sodium.client.config.structure.ModOptions;
import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.caffeinemc.mods.sodium.client.config.structure.OptionGroup;
import net.caffeinemc.mods.sodium.client.config.structure.Page;
import net.caffeinemc.mods.sodium.client.config.structure.StatefulOption;
import net.caffeinemc.mods.sodium.client.config.value.DynamicValue;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.compat.sodium.mixin.SodiumWorldRendererAccessor;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.multiplayer.ClientLevel;

import java.lang.reflect.Field;
import java.util.Collection;

/**
 * Join / transfer can leave the world as a near cliff until the player opens
 * the vanilla <em>Options</em> screen (not Video Settings). Earlier kicks flashed
 * Sodium Video Settings and closed it on the same tick — no frame rendered with
 * a menu open, so the cliff never cleared. The player leaves Options open for
 * seconds (many rendered frames); that is what actually finishes the world.
 *
 * <p>This class flashes {@link OptionsScreen} via {@code Gui.setScreen}, holds it
 * for several ticks so the world renders behind the menu, then closes. Retries
 * at +2s / +5s after transfer if needed.
 */
public final class SodiumWorldKick {
	/** Frames to leave Options open so terrain can catch up behind the menu. */
	private static final int HOLD_TICKS = 10; // 0.5s
	private static final int RETRY_2S_TICKS = 40;
	private static final int RETRY_5S_TICKS = 100;

	private static Field globalRebuildDependentsField;
	private static boolean globalRebuildDependentsResolved;
	private static boolean loggedDependentsProof;

	/** True while our flash Options screen is the active screen. */
	private static boolean silentRefresh;

	private static int ageSinceDemand;
	private static int boundLevelId;
	private static boolean kicking;
	private static int transferGen;
	private static int retryMask;
	private static boolean immediatePending;
	/** Countdown while flash Options is held open; close when it hits 0. */
	private static int holdTicksRemaining;
	private static Screen restoreScreen;

	private SodiumWorldKick() {
	}

	public static boolean isSilentRefresh() {
		return silentRefresh;
	}

	public static void demandRefresh() {
		ageSinceDemand = 0;
		retryMask = 0;
		immediatePending = true;
		transferGen++;
		holdTicksRemaining = 0;
		Minecraft mc = Minecraft.getInstance();
		if (mc != null && mc.level != null) {
			boundLevelId = System.identityHashCode(mc.level);
		}
		proveGlobalRebuildDependents();
		Iris.logger.info("[SodiumWorldKick] demand gen={} (flash Options hold={}t)", transferGen, HOLD_TICKS);
		if (mc != null) {
			mc.execute(() -> runSilentOptionsFlash(mc, "immediate"));
		}
	}

	public static void arm(int ticks) {
		demandRefresh();
	}

	public static void armWithFollowUp(int ticks, int followUp) {
		demandRefresh();
	}

	public static void onLeave() {
		ageSinceDemand = 0;
		retryMask = 0;
		immediatePending = false;
		boundLevelId = 0;
		transferGen = 0;
		holdTicksRemaining = 0;
		restoreScreen = null;
		silentRefresh = false;
		kicking = false;
	}

	public static void invalidateLikeVideoSettings() {
		if (ConfigManager.CONFIG == null) {
			return;
		}
		ConfigManager.CONFIG.invalidateGlobalRebuildDependents();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void recomputeGlobalRebuildDependents() {
		Config config = ConfigManager.CONFIG;
		if (config == null) {
			return;
		}
		Collection dependents = globalRebuildDependents(config);
		if (dependents == null) {
			return;
		}
		for (Object raw : dependents) {
			if (raw instanceof DynamicValue<?> dv) {
				dv.get(config);
			}
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void forceReadAllOptionValues() {
		Config config = ConfigManager.CONFIG;
		if (config == null) {
			return;
		}
		for (ModOptions mod : config.getModOptions()) {
			for (Page page : mod.pages()) {
				for (OptionGroup group : page.groups()) {
					for (Option opt : group.options()) {
						opt.isEnabled();
						if (opt instanceof StatefulOption so) {
							so.getValidatedValue();
							OptionBinding binding = so.getBinding();
							if (binding != null) {
								binding.load();
							}
						}
					}
				}
			}
		}
	}

	private static Collection<?> globalRebuildDependents(Config config) {
		if (!globalRebuildDependentsResolved) {
			globalRebuildDependentsResolved = true;
			try {
				Field f = Config.class.getDeclaredField("globalRebuildDependents");
				f.setAccessible(true);
				globalRebuildDependentsField = f;
			} catch (ReflectiveOperationException e) {
				Iris.logger.warn("[SodiumWorldKick] cannot access globalRebuildDependents: {}", e.toString());
			}
		}
		if (globalRebuildDependentsField == null) {
			return null;
		}
		try {
			Object v = globalRebuildDependentsField.get(config);
			return v instanceof Collection<?> c ? c : null;
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	private static void proveGlobalRebuildDependents() {
		if (loggedDependentsProof) {
			return;
		}
		loggedDependentsProof = true;
		Config config = ConfigManager.CONFIG;
		if (config == null) {
			return;
		}
		Collection<?> deps = globalRebuildDependents(config);
		Iris.logger.info(
			"[SodiumWorldKick] proof: globalRebuildDependents accessible={} size={}",
			globalRebuildDependentsField != null,
			deps == null ? -1 : deps.size());
	}

	/** Light Sodium graph dirty — used after Options flash closes. */
	public static void kickGraphLikeVideoSettings(Minecraft mc, boolean clearGraph) {
		if (mc == null || mc.level == null) {
			return;
		}
		if (ConfigManager.CONFIG != null) {
			ConfigManager.CONFIG.resetAllOptionsFromBindings();
			ConfigManager.CONFIG.invalidateGlobalRebuildDependents();
			recomputeGlobalRebuildDependents();
			forceReadAllOptionValues();
		}
		if (clearGraph) {
			Config.onRendererUpdate();
		}
		if (mc.options != null) {
			mc.options.fov().get();
			mc.options.getEffectiveRenderDistance();
		}
		SodiumWorldRenderer sodium = SodiumWorldRenderer.instanceNullable();
		if (sodium == null) {
			return;
		}
		SodiumWorldRendererAccessor access = (SodiumWorldRendererAccessor) (Object) sodium;
		if (access.iris$getLevel() == null) {
			return;
		}
		access.iris$setLastFogParameters(FogParameters.NONE);
		sodium.scheduleTerrainUpdate();
	}

	public static void kickGraphLikeVideoSettings(Minecraft mc) {
		kickGraphLikeVideoSettings(mc, true);
	}

	private static boolean shadersExpectIris() {
		return Iris.getIrisConfig() != null
			&& Iris.getIrisConfig().areShadersEnabled()
			&& Iris.getCurrentPack().isPresent();
	}

	private static boolean readyForGoodReload() {
		if (shadersExpectIris()) {
			if (!(Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline)) {
				return false;
			}
			return WorldRenderingSettings.INSTANCE.getBlockStateIds() != null;
		}
		return true;
	}

	private static boolean sodiumLevelReady(Minecraft mc) {
		if (mc == null || mc.level == null || mc.levelExtractor == null) {
			return false;
		}
		SodiumWorldRenderer sodium = SodiumWorldRenderer.instanceNullable();
		if (sodium == null) {
			return false;
		}
		return ((SodiumWorldRendererAccessor) (Object) sodium).iris$getLevel() != null;
	}

	/**
	 * Flash vanilla {@link OptionsScreen} (what the player opens) and keep it
	 * open for {@link #HOLD_TICKS} so the world renders behind the menu.
	 */
	public static boolean runSilentOptionsFlash(Minecraft mc, String reason) {
		if (mc == null || mc.level == null || kicking || silentRefresh) {
			return false;
		}
		if (!sodiumLevelReady(mc)) {
			Iris.logger.info("[SodiumWorldKick] Options flash skip (sodium not ready) reason={}", reason);
			return false;
		}

		Screen cur = mc.gui.screen();
		// Player already in Options / VS / pause — do not steal focus.
		if (cur instanceof OptionsScreen || cur instanceof VideoSettingsScreen) {
			immediatePending = false;
			return false;
		}

		kicking = true;
		silentRefresh = true;
		immediatePending = false;
		restoreScreen = cur;
		try {
			// Same ctor Options uses in-world (inWorld=true builds FOV widget etc.).
			OptionsScreen options = new OptionsScreen(restoreScreen, mc.options, true);
			mc.gui.setScreen(options);
			holdTicksRemaining = HOLD_TICKS;
			Iris.logger.info("[SodiumWorldKick] flash Options open gen={} hold={} reason={}",
				transferGen, HOLD_TICKS, reason);
			return true;
		} catch (Throwable t) {
			silentRefresh = false;
			kicking = false;
			holdTicksRemaining = 0;
			restoreScreen = null;
			Iris.logger.warn("[SodiumWorldKick] flash Options failed reason={}: {}", reason, t.toString());
			return false;
		}
	}

	/** @deprecated use {@link #runSilentOptionsFlash} */
	public static boolean runSilentVsFinish(Minecraft mc, String reason) {
		return runSilentOptionsFlash(mc, reason);
	}

	private static void closeFlashIfHeld(Minecraft mc) {
		if (holdTicksRemaining <= 0) {
			return;
		}
		holdTicksRemaining--;
		if (holdTicksRemaining > 0) {
			return;
		}
		try {
			if (mc.gui.screen() instanceof OptionsScreen && silentRefresh) {
				Screen restore = restoreScreen;
				mc.gui.setScreen(restore);
				// Options.removed() already ran via setScreen and saved options.
				kickGraphLikeVideoSettings(mc, true);
				if (readyForGoodReload()) {
					SodiumWorldRenderer sodium = SodiumWorldRenderer.instanceNullable();
					if (sodium != null) {
						SodiumWorldRendererAccessor access = (SodiumWorldRendererAccessor) (Object) sodium;
						if (access.iris$getLevel() != null) {
							access.iris$setRenderDistance(-1);
							Config.onRendererReload();
							sodium.reload();
							access.iris$setLastFogParameters(FogParameters.NONE);
							sodium.scheduleTerrainUpdate();
						}
					}
				}
				Iris.logger.info("[SodiumWorldKick] flash Options closed gen={}", transferGen);
			}
		} finally {
			silentRefresh = false;
			kicking = false;
			restoreScreen = null;
			holdTicksRemaining = 0;
		}
	}

	public static boolean kickNow(Minecraft mc) {
		if (mc == null || mc.level == null) {
			return false;
		}
		kickGraphLikeVideoSettings(mc, true);
		if (!sodiumLevelReady(mc) || !readyForGoodReload()) {
			return false;
		}
		kicking = true;
		try {
			SodiumWorldRenderer sodium = SodiumWorldRenderer.instanceNullable();
			SodiumWorldRendererAccessor access = (SodiumWorldRendererAccessor) (Object) sodium;
			access.iris$setRenderDistance(-1);
			Config.onRendererUpdate();
			Config.onRendererReload();
			sodium.reload();
			access.iris$setLastFogParameters(FogParameters.NONE);
			sodium.scheduleTerrainUpdate();
			return true;
		} finally {
			kicking = false;
		}
	}

	public static void onClientTick(Minecraft mc) {
		if (mc.level == null) {
			if (transferGen != 0 || boundLevelId != 0) {
				onLeave();
			}
			return;
		}

		int levelId = System.identityHashCode(mc.level);
		if (levelId != boundLevelId) {
			boundLevelId = levelId;
			demandRefresh();
			return;
		}

		// Finish a held Options flash first.
		if (holdTicksRemaining > 0) {
			closeFlashIfHeld(mc);
			ageSinceDemand++;
			return;
		}

		if (transferGen == 0) {
			return;
		}

		if ((retryMask & 0b11) == 0b11 && !immediatePending) {
			transferGen = 0;
			ageSinceDemand = 0;
			return;
		}

		ageSinceDemand++;

		if (immediatePending && !kicking) {
			runSilentOptionsFlash(mc, "tick-immediate");
		}

		if ((retryMask & 0b01) == 0 && ageSinceDemand >= RETRY_2S_TICKS) {
			retryMask |= 0b01;
			if (!kicking) {
				runSilentOptionsFlash(mc, "+2s");
			}
		}
		if ((retryMask & 0b10) == 0 && ageSinceDemand >= RETRY_5S_TICKS) {
			retryMask |= 0b10;
			if (!kicking) {
				runSilentOptionsFlash(mc, "+5s");
			}
		}
	}

	public static void onLevelAttached(ClientLevel level) {
		if (level == null) {
			onLeave();
			return;
		}
		demandRefresh();
	}
}
