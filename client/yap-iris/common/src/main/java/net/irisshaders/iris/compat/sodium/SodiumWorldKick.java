package net.irisshaders.iris.compat.sodium;

import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.minecraft.client.Minecraft;

/**
 * Opening Sodium's Video Settings screen finishes the world: it re-reads options
 * from vanilla bindings ({@code resetAllOptionsFromBindings} in the constructor),
 * drops cached fog / distance {@code DynamicValue}s via
 * {@code invalidateGlobalRebuildDependents()} in {@code init()}, then the
 * renderer rebuilds. Joining a world never did that, so view distance stayed
 * short and skins / terrain stayed wrong until that screen was opened.
 * <p>
 * This kick mirrors that Config path on a client tick (never mid-
 * {@code LevelRenderer.render}). Stock Video Settings does <em>not</em> call
 * {@code allChanged()} on open — Iris adds that so chunk meshes pick up the
 * shader vertex format / block-ID maps.
 */
public final class SodiumWorldKick {
	private static int ticksUntilKick;
	private static int followUpTicks;
	private static int pendingArmTicks = -1;
	private static int pendingFollowUpTicks;
	private static boolean armed;
	private static boolean kicking;

	private SodiumWorldKick() {
	}

	/**
	 * Schedule the Video Settings refresh. Keeps the soonest pending deadline
	 * when already armed (join kick + later skin-ready kick coexist).
	 * Arms requested during {@link #kickNow} are queued and applied after the
	 * kick finishes — never dropped (that used to swallow block-ID rebuilds).
	 */
	public static void arm(int ticks) {
		int next = Math.max(1, ticks);
		if (kicking) {
			pendingArmTicks = pendingArmTicks < 0 ? next : Math.min(pendingArmTicks, next);
			return;
		}
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
		int nextFollow = Math.max(1, followUp);
		if (kicking) {
			pendingFollowUpTicks = Math.max(pendingFollowUpTicks, nextFollow);
		} else {
			followUpTicks = Math.max(followUpTicks, nextFollow);
		}
	}

	public static void onLeave() {
		if (kicking) {
			// Still clear intent so we do not re-arm into a dead world after kickNow.
			pendingArmTicks = -1;
			pendingFollowUpTicks = 0;
			return;
		}
		armed = false;
		ticksUntilKick = 0;
		followUpTicks = 0;
		pendingArmTicks = -1;
		pendingFollowUpTicks = 0;
	}

	/**
	 * Same work as Sodium Video Settings open: {@code resetAllOptionsFromBindings}
	 * (constructor) then {@code invalidateGlobalRebuildDependents} ({@code init()}),
	 * plus {@code allChanged()} so Iris chunk meshes rebuild. Safe only on the
	 * client tick / execute queue — never call from {@code LevelRenderer.render}.
	 */
	public static void kickNow(Minecraft mc) {
		if (mc == null || mc.level == null || mc.levelExtractor == null) {
			return;
		}
		kicking = true;
		try {
			// Match Video Settings: reset bindings first, then invalidate globals.
			// Invalidate-before-reset left fog/distance DynamicValues stale when
			// reset changed binding values without touching globalRebuildDependents.
			ConfigManager.CONFIG.resetAllOptionsFromBindings();
			ConfigManager.CONFIG.invalidateGlobalRebuildDependents();
			mc.levelExtractor.allChanged();
		} finally {
			kicking = false;
			flushPendingArms();
		}
	}

	private static void flushPendingArms() {
		int pending = pendingArmTicks;
		int pendingFollow = pendingFollowUpTicks;
		pendingArmTicks = -1;
		pendingFollowUpTicks = 0;
		if (pending >= 0) {
			arm(pending);
		}
		if (pendingFollow > 0) {
			followUpTicks = Math.max(followUpTicks, pendingFollow);
		}
	}

	public static void onClientTick(Minecraft mc) {
		if (!armed) {
			return;
		}
		// Left the world — cancel.
		if (mc.level == null) {
			armed = false;
			ticksUntilKick = 0;
			followUpTicks = 0;
			pendingArmTicks = -1;
			pendingFollowUpTicks = 0;
			return;
		}
		// levelExtractor can lag a tick behind level attach; wait, don't disarm.
		if (mc.levelExtractor == null) {
			return;
		}
		if (--ticksUntilKick > 0) {
			return;
		}
		armed = false;
		int nextFollow = followUpTicks;
		followUpTicks = 0;
		kickNow(mc);
		if (nextFollow > 0) {
			arm(nextFollow);
		}
	}
}
