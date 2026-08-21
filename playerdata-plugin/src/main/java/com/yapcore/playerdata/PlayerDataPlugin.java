package com.yapcore.playerdata;

import com.yapcore.playerdata.claims.ClaimListener;
import com.yapcore.playerdata.claims.ClaimService;
import com.yapcore.playerdata.cmd.AdminCommand;
import com.yapcore.playerdata.cmd.AuctionCommands;
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
import com.yapcore.playerdata.db.ClaimRepository;
import com.yapcore.playerdata.db.Database;
import com.yapcore.playerdata.db.HomesRepository;
import com.yapcore.playerdata.db.JobRepository;
import com.yapcore.playerdata.db.KitRepository;
import com.yapcore.playerdata.db.MailRepository;
import com.yapcore.playerdata.db.PlayerRepository;
import com.yapcore.playerdata.db.ShopRepository;
import com.yapcore.playerdata.db.WarpsRepository;
import com.yapcore.playerdata.economy.BalanceStore;
import com.yapcore.playerdata.economy.YaPEconomy;
import com.yapcore.playerdata.feature.JobListener;
import com.yapcore.playerdata.feature.ShopListener;
import com.yapcore.playerdata.gui.MenuListener;
import com.yapcore.playerdata.gui.Menus;
import com.yapcore.playerdata.sync.JoinQuitListener;
import com.yapcore.playerdata.sync.SessionLock;
import com.yapcore.playerdata.sync.SyncService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Cross-server player data + claims + fancy GUIs (first-party YaPcore).
 */
public final class PlayerDataPlugin extends JavaPlugin {

    private PlayerDataConfig config;
    private Database database;
    private SyncService sync;
    private YaPEconomy economy;
    private ClaimService claims;
    private Menus menus;

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
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PlayerRepository repository = new PlayerRepository(database, config);
        SessionLock locks = new SessionLock(repository, config);
        sync = new SyncService(this, config, repository, locks);
        sync.startAutosave();

        BalanceStore balances = new BalanceStore(sync, repository, getLogger());
        HomesRepository homes = new HomesRepository(database);
        WarpsRepository warps = new WarpsRepository(database);
        MailRepository mail = new MailRepository(database);
        KitRepository kits = new KitRepository(database);
        ShopRepository shops = new ShopRepository(database);
        JobRepository jobs = new JobRepository(database);
        AuctionRepository auctions = new AuctionRepository(database);
        ClaimRepository claimRepo = new ClaimRepository(database);

        claims = new ClaimService(this, config, claimRepo);
        claims.start();

        menus = new Menus(this, config, sync, balances, homes, warps, kits, jobs, auctions, mail, claims);

        getServer().getPluginManager().registerEvents(new JoinQuitListener(this, sync, mail), this);
        getServer().getPluginManager().registerEvents(new MenuListener(menus), this);
        getServer().getPluginManager().registerEvents(new ClaimListener(this, claims), this);

        BalanceCommands balanceCommands = new BalanceCommands(balances);
        bind("bal", balanceCommands, balanceCommands);
        bind("pay", balanceCommands, balanceCommands);

        AdminCommand admin = new AdminCommand(this, config, database, sync);
        bind("yapdata", admin, admin);

        MenuCommand menuCommand = new MenuCommand(menus, sync);
        bind("menu", menuCommand, menuCommand);

        HomeCommands homeCommands = new HomeCommands(config, homes, sync, menus);
        bind("sethome", homeCommands, homeCommands);
        bind("home", homeCommands, homeCommands);
        bind("delhome", homeCommands, homeCommands);
        bind("homes", homeCommands, homeCommands);

        WarpCommands warpCommands = new WarpCommands(config, warps, menus);
        bind("setwarp", warpCommands, warpCommands);
        bind("delwarp", warpCommands, warpCommands);
        bind("warp", warpCommands, warpCommands);
        bind("warps", warpCommands, warpCommands);

        KitCommands kitCommands = new KitCommands(config, kits, sync, menus);
        bind("kit", kitCommands, kitCommands);
        bind("kits", kitCommands, kitCommands);

        MailCommands mailCommands = new MailCommands(config, mail, sync, menus);
        bind("mail", mailCommands, mailCommands);

        ShopCommands shopCommands = new ShopCommands(config, shops, balances, sync);
        bind("shop", shopCommands, shopCommands);
        getServer().getPluginManager().registerEvents(new ShopListener(shopCommands), this);

        JobCommands jobCommands = new JobCommands(config, jobs, sync, menus);
        bind("jobs", jobCommands, jobCommands);
        getServer().getPluginManager().registerEvents(
                new JobListener(this, config, jobs, balances, sync), this);

        AuctionCommands auctionCommands = new AuctionCommands(config, auctions, balances, sync, menus);
        bind("ah", auctionCommands, auctionCommands);

        ClaimCommands claimCommands = new ClaimCommands(this, claims, sync, menus);
        bind("claim", claimCommands, claimCommands);

        if (config.syncEconomy() && Bukkit.getPluginManager().getPlugin("Vault") != null) {
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

        getLogger().info("YaPPlayerData 0.3 — server-id=" + config.serverId()
                + " profile=" + config.inventoryProfile()
                + " claims=" + config.claimsEnabled()
                + " kits=" + config.kits().size()
                + " jobs=" + config.jobs().size());
    }

    @Override
    public void onDisable() {
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
