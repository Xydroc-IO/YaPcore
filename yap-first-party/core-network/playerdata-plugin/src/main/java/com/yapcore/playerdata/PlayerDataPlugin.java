package com.yapcore.playerdata;

import com.yapcore.playerdata.auth.AuthListener;
import com.yapcore.playerdata.auth.AuthService;
import com.yapcore.playerdata.claims.ClaimFlagRepository;
import com.yapcore.playerdata.claims.ClaimFlagService;
import com.yapcore.playerdata.claims.ClaimListener;
import com.yapcore.playerdata.claims.ClaimMessageRepository;
import com.yapcore.playerdata.claims.ClaimService;
import com.yapcore.playerdata.claims.TaxService;
import com.yapcore.playerdata.bag.BackpackListener;
import com.yapcore.playerdata.bag.BackpackService;
import com.yapcore.playerdata.cmd.AdminCommand;
import com.yapcore.playerdata.cmd.AuctionCommands;
import com.yapcore.playerdata.cmd.AuthCommands;
import com.yapcore.playerdata.cmd.BagCommands;
import com.yapcore.playerdata.cmd.BalanceCommands;
import com.yapcore.playerdata.cmd.ClaimCommands;
import com.yapcore.playerdata.cmd.HomeCommands;
import com.yapcore.playerdata.cmd.JobCommands;
import com.yapcore.playerdata.cmd.KitCommands;
import com.yapcore.playerdata.cmd.MailCommands;
import com.yapcore.playerdata.cmd.MenuCommand;
import com.yapcore.playerdata.cmd.ShopCommands;
import com.yapcore.playerdata.cmd.WarpCommands;
import com.yapcore.playerdata.db.AuctionRepository;
import com.yapcore.playerdata.db.AuthRepository;
import com.yapcore.playerdata.db.BackpackRepository;
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
import com.yapcore.playerdata.kit.KitDelivery;
import com.yapcore.playerdata.kit.KitGrantService;
import com.yapcore.playerdata.kit.KitSignListener;
import com.yapcore.playerdata.npc.NpcTraderService;
import com.yapcore.playerdata.service.PlayerDataServiceImpl;
import com.yapcore.playerdata.service.PlayerFeaturesImpl;
import com.yapcore.playerdata.sync.JoinQuitListener;
import com.yapcore.playerdata.sync.PlaytimeTracker;
import com.yapcore.playerdata.sync.SessionLock;
import com.yapcore.playerdata.sync.SyncService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Cross-server player data plane: sync, session lock, auth, schema, service APIs.
 * Player-facing QoL commands are owned by YaPEssentials via {@link PlayerFeatures}.
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
    private BackpackService backpack;
    private PlayerDataServiceImpl playerDataService;
    private PlayerFeaturesImpl playerFeatures;
    private KitGrantService kitGrants;
    private PlaytimeTracker playtime;

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

        BalanceStore balances = new BalanceStore(sync, repository, getLogger());
        playerDataService = new PlayerDataServiceImpl(this, config, locks, repository, authRepo, balances);
        playtime = new PlaytimeTracker(this, repository);
        playerDataService.bindPlaytime(playtime);
        getServer().getServicesManager().register(
                PlayerDataService.class, playerDataService, this, ServicePriority.Normal);

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
            ClaimMessageRepository messageRepo = new ClaimMessageRepository(database);
            claims = new ClaimService(this, config, claimRepo, flagService, messageRepo);
            claims.start();
            if (config.claimsTaxEnabled()) {
                taxes = new TaxService(this, config, claims, balances);
                taxes.start();
            }
        }

        if (config.featureTraders()) {
            traders = new NpcTraderService(this, config, traderRepo, balances);
            traders.start();
            getServer().getServicesManager().register(
                    NpcTraderAccess.class, traders, this, ServicePriority.Normal);
        }

        menus = new Menus(this, config, sync, balances, homes, warps, kits, jobs, auctions, mail, claims);
        playerFeatures = new PlayerFeaturesImpl(this);

        if (config.featureBackpack()) {
            BackpackRepository backpackRepo = new BackpackRepository(database);
            backpack = new BackpackService(this, config, sync, backpackRepo);
            menus.bindBackpack(backpack);
            playerFeatures.bindBackpack(backpack);
            BagCommands bagCommands = new BagCommands(this, backpack, backpackRepo, sync);
            playerFeatures.put("bag", bagCommands, bagCommands);
            playerFeatures.addListener(new BackpackListener(backpack));
        } else {
            playerFeatures.putDisabled("bag", "features.backpack");
        }

        KitDelivery kitDelivery = new KitDelivery(this, config, kits, balances);
        kitGrants = config.featureKits() ? new KitGrantService(this, config, kits, sync, kitDelivery) : null;

        getServer().getPluginManager().registerEvents(
                new JoinQuitListener(this, sync, config.featureMail() ? mail : null, kitGrants), this);
        getServer().getPluginManager().registerEvents(playtime, this);
        getServer().getPluginManager().registerEvents(new AuthListener(auth, repository, config), this);

        playerFeatures.addListener(new MenuListener(menus, traders));
        if (claims != null) {
            playerFeatures.addListener(new ClaimListener(this, claims));
        }

        if (config.economyEnabled()) {
            BalanceCommands balanceCommands = new BalanceCommands(balances);
            playerFeatures.put("bal", balanceCommands, balanceCommands);
            playerFeatures.put("pay", balanceCommands, balanceCommands);
            playerFeatures.put("eco", balanceCommands, balanceCommands);
        } else {
            playerFeatures.putDisabled("bal", "economy");
            playerFeatures.putDisabled("pay", "economy");
            playerFeatures.putDisabled("eco", "economy");
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
        playerFeatures.put("menu", menuCommand, menuCommand);

        if (config.featureHomes()) {
            HomeCommands homeCommands = new HomeCommands(config, homes, sync, menus);
            playerFeatures.put("sethome", homeCommands, homeCommands);
            playerFeatures.put("home", homeCommands, homeCommands);
            playerFeatures.put("delhome", homeCommands, homeCommands);
            playerFeatures.put("homes", homeCommands, homeCommands);
        } else {
            playerFeatures.putDisabled("sethome", "features.homes");
            playerFeatures.putDisabled("home", "features.homes");
            playerFeatures.putDisabled("delhome", "features.homes");
            playerFeatures.putDisabled("homes", "features.homes");
        }

        if (config.featureWarps()) {
            WarpCommands warpCommands = new WarpCommands(config, warps, menus);
            playerFeatures.put("setwarp", warpCommands, warpCommands);
            playerFeatures.put("delwarp", warpCommands, warpCommands);
            playerFeatures.put("warp", warpCommands, warpCommands);
            playerFeatures.put("warps", warpCommands, warpCommands);
        } else {
            playerFeatures.putDisabled("setwarp", "features.warps");
            playerFeatures.putDisabled("delwarp", "features.warps");
            playerFeatures.putDisabled("warp", "features.warps");
            playerFeatures.putDisabled("warps", "features.warps");
        }

        if (config.featureKits()) {
            KitCommands kitCommands = new KitCommands(this, config, kits, sync, kitGrants, kitDelivery, menus);
            playerFeatures.put("kit", kitCommands, kitCommands);
            playerFeatures.put("kits", kitCommands, kitCommands);
            playerFeatures.put("createkit", kitCommands, kitCommands);
            playerFeatures.put("delkit", kitCommands, kitCommands);
            playerFeatures.put("showkit", kitCommands, kitCommands);
            playerFeatures.put("kitreset", kitCommands, kitCommands);
            playerFeatures.put("kitresetcooldown", kitCommands, kitCommands);
            playerFeatures.addListener(new KitSignListener(this, kitCommands));
        } else {
            playerFeatures.putDisabled("kit", "features.kits");
            playerFeatures.putDisabled("kits", "features.kits");
            playerFeatures.putDisabled("createkit", "features.kits");
            playerFeatures.putDisabled("delkit", "features.kits");
            playerFeatures.putDisabled("showkit", "features.kits");
            playerFeatures.putDisabled("kitreset", "features.kits");
            playerFeatures.putDisabled("kitresetcooldown", "features.kits");
        }

        if (config.featureMail()) {
            MailCommands mailCommands = new MailCommands(config, mail, sync, menus);
            playerFeatures.put("mail", mailCommands, mailCommands);
        } else {
            playerFeatures.putDisabled("mail", "features.mail");
        }

        if (config.featureShops()) {
            ShopCommands shopCommands = new ShopCommands(config, shops, balances, sync);
            playerFeatures.put("shop", shopCommands, shopCommands);
            playerFeatures.addListener(new ShopListener(shopCommands));
        } else {
            playerFeatures.putDisabled("shop", config.economyEnabled() ? "features.shops" : "economy");
        }

        if (config.featureJobs()) {
            JobCommands jobCommands = new JobCommands(config, jobs, sync, menus);
            playerFeatures.put("jobs", jobCommands, jobCommands);
            playerFeatures.addListener(new JobListener(this, config, jobs, balances, sync));
        } else {
            playerFeatures.putDisabled("jobs", config.economyEnabled() ? "features.jobs" : "economy");
        }

        if (config.featureAuctions()) {
            AuctionCommands auctionCommands = new AuctionCommands(config, auctions, balances, sync, menus);
            playerFeatures.put("ah", auctionCommands, auctionCommands);
        } else {
            playerFeatures.putDisabled("ah", config.economyEnabled() ? "features.auctions" : "economy");
        }

        if (config.featureClaims() && claims != null) {
            ClaimCommands claimCommands = new ClaimCommands(this, claims, taxes, sync, menus);
            playerFeatures.put("claim", claimCommands, claimCommands);
        } else {
            playerFeatures.putDisabled("claim", "features.claims");
        }

        getServer().getServicesManager().register(
                PlayerFeatures.class, playerFeatures, this, ServicePriority.Normal);

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

        getLogger().info("YaPPlayerData 0.7 — data plane server-id=" + config.serverId()
                + " profile=" + config.inventoryProfile()
                + " auth=" + (auth.isActive() ? "on" : "off")
                + " economy=" + (config.economyEnabled() ? "on" : "off")
                + " modules=" + enabledModulesSummary()
                + " (QoL commands via YaPEssentials)");
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
            on.add("npc-shops");
        }
        if (config.featureBackpack()) {
            on.add("backpack");
        }
        return on.isEmpty() ? "none" : String.join(",", on);
    }

    @Override
    public void onDisable() {
        if (playerFeatures != null) {
            try {
                getServer().getServicesManager().unregister(PlayerFeatures.class, playerFeatures);
            } catch (Throwable ignored) {
            }
            playerFeatures.onHostDisable(this);
            playerFeatures = null;
        }
        if (backpack != null) {
            backpack.flushAllOnlineBlocking();
            backpack = null;
        }
        if (traders != null) {
            try {
                getServer().getServicesManager().unregister(NpcTraderAccess.class, traders);
            } catch (Throwable ignored) {
            }
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
        if (playtime != null) {
            playtime.flushAllOnline();
            playtime = null;
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
            getServer().getServicesManager().unregister(PlayerDataService.class, playerDataService);
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
}
