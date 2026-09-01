package com.yapcore.playerdata;

import com.yapcore.playerdata.auth.AuthListener;
import com.yapcore.playerdata.auth.AuthService;
import com.yapcore.playerdata.claims.ClaimFlagRepository;
import com.yapcore.playerdata.claims.ClaimFlagService;
import com.yapcore.playerdata.claims.ClaimListener;
import com.yapcore.playerdata.claims.ClaimService;
import com.yapcore.playerdata.claims.TaxService;
import com.yapcore.playerdata.cmd.AdminCommand;
import com.yapcore.playerdata.cmd.AuctionCommands;
import com.yapcore.playerdata.cmd.AuthCommands;
import com.yapcore.playerdata.cmd.BalanceCommands;
import com.yapcore.playerdata.cmd.ClaimCommands;
import com.yapcore.playerdata.cmd.HomeCommands;
import com.yapcore.playerdata.cmd.JobCommands;
import com.yapcore.playerdata.cmd.KitCommands;
import com.yapcore.playerdata.cmd.MailCommands;
import com.yapcore.playerdata.cmd.MenuCommand;
import com.yapcore.playerdata.cmd.ShopCommands;
import com.yapcore.playerdata.cmd.TraderCommands;
import com.yapcore.playerdata.cmd.WarpCommands;
import com.yapcore.playerdata.db.AuctionRepository;
import com.yapcore.playerdata.db.AuthRepository;
import com.yapcore.playerdata.db.ClaimRepository;
import com.yapcore.playerdata.db.Database;
import com.yapcore.playerdata.db.HomesRepository;
import com.yapcore.playerdata.db.JobRepository;
import com.yapcore.playerdata.db.KitRepository;
import com.yapcore.playerdata.db.MailRepository;
import com.yapcore.playerdata.db.NpcTraderRepository;
import com.yapcore.playerdata.db.PlayerRepository;
import com.yapcore.playerdata.db.ShopRepository;
import com.yapcore.playerdata.db.WarpsRepository;
import com.yapcore.playerdata.economy.BalanceStore;
import com.yapcore.playerdata.economy.YaPEconomy;
import com.yapcore.playerdata.feature.JobListener;
import com.yapcore.playerdata.feature.ShopListener;
import com.yapcore.playerdata.gui.MenuListener;
import com.yapcore.playerdata.gui.Menus;
import com.yapcore.playerdata.kit.KitGrantService;
import com.yapcore.playerdata.npc.NpcTraderListener;
import com.yapcore.playerdata.npc.NpcTraderService;
import com.yapcore.playerdata.service.PlayerDataServiceImpl;
import com.yapcore.playerdata.sync.JoinQuitListener;
import com.yapcore.playerdata.sync.SessionLock;
import com.yapcore.playerdata.sync.SyncService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Cross-server player data + optional modules (homes, claims, economy features).
 */
public final class PlayerDataPlugin extends JavaPlugin {

    private PlayerDataConfig config;
    private Database database;
    private SyncService sync;
    private YaPEconomy economy;
    private ClaimService claims;
    private TaxService taxes;
    private NpcTraderService traders;
    private Menus menus;
    private PlayerDataServiceImpl playerDataService;
    private KitGrantService kitGrants;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new PlayerDataConfig(this);
        config.reload();

