package com.yapcore.game;

import com.yapcore.paper.PaperCommandBridge;

import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Authority-aware console / BE command dispatch into the live game.
 * <ul>
 *   <li>Same-JVM Bukkit (Paper Phase 3, future same-JVM Folia) via
 *       {@link PaperCommandBridge} + Folia {@code GlobalRegionScheduler} when present</li>
 *   <li>Managed Folia/Paper process via stdin ({@link #setProcessDispatch})</li>
 * </ul>
 */
public final class GameCommandBridge {

    private static final Logger LOG = Logger.getLogger("YaPcore.GameCmd");

    private static volatile Function<String, String> processDispatch;

    private GameCommandBridge() {
    }

    /**
     * Register managed-process stdin dispatch (FoliaKernel / PaperKernel).
     * Cleared on stop.
     */
    public static void setProcessDispatch(Function<String, String> dispatch) {
        processDispatch = dispatch;
        if (dispatch != null) {
            LOG.info("Game command bridge: managed process stdin armed");
        }
    }

    public static void clearProcessDispatch() {
        processDispatch = null;
    }

    /**
     * Dispatch {@code line} (leading {@code /} optional) into the game.
     *
     * @param preferredLoader optional same-JVM game classloader (may be null)
     */
    public static String dispatch(String line, ClassLoader preferredLoader) {
        if (line == null || line.isBlank()) {
            return "";
        }
        // Prefer same-JVM Bukkit when live (Phase 3 Paper / same-JVM Folia).
        String sameJvm = PaperCommandBridge.dispatchToPaper(line, preferredLoader);
        if (sameJvm != null
                && !sameJvm.startsWith("Paper not ready")
                && !sameJvm.startsWith("Could not reach Paper")) {
            return rewriteAuthorityLabel(sameJvm);
        }
        Function<String, String> proc = processDispatch;
        if (proc != null) {
            return proc.apply(line);
        }
        return sameJvm != null ? rewriteAuthorityLabel(sameJvm) : "Game not ready — cannot run: " + line.trim();
    }

    /** Convenience: no preferred loader. */
    public static String dispatch(String line) {
        return dispatch(line, null);
    }

    private static String rewriteAuthorityLabel(String msg) {
        if (msg == null) {
            return "";
        }
        // Keep legacy "Paper:" prefix for benches; Folia GlobalRegion path still says Paper today.
        return msg;
    }
}
