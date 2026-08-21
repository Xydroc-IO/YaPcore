package com.yapcore.api.threading;

import com.yapcore.api.Pool;

/**
 * Tracks which YaP pool owns the current thread.
 * World / inventory / block mutations must run on {@link Pool#SYNC}.
 */
public final class ThreadPools {

    private static final ThreadLocal<Pool> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<String> OWNER = ThreadLocal.withInitial(() -> "unset");

    private ThreadPools() {
    }

    public static void enter(Pool pool, String owner) {
        CURRENT.set(pool);
        OWNER.set(owner != null ? owner : pool.name());
    }

    public static void exit() {
        CURRENT.remove();
        OWNER.remove();
    }

    public static Pool current() {
        return CURRENT.get();
    }

    public static String owner() {
        return OWNER.get();
    }

    public static boolean isSync() {
        return CURRENT.get() == Pool.SYNC;
    }

    public static boolean isHeavy() {
        return CURRENT.get() == Pool.HEAVY;
    }

    public static boolean isUi() {
        return CURRENT.get() == Pool.UI;
    }

    /** Soft check — logs via IllegalStateException message if strict. */
    public static void requireSync(String action) {
        Pool p = CURRENT.get();
        if (p != null && p != Pool.SYNC) {
            throw new IllegalStateException(
                    action + " must run on SYNC (Compatibility Bridge); current pool="
                            + p + " owner=" + OWNER.get());
        }
    }

    public static void requireHeavy(String action) {
        Pool p = CURRENT.get();
        if (p != null && p != Pool.HEAVY) {
            throw new IllegalStateException(
                    action + " should run on HEAVY; current pool=" + p);
        }
    }

    public static Runnable wrap(Pool pool, String owner, Runnable task) {
        return () -> {
            enter(pool, owner);
            try {
                task.run();
            } finally {
                exit();
            }
        };
    }
}
