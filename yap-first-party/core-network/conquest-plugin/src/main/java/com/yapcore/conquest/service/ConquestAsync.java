package com.yapcore.conquest.service;

import com.yapcore.sched.YapSched;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** DB work on Folia's async scheduler, returned as a future for command callbacks. */
final class ConquestAsync {

    private ConquestAsync() {
    }

    static CompletableFuture<Void> run(Plugin plugin, Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        YapSched.async(plugin, () -> {
            try {
                task.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    static <T> CompletableFuture<T> supply(Plugin plugin, Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        YapSched.async(plugin, () -> {
            try {
                future.complete(task.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }
}
