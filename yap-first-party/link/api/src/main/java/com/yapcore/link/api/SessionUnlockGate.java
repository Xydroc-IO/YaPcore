package com.yapcore.link.api;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Optional YaPPlayerData session-lock bridge. Registered by {@code yaplink-server-selector}.
 * Soft-switch / first-login wait for unlock, or clear stale locks when the holder is down.
 */
public interface SessionUnlockGate {

    /**
     * Non-blocking check. Clears the lock automatically when the holder backend is down
     * (crash / kill left a stale {@code lock_server}).
     *
     * @return true if no foreign lock blocks login to {@code loginServer}
     */
    boolean isReady(UUID uuid, String loginServer, Predicate<String> backendUp);

    /** Force-clear any lock (transfer timeout / operator recovery). */
    void forceClear(UUID uuid);

    final class Holder {
        private static volatile SessionUnlockGate gate;

        private Holder() {
        }

        public static void set(SessionUnlockGate g) {
            gate = g;
        }

        public static SessionUnlockGate get() {
            return gate;
        }
    }
}
