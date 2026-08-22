package com.yapcore.playerdata.gui;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.PlayerDataPlugin;
import com.yapcore.playerdata.claims.Claim;
import com.yapcore.playerdata.claims.ClaimService;
import com.yapcore.playerdata.claims.ClaimVisualizer;
import com.yapcore.playerdata.cmd.Perms;
import com.yapcore.playerdata.db.AuctionRepository;
import com.yapcore.playerdata.db.HomesRepository;
import com.yapcore.playerdata.db.JobRepository;
import com.yapcore.playerdata.db.KitRepository;
import com.yapcore.playerdata.db.LocationRow;
import com.yapcore.playerdata.db.MailRepository;
import com.yapcore.playerdata.db.WarpsRepository;
import com.yapcore.playerdata.economy.BalanceStore;
import com.yapcore.playerdata.sync.ItemSerializer;
import com.yapcore.playerdata.sync.SyncService;
import com.yapcore.playerdata.util.Teleports;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Fancy inventory menus for hub + features.
 */
public final class Menus {

    private final PlayerDataPlugin plugin;
    private final PlayerDataConfig config;
    private final SyncService sync;
    private final BalanceStore balances;
    private final HomesRepository homes;
    private final WarpsRepository warps;
    private final KitRepository kits;
    private final JobRepository jobs;
    private final AuctionRepository auctions;
    private final MailRepository mail;
    private final ClaimService claims;

    /** slot → auction id / home name / etc for click routing */
    private final Map<java.util.UUID, Map<Integer, String>> clickMeta = new HashMap<>();

    public Menus(PlayerDataPlugin plugin, PlayerDataConfig config, SyncService sync,
                 BalanceStore balances, HomesRepository homes, WarpsRepository warps,
                 KitRepository kits, JobRepository jobs, AuctionRepository auctions,
                 MailRepository mail, ClaimService claims) {
        this.plugin = plugin;
        this.config = config;
        this.sync = sync;
        this.balances = balances;
        this.homes = homes;
        this.warps = warps;
        this.kits = kits;
        this.jobs = jobs;
        this.auctions = auctions;
        this.mail = mail;
        this.claims = claims;
    }

    public void openHub(Player player) {
        if (!Perms.require(player, "yapdata.menu")) {
            return;
        }
        YapMenuHolder holder = new YapMenuHolder(YapMenuHolder.Kind.HUB);
        Inventory inv = Bukkit.createInventory(holder, 45, Component.text("YaP Menu", NamedTextColor.GOLD));
        holder.bind(inv);
        for (int i = 0; i < 45; i++) {
            inv.setItem(i, YapMenuHolder.filler());
        }
        if (config.economyEnabled()) {
            double bal = balances.getBalance(player.getUniqueId());
            inv.setItem(4, YapMenuHolder.icon(Material.GOLD_INGOT, NamedTextColor.GREEN,
                    "Balance", "$" + String.format("%.2f", bal), "Profile: " + config.inventoryProfile()));
        } else {
            inv.setItem(4, YapMenuHolder.icon(Material.PAPER, NamedTextColor.GRAY,
                    "Profile", "Economy off", "Profile: " + config.inventoryProfile()));
        }
        if (config.featureHomes()) {
            inv.setItem(19, YapMenuHolder.icon(Material.RED_BED, "Homes", "Click to open"));
        }
        if (config.featureWarps()) {
            inv.setItem(21, YapMenuHolder.icon(Material.ENDER_PEARL, "Warps", "Click to open"));
        }
        if (config.featureKits()) {
            inv.setItem(23, YapMenuHolder.icon(Material.CHEST, "Kits", "Click to open"));
        }
        if (config.featureJobs()) {
            inv.setItem(25, YapMenuHolder.icon(Material.IRON_PICKAXE, "Jobs", "Click to open"));
        }
        if (config.featureAuctions()) {
            inv.setItem(29, YapMenuHolder.icon(Material.GOLDEN_HORSE_ARMOR, "Auctions", "Click to open"));
        }
        if (config.featureMail()) {
            inv.setItem(31, YapMenuHolder.icon(Material.WRITABLE_BOOK, "Mail", "Click to open"));
        }
        if (config.featureClaims() && claims != null) {
            inv.setItem(33, YapMenuHolder.icon(Material.GOLDEN_SHOVEL, "Claims", "Click to open"));
        }
        inv.setItem(40, YapMenuHolder.icon(Material.BARRIER, NamedTextColor.RED, "Close"));
        player.openInventory(inv);
    }

