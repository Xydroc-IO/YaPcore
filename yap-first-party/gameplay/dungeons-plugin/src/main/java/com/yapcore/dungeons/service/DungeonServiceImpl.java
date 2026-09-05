package com.yapcore.dungeons.service;

import com.yapcore.dungeons.DungeonGate;
import com.yapcore.dungeons.DungeonInvite;
import com.yapcore.dungeons.DungeonProgress;
import com.yapcore.dungeons.DungeonRun;
import com.yapcore.dungeons.DungeonService;
import com.yapcore.dungeons.DungeonsConfig;
import com.yapcore.dungeons.db.DungeonRepository;
import com.yapcore.dungeons.gate.GateEvaluator;
import com.yapcore.dungeons.portal.PortalItems;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class DungeonServiceImpl implements DungeonService {

    private final JavaPlugin plugin;
    private final DungeonsConfig config;
    private final DungeonRepository repository;
    private final GateEvaluator gates;
    private final DungeonInstanceManager instances;
    private final PortalItems portalItems;

    public DungeonServiceImpl(
            JavaPlugin plugin,
            DungeonsConfig config,
            DungeonRepository repository,
            GateEvaluator gates,
            DungeonInstanceManager instances,
            PortalItems portalItems) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.gates = gates;
        this.instances = instances;
        this.portalItems = portalItems;
    }

    @Override
    public boolean enabled() {
        return config.enabled();
    }

    @Override
    public CompletableFuture<List<Integer>> selectableLevels(UUID playerId) {
        return gates.overallLevel(playerId).thenCompose(overall ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return UnlockMath.selectableLevels(repository.getProgress(playerId), overall);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "selectableLevels", e);
                        return List.of();
                    }
                }));
    }

    @Override
    public CompletableFuture<DungeonProgress> progress(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.getProgress(playerId);
            } catch (Exception e) {
                return DungeonProgress.empty(playerId);
            }
        });
    }

    @Override
    public CompletableFuture<DungeonGate> gateFor(int dungeonLevel) {
        return CompletableFuture.completedFuture(gates.gate(dungeonLevel));
    }

    @Override
    public CompletableFuture<Optional<DungeonRun>> startRun(Player leader, int dungeonLevel) {
        if (!config.enabled()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        if (instances.byPlayer(leader.getUniqueId()).isPresent()) {
            leader.sendMessage("§cYou are already in a dungeon.");
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return selectableLevels(leader.getUniqueId()).thenCompose(levels -> {
            if (!levels.contains(dungeonLevel)) {
                YapSched.entity(plugin, leader, () -> leader.sendMessage("§cThat dungeon level is locked."));
                return CompletableFuture.completedFuture(Optional.empty());
            }
            return gates.evaluate(leader.getUniqueId(), dungeonLevel).thenCompose(result -> {
                if (!result.ok()) {
                    YapSched.entity(plugin, leader, () ->
                            leader.sendMessage("§cGate failed: §7" + String.join(", ", result.failures())));
                    return CompletableFuture.completedFuture(Optional.empty());
                }
                return instances.createAndGenerate(leader, dungeonLevel)
                        .thenApply(opt -> opt.map(LiveRun::snapshot));
            });
        });
    }

    @Override
    public CompletableFuture<Boolean> invite(Player leader, UUID invitee) {
        LiveRun run = instances.byPlayer(leader.getUniqueId()).orElse(null);
        if (run == null || !run.leader().equals(leader.getUniqueId())) {
            return CompletableFuture.completedFuture(false);
        }
        if (run.members().size() >= config.maxPartySize()) {
            leader.sendMessage("§cParty is full.");
            return CompletableFuture.completedFuture(false);
        }
        if (instances.byPlayer(invitee).isPresent()) {
            leader.sendMessage("§cThat player is already in a dungeon.");
            return CompletableFuture.completedFuture(false);
        }
        return gates.evaluate(invitee, run.dungeonLevel()).thenCompose(result -> {
            if (!result.ok()) {
                YapSched.entity(plugin, leader, () ->
                        leader.sendMessage("§cInvitee fails gates: §7" + String.join(", ", result.failures())));
                return CompletableFuture.completedFuture(false);
            }
            DungeonInvite invite = new DungeonInvite(
                    run.runId(),
                    invitee,
                    leader.getUniqueId(),
                    Instant.now(),
                    Instant.now().plus(config.inviteExpireMinutes(), ChronoUnit.MINUTES));
            return CompletableFuture.supplyAsync(() -> {
                try {
                    repository.upsertInvite(invite);
                    Player target = Bukkit.getPlayer(invitee);
                    if (target != null) {
                        String prefix = run.runId().substring(0, 8);
                        YapSched.entity(plugin, target, () -> target.sendMessage(
                                "§6Dungeon invite from §f" + leader.getName()
                                        + " §7(L" + run.dungeonLevel() + "). §e/dungeon accept " + prefix));
                    }
                    YapSched.entity(plugin, leader, () -> leader.sendMessage("§aInvite sent."));
                    return true;
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "invite", e);
                    return false;
                }
            });
        });
    }

    @Override
    public CompletableFuture<Boolean> acceptInvite(Player invitee, String runIdPrefix) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                DungeonInvite matched = null;
                for (DungeonInvite inv : repository.invitesFor(invitee.getUniqueId())) {
                    if (inv.isExpired()) {
                        continue;
                    }
                    if (inv.runId().startsWith(runIdPrefix) || inv.runId().equals(runIdPrefix)) {
                        matched = inv;
                        break;
                    }
                }
                if (matched == null) {
                    YapSched.entity(plugin, invitee, () -> invitee.sendMessage("§cNo matching invite."));
                    return false;
                }
                LiveRun live = instances.byId(matched.runId()).orElse(null);
                if (live == null || live.isTerminal()) {
                    YapSched.entity(plugin, invitee, () -> invitee.sendMessage("§cThat run is gone."));
                    return false;
                }
                if (live.members().size() >= config.maxPartySize()) {
                    YapSched.entity(plugin, invitee, () -> invitee.sendMessage("§cParty is full."));
                    return false;
                }
                var gate = gates.evaluate(invitee.getUniqueId(), live.dungeonLevel()).join();
                if (!gate.ok()) {
                    YapSched.entity(plugin, invitee, () ->
                            invitee.sendMessage("§cGate failed: §7" + String.join(", ", gate.failures())));
                    return false;
                }
                repository.deleteInvite(matched.runId(), invitee.getUniqueId());
                instances.addMember(live, invitee.getUniqueId());
                LocationTeleport.teleport(plugin, invitee, live);
                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "acceptInvite", e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> denyInvite(Player invitee, String runIdPrefix) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                for (DungeonInvite inv : repository.invitesFor(invitee.getUniqueId())) {
                    if (inv.runId().startsWith(runIdPrefix) || inv.runId().equals(runIdPrefix)) {
                        repository.deleteInvite(inv.runId(), invitee.getUniqueId());
                        YapSched.entity(plugin, invitee, () -> invitee.sendMessage("§7Invite denied."));
                        return true;
                    }
                }
                return false;
            } catch (Exception e) {
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> leave(Player player) {
        instances.removePlayer(player.getUniqueId(), true);
        player.sendMessage("§7Left dungeon.");
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public Optional<DungeonRun> activeRun(UUID playerId) {
        return instances.byPlayer(playerId).map(LiveRun::snapshot);
    }

    @Override
    public Optional<DungeonRun> runById(String runId) {
        return instances.byId(runId).map(LiveRun::snapshot);
    }

    @Override
    public List<DungeonRun> activeRuns() {
        return instances.activeSnapshots();
    }

    @Override
    public ItemStack createPortalItem() {
        return portalItems.createPortalItem();
    }

    @Override
    public CompletableFuture<Boolean> forceStop(String runIdPrefix) {
        for (var snap : instances.activeSnapshots()) {
            if (snap.runId().startsWith(runIdPrefix) || snap.runId().equals(runIdPrefix)) {
                instances.fail(snap.runId(), "admin stop");
                return CompletableFuture.completedFuture(true);
            }
        }
        return CompletableFuture.completedFuture(false);
    }

    public DungeonInstanceManager instances() {
        return instances;
    }
}