        database = new Database(this, config);
        try {
            database.open();
        } catch (Exception e) {
            getLogger().severe("Failed to open MariaDB/MySQL — disabling YaPPlayerData: " + e.getMessage());
            getLogger().severe("Prefer shared YaPDB (yap-db.jar). JDBC must match deploy/mariadb/.env (port often 3316).");
            getLogger().severe("Setup: ./scripts/db/ensure-db.sh --server-id <id>   (or Windows Start-MariaDB + Configure-Db)");
            getLogger().severe("Docs: docs/data/YAPDB.md · docs/data/MARIADB.md");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PlayerRepository repository = new PlayerRepository(database, config);
        SessionLock locks = new SessionLock(repository, config);
        sync = new SyncService(this, config, repository, locks);

        AuthRepository authRepo = new AuthRepository(database);
        AuthService auth = new AuthService(this, config, authRepo);
        auth.bindSync(sync);
        sync.bindAuth(auth);
        sync.startAutosave();

        playerDataService = new PlayerDataServiceImpl(this, config, locks, repository, authRepo);
        getServer().getServicesManager().register(
                com.yapcore.playerdata.PlayerDataService.class,
                playerDataService, this, ServicePriority.Normal);

        BalanceStore balances = new BalanceStore(sync, repository, getLogger());
        HomesRepository homes = new HomesRepository(database);
        WarpsRepository warps = new WarpsRepository(database);
        MailRepository mail = new MailRepository(database);
        KitRepository kits = new KitRepository(database);
        ShopRepository shops = new ShopRepository(database);
        JobRepository jobs = new JobRepository(database);
        AuctionRepository auctions = new AuctionRepository(database);
        ClaimRepository claimRepo = new ClaimRepository(database);
        NpcTraderRepository traderRepo = new NpcTraderRepository(database);

        if (config.featureClaims()) {
            ClaimFlagRepository flagRepo = new ClaimFlagRepository(database);
            ClaimFlagService flagService = new ClaimFlagService(flagRepo, config);
            claims = new ClaimService(this, config, claimRepo, flagService);
            claims.start();
            if (config.claimsTaxEnabled()) {
                taxes = new TaxService(this, config, claims, balances);
                taxes.start();
            }
        }

        if (config.featureTraders()) {
            traders = new NpcTraderService(this, config, traderRepo, balances);
            traders.start();
        }

        menus = new Menus(this, config, sync, balances, homes, warps, kits, jobs, auctions, mail, claims);
        kitGrants = config.featureKits() ? new KitGrantService(this, config, kits, sync) : null;

        getServer().getPluginManager().registerEvents(
                new JoinQuitListener(this, sync, config.featureMail() ? mail : null, kitGrants), this);
        getServer().getPluginManager().registerEvents(new AuthListener(auth, repository, config), this);
        getServer().getPluginManager().registerEvents(new MenuListener(menus, traders), this);
        if (claims != null) {
            getServer().getPluginManager().registerEvents(new ClaimListener(this, claims), this);
        }
        if (traders != null) {
            getServer().getPluginManager().registerEvents(new NpcTraderListener(traders), this);
        }

        if (config.economyEnabled()) {
            BalanceCommands balanceCommands = new BalanceCommands(balances);
            bind("bal", balanceCommands, balanceCommands);
            bind("pay", balanceCommands, balanceCommands);
        } else {
            bindDisabled("bal", "economy");
            bindDisabled("pay", "economy");
        }

        AdminCommand admin = new AdminCommand(this, config, database, sync, auth);
        bind("yapdata", admin, admin);

        AuthCommands authCommands = new AuthCommands(auth, authRepo);
        bind("register", authCommands, authCommands);
        bind("login", authCommands, authCommands);
        bind("changepassword", authCommands, authCommands);
        bind("logout", authCommands, authCommands);
        bind("unregister", authCommands, authCommands);

        MenuCommand menuCommand = new MenuCommand(menus, sync);
        bind("menu", menuCommand, menuCommand);

        if (config.featureHomes()) {
            HomeCommands homeCommands = new HomeCommands(config, homes, sync, menus);
            bind("sethome", homeCommands, homeCommands);
            bind("home", homeCommands, homeCommands);
            bind("delhome", homeCommands, homeCommands);
            bind("homes", homeCommands, homeCommands);
        } else {
            bindDisabled("sethome", "features.homes");
            bindDisabled("home", "features.homes");
            bindDisabled("delhome", "features.homes");
            bindDisabled("homes", "features.homes");
        }

        if (config.featureWarps()) {
            WarpCommands warpCommands = new WarpCommands(config, warps, menus);
            bind("setwarp", warpCommands, warpCommands);
            bind("delwarp", warpCommands, warpCommands);
            bind("warp", warpCommands, warpCommands);
            bind("warps", warpCommands, warpCommands);
        } else {
            bindDisabled("setwarp", "features.warps");
            bindDisabled("delwarp", "features.warps");
            bindDisabled("warp", "features.warps");
            bindDisabled("warps", "features.warps");
        }

        if (config.featureKits()) {
            KitCommands kitCommands = new KitCommands(this, config, kits, sync, kitGrants, menus);
            bind("kit", kitCommands, kitCommands);
            bind("kits", kitCommands, kitCommands);
        } else {
            bindDisabled("kit", "features.kits");
            bindDisabled("kits", "features.kits");
        }

        if (config.featureMail()) {
            MailCommands mailCommands = new MailCommands(config, mail, sync, menus);
            bind("mail", mailCommands, mailCommands);
        } else {
            bindDisabled("mail", "features.mail");
        }

        if (config.featureShops()) {
            ShopCommands shopCommands = new ShopCommands(config, shops, balances, sync);
            bind("shop", shopCommands, shopCommands);
            getServer().getPluginManager().registerEvents(new ShopListener(shopCommands), this);
        } else {
            bindDisabled("shop", config.economyEnabled() ? "features.shops" : "economy");
        }

        if (config.featureJobs()) {
            JobCommands jobCommands = new JobCommands(config, jobs, sync, menus);
            bind("jobs", jobCommands, jobCommands);
            getServer().getPluginManager().registerEvents(
                    new JobListener(this, config, jobs, balances, sync), this);
        } else {
            bindDisabled("jobs", config.economyEnabled() ? "features.jobs" : "economy");
        }

        if (config.featureAuctions()) {
            AuctionCommands auctionCommands = new AuctionCommands(config, auctions, balances, sync, menus);
            bind("ah", auctionCommands, auctionCommands);
        } else {
            bindDisabled("ah", config.economyEnabled() ? "features.auctions" : "economy");
        }

        if (config.featureClaims() && claims != null) {
            ClaimCommands claimCommands = new ClaimCommands(this, claims, taxes, sync, menus);
            bind("claim", claimCommands, claimCommands);
        } else {
            bindDisabled("claim", "features.claims");
        }

        if (config.featureTraders() && traders != null) {
            TraderCommands traderCommands = new TraderCommands(traders, sync);
            bind("trader", traderCommands, traderCommands);
        } else {
            bindDisabled("trader", config.economyEnabled() ? "features.traders" : "economy");
        }

        if (config.economyEnabled() && config.syncEconomy()
                && Bukkit.getPluginManager().getPlugin("Vault") != null) {
            try {
                economy = new YaPEconomy(this, sync, balances);
                economy.register();
            } catch (Throwable t) {
                getLogger().warning("Vault present but Economy registration failed: " + t.getMessage());
            }
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            sync.beginJoin(online);
        }

        getLogger().info("YaPPlayerData 0.6 — server-id=" + config.serverId()
                + " profile=" + config.inventoryProfile()
                + " auth=" + (auth.isActive() ? "on" : "off")
                + " economy=" + (config.economyEnabled() ? "on" : "off")
                + " modules=" + enabledModulesSummary());
    }

