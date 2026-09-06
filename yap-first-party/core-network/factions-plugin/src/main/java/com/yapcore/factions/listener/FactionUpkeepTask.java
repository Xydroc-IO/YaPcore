package com.yapcore.factions.listener;

import com.yapcore.factions.Faction;
import com.yapcore.factions.FactionClaimOverlay;
import com.yapcore.factions.FactionsConfig;
import com.yapcore.factions.integration.ClaimIntegration;
import com.yapcore.factions.service.FactionServiceImpl;
import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;

/** Debits faction bank for linked claims; freezes claims after grace if unpaid. */
public final class FactionUpkeepTask {

    private final JavaPlugin plugin;
    private final FactionsConfig config;
    private final FactionServiceImpl factions;
    private YapTask task;

    public FactionUpkeepTask(JavaPlugin plugin, FactionsConfig config, FactionServiceImpl factions) {
        this.plugin = plugin;
        this.config = config;
        this.factions = factions;
    }

    public void start() {
        stop();
        if (!config.upkeepEnabled() || config.upkeepCostPerClaim() <= 0) {
            return;
        }
        long period = Math.max(20L * 60L, config.upkeepPeriodTicks());
        task = YapSched.asyncTimer(plugin, this::tick, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Admin force-collect. {@code factionRef} null/blank = all factions. Returns processed count. */
    public int collectNow(String factionRef) {
        int count = 0;
        if (factionRef != null && !factionRef.isBlank()) {
            Faction faction = factions.findByName(factionRef)
                    .or(() -> factions.findByTag(factionRef))
                    .orElse(null);
            if (faction == null) {
                return 0;
            }
            processFaction(faction, true);
            return 1;
        }
        for (Faction faction : factions.listFactions()) {
            processFaction(faction, true);
            count++;
        }
        return count;
    }

    void tick() {
        try {
            for (Faction faction : factions.listFactions()) {
                processFaction(faction, false);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "faction upkeep tick", e);
        }
    }

    private void processFaction(Faction faction, boolean forceFreeze) {
        List<FactionClaimOverlay> claims = factions.listClaims(faction.id());
        if (claims.isEmpty()) {
            clearUnpaid(faction);
            return;
        }
        double due = claims.size() * config.upkeepCostPerClaim();
        if (due <= 0) {
            return;
        }
        if (faction.bankBalance() >= due) {
            try {
                factions.adminDebitBank(faction.id(), due);
                factions.adminSetUpkeepUnpaidSince(faction.id(), null);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "upkeep debit " + faction.tag(), e);
                return;
            }
            for (FactionClaimOverlay overlay : claims) {
                ClaimIntegration.setTaxFrozen(overlay.claimId(), false);
            }
            return;
        }

        Instant unpaidSince = faction.upkeepUnpaidSince();
        if (unpaidSince == null) {
            unpaidSince = Instant.now();
            try {
                factions.adminSetUpkeepUnpaidSince(faction.id(), unpaidSince);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "upkeep mark unpaid " + faction.tag(), e);
            }
        }

        long graceSeconds = config.upkeepGraceHours() * 3600L;
        boolean pastGrace = forceFreeze
                || graceSeconds <= 0
                || Duration.between(unpaidSince, Instant.now()).getSeconds() >= graceSeconds;
        if (pastGrace) {
            for (FactionClaimOverlay overlay : claims) {
                ClaimIntegration.setTaxFrozen(overlay.claimId(), true);
            }
            plugin.getLogger().info("Faction " + faction.tag()
                    + " upkeep unpaid — linked claims frozen"
                    + (forceFreeze ? " (forced)" : " (past grace)"));
        } else {
            long remaining = graceSeconds - Duration.between(unpaidSince, Instant.now()).getSeconds();
            plugin.getLogger().fine("Faction " + faction.tag()
                    + " upkeep unpaid; freeze in ~" + (remaining / 3600) + "h");
        }
    }

    private void clearUnpaid(Faction faction) {
        if (faction.upkeepUnpaidSince() == null) {
            return;
        }
        try {
            factions.adminSetUpkeepUnpaidSince(faction.id(), null);
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "clear upkeep unpaid", e);
        }
    }
}
