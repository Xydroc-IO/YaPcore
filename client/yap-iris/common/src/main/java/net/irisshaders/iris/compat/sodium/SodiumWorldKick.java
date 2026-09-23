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
	private static int followUpTicks;
	private static boolean armed;
	private static boolean kicking;

	private SodiumWorldKick() {
	}

	/**
	 * Schedule the Video Settings refresh. Keeps the soonest pending deadline
	 * when already armed (join kick + later skin-ready kick coexist).
	 */
	public static void arm(int ticks) {
		if (kicking) {
			return;
		}
		int next = Math.max(1, ticks);
		if (armed) {
			ticksUntilKick = Math.min(ticksUntilKick, next);
		} else {
			ticksUntilKick = next;
			armed = true;
		}
	}

	/** After the next kick fires, schedule another one {@code ticks} later. */
	public static void armWithFollowUp(int ticks, int followUp) {
		arm(ticks);
		followUpTicks = Math.max(followUpTicks, Math.max(1, followUp));
	}

	public static void onLeave() {
		if (kicking) {
			return;
		}
		armed = false;
		ticksUntilKick = 0;
		followUpTicks = 0;
	}

	public static void onClientTick(Minecraft mc) {
		if (!armed) {
			return;
		}
		if (mc.level == null || mc.levelExtractor == null) {
			armed = false;
			ticksUntilKick = 0;
			followUpTicks = 0;
			return;
		}
		if (--ticksUntilKick > 0) {
			return;
		}
		armed = false;
		int nextFollow = followUpTicks;
		followUpTicks = 0;
		kicking = true;
		try {
			// Shader packs need Sodium option rebind; skins/meshes need allChanged either way.
			if (Iris.isPackInUseQuick()) {
				ConfigManager.CONFIG.resetAllOptionsFromBindings();
				ConfigManager.CONFIG.invalidateGlobalRebuildDependents();
			}
			mc.levelExtractor.allChanged();
		} finally {
			kicking = false;
		}
		if (nextFollow > 0) {
			arm(nextFollow);
		}
	}
}
