package com.yapcore.world;

import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Soft EditSession-like apply surface for other YaP plugins (not EngineHub API).
 */
public interface EditApplyService {

    CompletableFuture<Integer> fillPattern(Player player, CuboidSelection selection, String pattern);

    CompletableFuture<Integer> replaceMask(Player player, CuboidSelection selection, String fromMask, String toPattern);

    Optional<CuboidSelection> selection(UUID playerUuid);
}