    private String enabledModulesSummary() {
        List<String> on = new ArrayList<>();
        if (config.featureHomes()) {
            on.add("homes");
        }
        if (config.featureWarps()) {
            on.add("warps");
        }
        if (config.featureKits()) {
            on.add("kits");
        }
        if (config.featureMail()) {
            on.add("mail");
        }
        if (config.featureShops()) {
            on.add("shops");
        }
        if (config.featureJobs()) {
            on.add("jobs");
        }
        if (config.featureAuctions()) {
            on.add("ah");
        }
        if (config.featureClaims()) {
            on.add("claims");
        }
        if (config.featureTraders()) {
            on.add("traders");
        }
        return on.isEmpty() ? "none" : String.join(",", on);
    }

    @Override
    public void onDisable() {
        if (traders != null) {
            traders.stop();
            traders = null;
        }
        if (taxes != null) {
            taxes.stop();
            taxes = null;
        }
        if (claims != null) {
            claims.stop();
            claims = null;
        }
        if (sync != null) {
            sync.shutdown();
        }
        if (economy != null) {
            try {
                economy.unregister();
            } catch (Throwable ignored) {
            }
            economy = null;
        }
        if (playerDataService != null) {
            getServer().getServicesManager().unregister(
                    com.yapcore.playerdata.PlayerDataService.class, playerDataService);
            playerDataService = null;
        }
        if (database != null) {
            database.close();
            database = null;
        }
        sync = null;
        menus = null;
    }

    public SyncService sync() {
        return sync;
    }

    public Menus menus() {
        return menus;
    }

    public ClaimService claims() {
        return claims;
    }

    private void bind(String name, org.bukkit.command.CommandExecutor exec,
                      org.bukkit.command.TabCompleter tabs) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Missing command in plugin.yml: " + name);
            return;
        }
        cmd.setExecutor(exec);
        cmd.setTabCompleter(tabs);
    }

    private void bindDisabled(String name, String configKey) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            return;
        }
        cmd.setExecutor((sender, command, label, args) -> {
            tellDisabled(sender, configKey);
            return true;
        });
        cmd.setTabCompleter((sender, command, alias, args) -> List.of());
    }

    private static void tellDisabled(CommandSender sender, String configKey) {
        sender.sendMessage("§cYaPPlayerData: that feature is disabled (§f"
                + configKey + "§c in plugins/YaPPlayerData/config.yml).");
    }
}
