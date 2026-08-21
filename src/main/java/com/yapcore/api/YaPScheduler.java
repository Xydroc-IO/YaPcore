package com.yapcore.api;

/**
 * Scheduler for next-gen plugins — routes to UI / Heavy I/O / SYNC bridge.
 */
public interface YaPScheduler {

    void run(Pool pool, Runnable task);

    void runLater(Pool pool, Runnable task, long delayMs);

    default void runUi(Runnable task) {
        run(Pool.UI, task);
    }

    default void runHeavy(Runnable task) {
        run(Pool.HEAVY, task);
    }

    default void runSync(Runnable task) {
        run(Pool.SYNC, task);
    }

    /**
     * Typical all-in-one flow: heavy DB work, then sync world mutation, then UI feedback.
     */
    default void runPurchaseFlow(Runnable heavyVerify, Runnable syncApply, Runnable uiFeedback) {
        runHeavy(() -> {
            heavyVerify.run();
            runSync(() -> {
                syncApply.run();
                runUi(uiFeedback);
            });
        });
    }
}
