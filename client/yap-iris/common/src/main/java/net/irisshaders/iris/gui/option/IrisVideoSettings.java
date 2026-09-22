package net.irisshaders.iris.gui.option;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pathways.colorspace.ColorSpace;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.OptionalInt;

public class IrisVideoSettings {
	private static final Tooltip DISABLED_TOOLTIP = Tooltip.create(Component.translatable("options.iris.shadowDistance.disabled"));
	private static final Tooltip ENABLED_TOOLTIP = Tooltip.create(Component.translatable("options.iris.shadowDistance.enabled"));
	public static int shadowDistance = 32;
	public static ColorSpace colorSpace = ColorSpace.SRGB;

	/** Cached pack-forced shadow chunks; invalidated when the active pipeline identity changes. */
	private static WorldRenderingPipeline cachedPipeline;
	private static OptionalInt cachedForcedChunks = OptionalInt.empty();

	/**
	 * Uses the stored slider value at construction — not a live pipeline override — so class
	 * load / Video Options open does not query the Iris pipeline on the main thread.
	 */
	public static final OptionInstance<Integer> RENDER_DISTANCE = new ShadowDistanceOption<>("options.iris.shadowDistance",
		mc -> isShadowDistanceSliderEnabled() ? ENABLED_TOOLTIP : DISABLED_TOOLTIP,
		(arg, d) -> {
			d = getOverriddenShadowDistance(d);
			if (d <= 0.0) {
				return Component.translatable("options.generic_value", Component.translatable("options.iris.shadowDistance"), "0 (disabled)");
			} else {
				return Component.translatable("options.generic_value",
					Component.translatable("options.iris.shadowDistance"),
					Component.translatable("options.chunks", d));
			}
		},
		new OptionInstance.IntRange(0, 32),
		shadowDistance,
		integer -> {
			shadowDistance = integer;
			try {
				Iris.getIrisConfig().save();
			} catch (IOException e) {
				Iris.logger.fatal("Failed to save config!", e);
			}
		});

	private static void refreshCache() {
		WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
		if (pipeline == cachedPipeline) {
			return;
		}
		cachedPipeline = pipeline;
		cachedForcedChunks = pipeline == null
			? OptionalInt.empty()
			: pipeline.getForcedShadowRenderDistanceChunksForDisplay();
	}

	public static int getOverriddenShadowDistance(int base) {
		refreshCache();
		return cachedForcedChunks.orElse(base);
	}

	public static boolean isShadowDistanceSliderEnabled() {
		refreshCache();
		return cachedForcedChunks.isEmpty();
	}

	/** Drop cached pack overrides after a pipeline swap/reload. */
	public static void invalidateShadowDistanceCache() {
		cachedPipeline = null;
		cachedForcedChunks = OptionalInt.empty();
	}
}