    public void openHomes(Player player) {
        if (!Perms.require(player, "yapdata.home")) {
            return;
        }
        YapMenuHolder holder = new YapMenuHolder(YapMenuHolder.Kind.HOMES);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Homes", NamedTextColor.AQUA));
        holder.bind(inv);
        YapMenuHolder.fillBorder(inv);
        Map<Integer, String> meta = new HashMap<>();
        try {
            List<LocationRow> list = homes.list(player.getUniqueId());
            int slot = 10;
            for (LocationRow h : list) {
                while (slot % 9 == 0 || slot % 9 == 8) {
                    slot++;
                }
                if (slot >= 44) {
                    break;
                }
                inv.setItem(slot, YapMenuHolder.icon(Material.RED_BED, h.name(),
                        "Server: " + h.serverId(),
                        "Click to teleport",
                        "Shift-click to delete"));
                meta.put(slot, h.name());
                slot++;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "homes gui", e);
        }
        inv.setItem(49, YapMenuHolder.icon(Material.ARROW, "Back"));
        clickMeta.put(player.getUniqueId(), meta);
        player.openInventory(inv);
    }

    public void openWarps(Player player) {
        if (!Perms.require(player, "yapdata.warp")) {
            return;
        }
        YapMenuHolder holder = new YapMenuHolder(YapMenuHolder.Kind.WARPS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Warps", NamedTextColor.LIGHT_PURPLE));
        holder.bind(inv);
        YapMenuHolder.fillBorder(inv);
        Map<Integer, String> meta = new HashMap<>();
        try {
            int slot = 10;
            for (LocationRow w : warps.list()) {
                while (slot % 9 == 0 || slot % 9 == 8) {
                    slot++;
                }
                if (slot >= 44) {
                    break;
                }
                inv.setItem(slot, YapMenuHolder.icon(Material.ENDER_PEARL, w.name(),
                        "Server: " + w.serverId(), "Click to warp"));
                meta.put(slot, w.name());
                slot++;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "warps gui", e);
        }
        inv.setItem(49, YapMenuHolder.icon(Material.ARROW, "Back"));
        clickMeta.put(player.getUniqueId(), meta);
        player.openInventory(inv);
    }

    public void openKits(Player player) {
        if (!Perms.require(player, "yapdata.kit")) {
            return;
        }
        YapMenuHolder holder = new YapMenuHolder(YapMenuHolder.Kind.KITS);
        Inventory inv = Bukkit.createInventory(holder, 45, Component.text("Kits", NamedTextColor.YELLOW));
        holder.bind(inv);
        YapMenuHolder.fillBorder(inv);
        Map<Integer, String> meta = new HashMap<>();
        int slot = 10;
        for (var entry : config.kits().entrySet()) {
            if (!Perms.hasKit(player, entry.getKey())) {
                continue;
            }
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 35) {
                break;
            }
            Material icon = entry.getValue().items().isEmpty()
                    ? Material.CHEST
                    : entry.getValue().items().get(0).getType();
            inv.setItem(slot, YapMenuHolder.icon(icon, entry.getKey(),
                    "Cooldown: " + entry.getValue().delaySeconds() + "s",
                    "Items: " + entry.getValue().items().size(),
                    "Click to claim"));
            meta.put(slot, entry.getKey());
            slot++;
        }
        inv.setItem(40, YapMenuHolder.icon(Material.ARROW, "Back"));
        clickMeta.put(player.getUniqueId(), meta);
        player.openInventory(inv);
    }

    public void openJobs(Player player) {
        if (!config.featureJobs()) {
            player.sendMessage("§cJobs are disabled.");
            return;
        }
        if (!Perms.require(player, "yapdata.jobs")) {
            return;
        }
        YapMenuHolder holder = new YapMenuHolder(YapMenuHolder.Kind.JOBS);
        Inventory inv = Bukkit.createInventory(holder, 45, Component.text("Jobs", NamedTextColor.GREEN));
        holder.bind(inv);
        YapMenuHolder.fillBorder(inv);
        Map<Integer, String> meta = new HashMap<>();
        try {
            var joined = jobs.list(player.getUniqueId());
            java.util.Set<String> active = new java.util.HashSet<>();
            for (var p : joined) {
                active.add(p.job());
            }
            int slot = 10;
            for (var entry : config.jobs().entrySet()) {
                if (!Perms.hasJob(player, entry.getKey()) && !active.contains(entry.getKey())) {
                    continue;
                }
                while (slot % 9 == 0 || slot % 9 == 8) {
                    slot++;
                }
                if (slot >= 35) {
                    break;
                }
                boolean on = active.contains(entry.getKey());
                inv.setItem(slot, YapMenuHolder.icon(
                        on ? Material.LIME_DYE : Material.GRAY_DYE,
                        entry.getValue().display(),
                        on ? "Joined — click to leave" : "Click to join",
                        "Break pays: " + entry.getValue().breakPays().size() + " blocks"));
                meta.put(slot, (on ? "leave:" : "join:") + entry.getKey());
                slot++;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "jobs gui", e);
        }
        inv.setItem(40, YapMenuHolder.icon(Material.ARROW, "Back"));
        clickMeta.put(player.getUniqueId(), meta);
        player.openInventory(inv);
    }

