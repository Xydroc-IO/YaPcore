package com.yapcore.dungeons;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Public dungeon contract for soft-depend plugins. */
public interface DungeonService {

    boolean enabled();

    /** Highest dungeon level selectable for this player (0 if none). */
    CompletableFuture<List<Integer>> selectableLevels(UUID playerId);

    CompletableFuture<DungeonProgress> progress(UUID playerId);

    CompletableFuture<DungeonGate> gateFor(int dungeonLevel);

    /** Start a new run as leader after gate checks. */
    CompletableFuture<Optional<DungeonRun>> startRun(Player leader, int dungeonLevel);

    CompletableFuture<Boolean> invite(Player leader, UUID invitee);

    CompletableFuture<Boolean> acceptInvite(Player invitee, String runId);

    CompletableFuture<Boolean> denyInvite(Player invitee, String runId);

    CompletableFuture<Boolean> leave(Player player);

    Optional<DungeonRun> activeRun(UUID playerId);

    Optional<DungeonRun> runById(String runId);

    List<DungeonRun> activeRuns();

    /** Craftable portal item stack. */
    ItemStack createPortalItem();

    /** Force-stop and delete a run (admin). */
    CompletableFuture<Boolean> forceStop(String runId);
}
