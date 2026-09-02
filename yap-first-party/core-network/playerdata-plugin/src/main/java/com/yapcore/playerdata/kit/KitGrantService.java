package com.yapcore.playerdata.kit;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.cmd.Perms;
import com.yapcore.playerdata.db.KitRepository;
import com.yapcore.playerdata.sync.SyncService;
import com.yapcore.sched.YapSched;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/** Delivers queued store/admin kit grants once playerdata sync is ready. */
public final class KitGrantService {

    private final JavaPlugin plugin;
    private final PlayerDataConfig config;
    private final KitRepository kits;
    private final SyncService sync;
    private final KitDelivery delivery;

    public KitGrantService(JavaPlugin plugin, PlayerDataConfig config, KitRepository kits, SyncService sync,
                           KitDelivery delivery) {
        this.plugin = plugin;
        this.config = config;
        this.kits = kits;
        this.sync = sync;
        this.delivery = delivery;
    }

    public void scheduleDelivery(Player player) {
        YapSched.asyncLater(plugin, () -> tryDeliver(player, 0), 40L);
    }

    private void tryDeliver(Player player, int attempt) {
        if (!player.isOnline()) {
            return;
        }
        if (!sync.isReady(player.getUniqueId())) {
            if (attempt < 25) {
                YapSched.asyncLater(plugin, () -> tryDeliver(player, attempt + 1), 20L);
            }
            return;
        }
        deliverPending(player);
        tryFirstJoin(player);
    }

    private void tryFirstJoin(Player player) {
        if (!player.isOnline() || !config.featureKits()) {
            return;
        }
        YapSched.entity(plugin, player, () -> {
            if (!player.isOnline()) {
                return;
            }
            for (KitDef def : config.kits().values()) {
                if (!def.firstJoin() || !Perms.hasKit(player, def.id())) {
                    continue;
                }
                try {
                    if (kits.lastClaim(player.getUniqueId(), def.id()).isPresent()) {
                        continue;
                    }
                    delivery.claim(player, def.id(), KitDelivery.Mode.FIRST_JOIN);
                    player.sendMessage("§aStarter kit §f" + def.id() + " §adelivered.");
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "First-join kit failed for " + player.getName(), e);
                }
            }
        });
    }

    public int deliverPending(Player player) {
        if (!config.featureKits() || !player.isOnline()) {
            return 0;
        }
        try {
            List<KitRepository.PendingGrant> pending = kits.pendingGrants(player.getUniqueId());
            if (pending.isEmpty()) {
                return 0;
            }
            int delivered = 0;
            for (KitRepository.PendingGrant grant : pending) {
                KitDef def = config.kits().get(grant.kit());
                if (def == null) {
                    plugin.getLogger().warning("Pending kit grant '" + grant.kit()
                            + "' for " + player.getName()
                            + " — kit missing from kits.yml on this backend; sync kits.yml to all servers.");
                    continue;
                }
                if (giveItemsSync(player, def)) {
                    kits.markGrantDelivered(grant.id());
                    kits.markClaimed(player.getUniqueId(), grant.kit());
                    delivered++;
                }
            }
            return delivered;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Kit grant delivery failed for " + player.getName(), e);
            return 0;
        }
    }

    /** Give kit items on the player's region thread; returns false if player left. */
    public boolean giveItemsSync(Player player, KitDef def) {
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] ok = {false};
        YapSched.entity(plugin, player, () -> {
            try {
                if (!player.isOnline()) {
                    return;
                }
                delivery.giveContents(player, def);
                delivery.runCommands(player, def);
                player.sendMessage("§aStore kit delivered: §f" + def.id());
                ok[0] = true;
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return ok[0];
    }

    public CompletableFuture<Boolean> giveOnline(Player player, KitDef def, boolean markCooldown) {
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        YapSched.entity(plugin, player, () -> {
            if (!player.isOnline()) {
                done.complete(false);
                return;
            }
            try {
                KitDelivery.Result result = delivery.claim(player, def.id(),
                        markCooldown ? KitDelivery.Mode.ADMIN : KitDelivery.Mode.ADMIN_FORCE);
                done.complete(result.outcome() == KitDelivery.Outcome.OK);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Admin kit give failed", e);
                done.complete(false);
            }
        });
        return done;
    }

    public long enqueue(UUID uuid, String kitId) throws Exception {
        return kits.enqueueGrant(uuid, kitId.toLowerCase(Locale.ROOT));
    }
}