    public void openAuctions(Player player) {
        if (!config.featureAuctions()) {
            player.sendMessage("§cAuctions are disabled.");
            return;
        }
        if (!Perms.require(player, "yapdata.ah")) {
            return;
        }
        YapMenuHolder holder = new YapMenuHolder(YapMenuHolder.Kind.AUCTIONS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Auction House", NamedTextColor.GOLD));
        holder.bind(inv);
        YapMenuHolder.fillBorder(inv);
        Map<Integer, String> meta = new HashMap<>();
        try {
            int slot = 10;
            for (var a : auctions.listActive(28)) {
                while (slot % 9 == 0 || slot % 9 == 8) {
                    slot++;
                }
                if (slot >= 44) {
                    break;
                }
                ItemStack[] items = ItemSerializer.deserialize(a.itemBlob(), 1);
                ItemStack display = items.length > 0 && items[0] != null
                        ? items[0].clone()
                        : YapMenuHolder.icon(Material.PAPER, "#" + a.id());
                display.editMeta(m -> {
                    var lore = m.lore() != null ? new java.util.ArrayList<>(m.lore()) : new java.util.ArrayList<Component>();
                    lore.add(Component.text("Price: $" + String.format("%.2f", a.price()), NamedTextColor.GREEN));
                    lore.add(Component.text("Seller: " + a.sellerName(), NamedTextColor.GRAY));
                    lore.add(Component.text("Click to buy #" + a.id(), NamedTextColor.YELLOW));
                    m.lore(lore);
                });
                inv.setItem(slot, display);
                meta.put(slot, "buy:" + a.id());
                slot++;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "ah gui", e);
        }
        inv.setItem(49, YapMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(45, YapMenuHolder.icon(Material.EMERALD, NamedTextColor.GREEN,
                "Sell held item", "Use /ah sell <price>"));
        clickMeta.put(player.getUniqueId(), meta);
        player.openInventory(inv);
    }

    public void openMail(Player player) {
        if (!Perms.require(player, "yapdata.mail")) {
            return;
        }
        YapMenuHolder holder = new YapMenuHolder(YapMenuHolder.Kind.MAIL);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Mail", NamedTextColor.WHITE));
        holder.bind(inv);
        YapMenuHolder.fillBorder(inv);
        try {
            int slot = 10;
            for (var m : mail.list(player.getUniqueId(), config.mailMaxUnread())) {
                while (slot % 9 == 0 || slot % 9 == 8) {
                    slot++;
                }
                if (slot >= 44) {
                    break;
                }
                inv.setItem(slot, YapMenuHolder.icon(
                        m.read() ? Material.PAPER : Material.MAP,
                        "#" + m.id() + " from " + m.fromName(),
                        m.message()));
                slot++;
            }
            mail.markAllRead(player.getUniqueId());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "mail gui", e);
        }
        inv.setItem(49, YapMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(53, YapMenuHolder.icon(Material.LAVA_BUCKET, NamedTextColor.RED, "Clear all"));
        player.openInventory(inv);
    }

    public void openClaims(Player player) {
        if (!config.featureClaims() || claims == null) {
            player.sendMessage("§cClaims are disabled.");
            return;
        }
        if (!Perms.require(player, "yapdata.claim")) {
            return;
        }
        YapMenuHolder holder = new YapMenuHolder(YapMenuHolder.Kind.CLAIMS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Claims", NamedTextColor.GREEN));
        holder.bind(inv);
        YapMenuHolder.fillBorder(inv);
        Map<Integer, String> meta = new HashMap<>();
        try {
            int blocks = claims.repo().getBlocks(player.getUniqueId(), config.claimsStartingBlocks());
            inv.setItem(4, YapMenuHolder.icon(Material.GOLDEN_SHOVEL, "Claim blocks",
                    String.valueOf(blocks),
                    "Tool: golden shovel (2 corners)",
                    "Inspect: stick"));
            int slot = 10;
            for (Claim c : claims.repo().listOwned(player.getUniqueId())) {
                while (slot % 9 == 0 || slot % 9 == 8) {
                    slot++;
                }
                if (slot >= 44) {
                    break;
                }
                inv.setItem(slot, YapMenuHolder.icon(
                        c.isSubdivision() ? Material.OAK_FENCE : Material.GRASS_BLOCK,
                        (c.isSubdivision() ? "Sub #" : "#") + c.id() + " " + c.name(),
                        "Server: " + c.serverId(),
                        "Area: " + c.area()
                                + (c.isSubdivision() ? " · parent #" + c.parentId() : ""),
                        c.isSubdivision() ? "Subdivision" : ("Tax: $" + String.format("%.2f", c.taxDue())
                                + (c.taxFrozen() ? " FROZEN" : "")),
                        "Click: visualize · Shift: abandon"));
                meta.put(slot, String.valueOf(c.id()));
                slot++;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "claims gui", e);
        }
        inv.setItem(49, YapMenuHolder.icon(Material.ARROW, "Back"));
        clickMeta.put(player.getUniqueId(), meta);
        player.openInventory(inv);
    }

    public boolean handleClick(Player player, YapMenuHolder holder, int slot, boolean shift) {
        if (!sync.isReady(player.getUniqueId()) && holder.kind() != YapMenuHolder.Kind.HUB) {
            player.sendMessage("§cStill loading…");
            return true;
        }
        ItemStack clicked = holder.getInventory().getItem(slot);
        if (clicked == null || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return true;
        }
        String name = plainName(clicked);

        try {
            return switch (holder.kind()) {
                case HUB -> hubClick(player, name);
                case HOMES -> homesClick(player, slot, shift, name);
                case WARPS -> warpsClick(player, slot, name);
                case KITS -> kitsClick(player, slot, name);
                case JOBS -> jobsClick(player, slot, name);
                case AUCTIONS -> auctionsClick(player, slot, name);
                case MAIL -> mailClick(player, name);
                case CLAIMS -> claimsClick(player, slot, shift, name);
                case NPC_TRADER -> {
                    Long traderId = holder.context();
                    if (traderId != null) {
                        // routed via NpcTraderService from MenuListener
                        yield false;
                    }
                    yield true;
                }
                default -> true;
            };
        } catch (Exception e) {
            player.sendMessage("§cError: " + e.getMessage());
            plugin.getLogger().log(Level.WARNING, "menu click", e);
            return true;
        }
    }

    private boolean hubClick(Player player, String name) {
        return switch (name) {
            case "Homes" -> {
                if (config.featureHomes()) {
                    openHomes(player);
                }
                yield true;
            }
            case "Warps" -> {
                if (config.featureWarps()) {
                    openWarps(player);
                }
                yield true;
            }
            case "Kits" -> {
                if (config.featureKits()) {
                    openKits(player);
                }
                yield true;
            }
            case "Jobs" -> {
                if (config.featureJobs()) {
                    openJobs(player);
                }
                yield true;
            }
            case "Auctions" -> {
                if (config.featureAuctions()) {
                    openAuctions(player);
                }
                yield true;
            }
            case "Mail" -> {
                if (config.featureMail()) {
                    openMail(player);
                }
                yield true;
            }
            case "Claims" -> {
                if (config.featureClaims()) {
                    openClaims(player);
                }
                yield true;
            }
            case "Close" -> {
                player.closeInventory();
                yield true;
            }
            default -> true;
        };
    }

    private boolean homesClick(Player player, int slot, boolean shift, String name) throws Exception {
        if ("Back".equals(name)) {
            openHub(player);
            return true;
        }
        Map<Integer, String> meta = clickMeta.getOrDefault(player.getUniqueId(), Map.of());
        String home = meta.get(slot);
        if (home == null) {
            return true;
        }
        if (shift) {
            homes.delete(player.getUniqueId(), home);
            player.sendMessage("§aDeleted home §f" + home);
            openHomes(player);
            return true;
        }
        var opt = homes.get(player.getUniqueId(), home);
        player.closeInventory();
        if (opt.isPresent() && Teleports.tryTeleport(player, opt.get(), config.serverId())) {
            player.sendMessage("§aTeleported to §f" + home);
        }
        return true;
    }

    private boolean warpsClick(Player player, int slot, String name) throws Exception {
        if ("Back".equals(name)) {
            openHub(player);
            return true;
        }
        Map<Integer, String> meta = clickMeta.getOrDefault(player.getUniqueId(), Map.of());
        String warp = meta.get(slot);
        if (warp == null) {
            return true;
        }
        var opt = warps.get(warp);
        player.closeInventory();
        if (opt.isPresent() && Teleports.tryTeleport(player, opt.get(), config.serverId())) {
            player.sendMessage("§aWarped to §f" + warp);
        }
        return true;
    }

    private boolean kitsClick(Player player, int slot, String name) throws Exception {
        if ("Back".equals(name)) {
            openHub(player);
            return true;
        }
        Map<Integer, String> meta = clickMeta.getOrDefault(player.getUniqueId(), Map.of());
        String kit = meta.get(slot);
        if (kit == null) {
            return true;
        }
        player.closeInventory();
        player.performCommand("kit " + kit);
        return true;
    }

    private boolean jobsClick(Player player, int slot, String name) throws Exception {
        if ("Back".equals(name)) {
            openHub(player);
            return true;
        }
        Map<Integer, String> meta = clickMeta.getOrDefault(player.getUniqueId(), Map.of());
        String action = meta.get(slot);
        if (action == null) {
            return true;
        }
        if (action.startsWith("join:")) {
            String id = action.substring(5);
            if (!Perms.hasJob(player, id)) {
                player.sendMessage("§cNo permission for that job.");
                return true;
            }
            jobs.join(player.getUniqueId(), id);
            player.sendMessage("§aJoined §f" + id);
        } else if (action.startsWith("leave:")) {
            jobs.leave(player.getUniqueId(), action.substring(6));
            player.sendMessage("§aLeft §f" + action.substring(6));
        }
        openJobs(player);
        return true;
    }

    private boolean auctionsClick(Player player, int slot, String name) throws Exception {
        if ("Back".equals(name)) {
            openHub(player);
            return true;
        }
        if (name.startsWith("Sell")) {
            player.closeInventory();
            player.sendMessage("§7Hold an item and use §f/ah sell <price>");
            return true;
        }
        Map<Integer, String> meta = clickMeta.getOrDefault(player.getUniqueId(), Map.of());
        String action = meta.get(slot);
        if (action == null || !action.startsWith("buy:")) {
            return true;
        }
        long id = Long.parseLong(action.substring(4));
        player.closeInventory();
        player.performCommand("ah buy " + id);
        return true;
    }

    private boolean mailClick(Player player, String name) throws Exception {
        if ("Back".equals(name)) {
            openHub(player);
            return true;
        }
        if ("Clear all".equals(name)) {
            mail.clear(player.getUniqueId());
            player.sendMessage("§aMail cleared.");
            openMail(player);
        }
        return true;
    }

    private boolean claimsClick(Player player, int slot, boolean shift, String name) throws Exception {
        if (claims == null) {
            return true;
        }
        if ("Back".equals(name)) {
            openHub(player);
            return true;
        }
        Map<Integer, String> meta = clickMeta.getOrDefault(player.getUniqueId(), Map.of());
        String idStr = meta.get(slot);
        if (idStr == null) {
            return true;
        }
        long id = Long.parseLong(idStr);
        var opt = claims.repo().get(id);
        if (opt.isEmpty()) {
            openClaims(player);
            return true;
        }
        Claim c = opt.get();
        if (shift) {
            if (claims.abandon(player, c)) {
                player.sendMessage("§aAbandoned claim §f#" + id);
            } else {
                player.sendMessage("§cCannot abandon.");
            }
            openClaims(player);
            return true;
        }
        player.closeInventory();
        if (c.serverId().equals(config.serverId()) && player.getWorld().getName().equals(c.world())) {
            ClaimVisualizer.show(plugin, player, c, config.claimsVisualSeconds());
            player.sendMessage("§aShowing claim §f#" + id);
        } else {
            player.sendMessage("§cClaim is on §f" + c.serverId() + "/" + c.world());
        }
        return true;
    }

    private static String plainName(ItemStack stack) {
        if (stack.getItemMeta() == null || stack.getItemMeta().displayName() == null) {
            return "";
        }
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(stack.getItemMeta().displayName());
    }

    public void clearMeta(Player player) {
        clickMeta.remove(player.getUniqueId());
    }
}
