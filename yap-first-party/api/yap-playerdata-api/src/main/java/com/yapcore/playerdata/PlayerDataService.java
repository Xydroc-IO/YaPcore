package com.yapcore.playerdata;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Session lock + profile network state + native economy for first-party plugins.
 * Provided by {@code YaPPlayerData} via {@code ServicesManager}.
 * Prefer this over Vault — Vault is an optional third-party bridge only.
 */
public interface PlayerDataService {

    String serverId();

    Optional<String> lockHolder(UUID uuid);

    CompletableFuture<Boolean> tryAcquireSessionLock(UUID uuid, String serverId);

    CompletableFuture<Void> releaseSessionLock(UUID uuid, String serverId);

    CompletableFuture<Optional<String>> lastKnownIp(UUID uuid);

    /** Whether economy features are enabled in YaPPlayerData. */
    boolean economyEnabled();

    /** Current economy balance (0 if economy off / unknown). */
    double balance(UUID uuid);

    /**
     * Add money. Returns new balance, or empty if economy is off / amount invalid.
     */
    Optional<Double> deposit(UUID uuid, double amount);

    /**
     * Remove money. Returns new balance, or empty if economy off / insufficient / invalid.
     */
    Optional<Double> withdraw(UUID uuid, double amount);

    /**
     * Set absolute balance. Returns new balance, or empty if economy off / amount invalid.
     */
    Optional<Double> setBalance(UUID uuid, double amount);

    /** Format for chat, e.g. {@code $12.34}. */
    default String formatMoney(double amount) {
        return String.format("$%.2f", amount);
    }

    /**
     * Lifetime playtime in whole minutes (persisted + current session).
     * Returns 0 when playtime tracking is unavailable.
     */
    default long playMinutes(UUID uuid) {
        return 0L;
    }
}
