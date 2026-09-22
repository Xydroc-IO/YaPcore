package com.yapcore.claims;

import com.yapcore.claims.cmd.ClaimCommands;
import com.yapcore.claims.db.ClaimRepository;
import com.yapcore.claims.db.ClaimsDatabase;
import com.yapcore.claims.gui.ClaimsMenus;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Land claims (GriefPrevention-class) — schema shared with legacy YaPPlayerData tables.
 * {@code /claim} is owned by YaPEssentials via {@link ClaimFeatures}.
 */
public final class ClaimsPlugin extends JavaPlugin {

    private ClaimsConfig config;
    private ClaimsDatabase database;
    private ClaimService claims;
    private ClaimLookup claimLookup;
    private TaxService taxes;
    private ClaimFeaturesImpl claimFeatures;
    private ClaimsMenus menus;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new ClaimsConfig(this);
        config.reload();

        if (!config.claimsEnabled()) {
            getLogger().info("YaPClaims disabled (claims.enabled=false).");
            return;
        }

        database = new ClaimsDatabase(this, config);
        try {
            database.open();
        } catch (Exception e) {
            getLogger().severe("Failed to open claims DB — disabling YaPClaims: " + e.getMessage());
            getLogger().severe("Prefer shared YaPDB (yap-db.jar). JDBC must match YaPPlayerData / deploy MariaDB.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ClaimRepository claimRepo = new ClaimRepository(database);
        ClaimFlagRepository flagRepo = new ClaimFlagRepository(database);
        ClaimFlagService flagService = new ClaimFlagService(flagRepo, config);
        ClaimMessageRepository messageRepo = new ClaimMessageRepository(database);
        claims = new ClaimService(this, config, claimRepo, flagService, messageRepo);
        claims.start();

        claimLookup = new ClaimLookupImpl(claims);
        getServer().getServicesManager().register(
                ClaimLookup.class, claimLookup, this, ServicePriority.Normal);

        if (config.claimsTaxEnabled()) {
            taxes = new TaxService(this, config, claims);
            taxes.start();
        }

        menus = new ClaimsMenus(this, config, claims);
        ClaimCommands claimCommands = new ClaimCommands(this, claims, taxes, menus);
        claimFeatures = new ClaimFeaturesImpl();
        claimFeatures.put("claim", claimCommands, claimCommands);
        ClaimListener claimListener = new ClaimListener(this, claims);
        // Register here (not via Essentials) so shovel/protect work even if /claim bind races.
        getServer().getPluginManager().registerEvents(claimListener, this);
        getServer().getPluginManager().registerEvents(
                new com.yapcore.claims.gui.ClaimsMenuListener(menus), this);
        getServer().getServicesManager().register(
                ClaimFeatures.class, claimFeatures, this, ServicePriority.Normal);

        // Essentials may have enabled first (circular soft-deps); rebind /claim now.
        rebindEssentialsClaim();

        getLogger().info("YaPClaims ready — server=" + config.serverId()
                + " claims=" + claims.localClaims().size()
                + " tax=" + (taxes != null)
                + " (/claim via YaPEssentials)");
    }

    /** Wire YaPEssentials {@code /claim} if that plugin already finished enable. */
    private void rebindEssentialsClaim() {
        org.bukkit.plugin.Plugin ess = getServer().getPluginManager().getPlugin("YaPEssentials");
        if (!(ess instanceof JavaPlugin host) || !ess.isEnabled() || claimFeatures == null) {
            return;
        }
        try {
            org.bukkit.command.PluginCommand cmd = host.getCommand("claim");
            if (cmd == null) {
                return;
            }
            org.bukkit.command.CommandExecutor exec = claimFeatures.executor("claim");
            org.bukkit.command.TabCompleter tabs = claimFeatures.tabCompleter("claim");
            if (exec == null) {
                return;
            }
            cmd.setExecutor(exec);
            if (tabs != null) {
                cmd.setTabCompleter(tabs);
            }
            getLogger().info("Rebound /claim onto YaPEssentials (late bind).");
        } catch (Exception e) {
            getLogger().warning("Could not rebind /claim on YaPEssentials: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (claimFeatures != null) {
            try {
                getServer().getServicesManager().unregister(ClaimFeatures.class, claimFeatures);
            } catch (Throwable ignored) {
            }
            claimFeatures.onHostDisable(this);
            claimFeatures = null;
        }
        if (taxes != null) {
            taxes.stop();
            taxes = null;
        }
        if (claims != null) {
            claims.stop();
            claims = null;
        }
        if (claimLookup != null) {
            try {
                getServer().getServicesManager().unregister(ClaimLookup.class, claimLookup);
            } catch (Throwable ignored) {
            }
            claimLookup = null;
        }
        if (database != null) {
            database.close();
            database = null;
        }
        menus = null;
    }

    public ClaimsConfig config() {
        return config;
    }

    public ClaimService claims() {
        return claims;
    }

    public ClaimsMenus menus() {
        return menus;
    }
}
