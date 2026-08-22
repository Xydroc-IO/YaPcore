package com.yapcore.playerdata.feature;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.db.JobRepository;
import com.yapcore.playerdata.economy.BalanceStore;
import com.yapcore.playerdata.sync.SyncService;
import com.yapcore.sched.YapSched;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class JobListener implements Listener {
    private final JavaPlugin plugin;
    private final PlayerDataConfig config;
    private final JobRepository jobs;
    private final BalanceStore balances;
    private final SyncService sync;

    public JobListener(JavaPlugin plugin, PlayerDataConfig config, JobRepository jobs,
                       BalanceStore balances, SyncService sync) {
        this.plugin = plugin;
        this.config = config;
        this.jobs = jobs;
        this.balances = balances;
        this.sync = sync;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!sync.isReady(player.getUniqueId())) {
            return;
        }
        YapSched.async(plugin, () -> {
            try {
                var progress = jobs.list(player.getUniqueId());
                if (progress.isEmpty()) {
                    return;
                }
                double pay = 0;
                String gainedJob = null;
                for (var p : progress) {
                    PlayerDataConfig.JobDef def = config.jobs().get(p.job());
                    if (def == null) {
                        continue;
                    }
                    Double amount = def.breakPays().get(event.getBlock().getType());
                    if (amount == null) {
                        continue;
                    }
                    double multi = 1.0 + (p.level() - 1) * 0.05;
                    pay += amount * multi;
                    jobs.addXp(player.getUniqueId(), p.job(), def.xpPerAction());
                    gainedJob = def.display();
                }
                if (pay <= 0) {
                    return;
                }
                double finalPay = Math.round(pay * 100.0) / 100.0;
                String jobName = gainedJob;
                YapSched.entity(plugin, player, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    balances.setBalance(player.getUniqueId(),
                            balances.getBalance(player.getUniqueId()) + finalPay);
                    player.sendActionBar(net.kyori.adventure.text.Component.text(
                            "+" + String.format("$%.2f", finalPay)
                                    + (jobName != null ? " (" + jobName + ")" : "")));
                });
            } catch (Exception ignored) {
            }
        });
    }
}
