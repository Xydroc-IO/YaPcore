package com.yapcore.playerdata.claims;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.db.ClaimRepository;
import com.yapcore.playerdata.economy.BalanceStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.logging.Level;

/**
 * Accrues claim taxes and freezes / auto-abandons unpaid top-level claims.
 */
public final class TaxService {

    private final JavaPlugin plugin;
    private final PlayerDataConfig config;
    private final ClaimService claims;
    private final ClaimRepository repo;
    private final BalanceStore balances;
    private BukkitTask task;

    public TaxService(JavaPlugin plugin, PlayerDataConfig config, ClaimService claims,
                      BalanceStore balances) {
        this.plugin = plugin;
        this.config = config;
        this.claims = claims;
        this.repo = claims.repo();
        this.balances = balances;
    }

    public void start() {
        if (!config.claimsTaxEnabled() || config.claimsTaxPerBlockPerDay() <= 0) {
            return;
        }
        long period = 20L * 60L * Math.max(1, config.claimsTaxTickMinutes());
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::tick, period, period);
        plugin.getLogger().info("Claim taxes enabled: $"
                + config.claimsTaxPerBlockPerDay() + "/block/day");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        try {
            double perDay = config.claimsTaxPerBlockPerDay();
            double fraction = config.claimsTaxTickMinutes() / (60.0 * 24.0);
            double freezeAt = config.claimsTaxFreezeAmount();
            double abandonAt = config.claimsTaxAbandonAmount();

            for (var claim : repo.listTopLevelForTax(config.serverId())) {
                double add = claim.area() * perDay * fraction;
                double due = Math.round((claim.taxDue() + add) * 100.0) / 100.0;
                boolean frozen = due >= freezeAt;
                repo.setTax(claim.id(), due, frozen);
                claim.setTaxDue(due);
                claim.setTaxFrozen(frozen);
                claims.updateLocal(claim);

                if (due >= abandonAt) {
                    autoAbandon(claim);
                    continue;
                }
                if (frozen) {
                    Player owner = Bukkit.getPlayer(claim.owner());
                    if (owner != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> owner.sendMessage(
                                "§cClaim #" + claim.id() + " is tax-frozen ($"
                                        + String.format("%.2f", due) + " due). §e/claim paytax"));
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "tax tick failed", e);
        }
    }

    private void autoAbandon(com.yapcore.playerdata.claims.Claim claim) {
        try {
            repo.delete(claim.id());
            claims.reloadLocal();
            Player owner = Bukkit.getPlayer(claim.owner());
            if (owner != null) {
                Bukkit.getScheduler().runTask(plugin, () -> owner.sendMessage(
                        "§cClaim #" + claim.id() + " was auto-abandoned for unpaid taxes."));
            }
            plugin.getLogger().info("Auto-abandoned claim #" + claim.id() + " for taxes");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "tax abandon failed", e);
        }
    }

    /** Pay all tax due on the claim under the player's feet (or top-level). */
    public String payTax(Player player) throws SQLException {
        var opt = claims.getTopLevelAt(player.getLocation());
        if (opt.isEmpty()) {
            return "§cStand in a claim you own.";
        }
        var claim = opt.get();
        if (!claim.owner().equals(player.getUniqueId()) && !player.hasPermission("yapdata.claims.admin")) {
            return "§cOnly the owner can pay claim tax.";
        }
        double due = claim.taxDue();
        if (due <= 0) {
            return "§aNo tax due on claim #" + claim.id();
        }
        double bal = balances.getBalance(player.getUniqueId());
        if (bal < due) {
            return "§cNeed $" + String.format("%.2f", due) + " (you have $"
                    + String.format("%.2f", bal) + ").";
        }
        balances.setBalance(player.getUniqueId(), bal - due);
        repo.setTax(claim.id(), 0, false);
        claim.setTaxDue(0);
        claim.setTaxFrozen(false);
        claims.updateLocal(claim);
        return "§aPaid §f$" + String.format("%.2f", due) + " §atax on claim §f#" + claim.id();
    }

    public String status(Player player) {
        var opt = claims.getTopLevelAt(player.getLocation());
        if (opt.isEmpty()) {
            return "§7No claim here.";
        }
        var c = opt.get();
        double perDay = c.area() * config.claimsTaxPerBlockPerDay();
        return "§aClaim §f#" + c.id() + " §7tax due §f$" + String.format("%.2f", c.taxDue())
                + (c.taxFrozen() ? " §c[FROZEN]" : "")
                + " §7· ~$" + String.format("%.2f", perDay) + "/day";
    }
}
