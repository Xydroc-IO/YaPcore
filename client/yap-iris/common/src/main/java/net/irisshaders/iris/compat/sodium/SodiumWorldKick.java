package net.irisshaders.iris.compat.sodium;

import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;

/**
 * Opening Sodium's Video Settings screen is what finishes the world: it
 * re-reads render distance from the vanilla options and drops cached fog /
 * distance values, then the renderer rebuilds chunks and entity meshes.
 * Joining a world never did that, so view distance stayed short and skins
 * stayed wrong until that screen was opened.
 */
public final class SodiumWorldKick {
	private static int ticksUntilKick;
	private static boolean armed;
	private static boolean kicking;

	private SodiumWorldKick() {
	}

	/** Schedule the Video Settings refresh. Later calls replace the timer. */
	public static void arm(int ticks) {
		if (kicking) {
			return;
		}
		ticksUntilKick = Math.max(1, ticks);
		armed = true;
	}

	public static void onLeave() {
		if (kicking) {
			return;
		}
		armed = false;
		ticksUntilKick = 0;
	}

	public static void onClientTick(Minecraft mc) {
		if (!armed) {
			return;
		}
		if (mc.level == null || mc.levelExtractor == null) {
			armed = false;
			ticksUntilKick = 0;
			return;
		}
		if (--ticksUntilKick > 0) {
			return;
		}
		armed = false;
		if (!Iris.isPackInUseQuick()) {
			return;
		}
		kicking = true;
		try {
			ConfigManager.CONFIG.resetAllOptionsFromBindings();
			ConfigManager.CONFIG.invalidateGlobalRebuildDependents();
			mc.levelExtractor.allChanged();
		} finally {
			kicking = false;
		}
	}
}
