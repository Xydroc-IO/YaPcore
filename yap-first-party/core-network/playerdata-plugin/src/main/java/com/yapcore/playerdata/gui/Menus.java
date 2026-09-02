package com.yapcore.playerdata.gui;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.PlayerDataPlugin;
import com.yapcore.playerdata.bag.BackpackService;
import com.yapcore.playerdata.claims.Claim;
import com.yapcore.playerdata.claims.ClaimService;
import com.yapcore.playerdata.claims.ClaimVisualizer;
import com.yapcore.playerdata.cmd.Perms;
import com.yapcore.playerdata.db.AuctionRepository;
import com.yapcore.playerdata.db.HomesRepository;
import com.yapcore.playerdata.db.JobRepository;
import com.yapcore.playerdata.db.KitRepository;
import com.yapcore.playerdata.kit.CooldownFormat;
import com.yapcore.playerdata.kit.KitDef;
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

    final PlayerDataPlugin plugin;
    final PlayerDataConfig config;
    final SyncService sync;
    private final BalanceStore balances;
    final HomesRepository homes;
    final WarpsRepository warps;
    final KitRepository kits;
    final JobRepository jobs;
    final AuctionRepository auctions;
    final MailRepository mail;
    final ClaimService claims;
    BackpackService backpack;

    /** slot → auction id / home name / etc for click routing */
    final Map<java.util.UUID, Map<Integer, String>> clickMeta = new HashMap<>();

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

    public void bindBackpack(BackpackService backpack) {
        this.backpack = backpack;
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
        if (config.featureBackpack() && backpack != null && player.hasPermission("yapdata.bag")) {
            int pages = backpack.pagesFor(player);
            inv.setItem(13, YapMenuHolder.icon(Material.BUNDLE, NamedTextColor.GOLD,
                    "Bag", pages + (pages == 1 ? " page" : " pages"), "Click to open · /bag"));
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
            KitDef def = entry.getValue();
            Material icon = def.iconStack() != null ? def.iconStack().getType() : Material.CHEST;
            String remain = "Ready";
            String uses = def.maxUses() > 0 ? "Uses left: ?" : "Unlimited uses";
            try {
                var last = kits.lastClaim(player.getUniqueId(), def.id());
                if (last.isPresent() && def.delaySeconds() > 0) {
                    long secs = java.time.Duration.between(java.time.Instant.now(),
                            last.get().plusSeconds(def.delaySeconds())).getSeconds();
                    if (secs > 0) {
                        remain = "Cooldown: " + CooldownFormat.formatSeconds(secs);
                    }
                }
                int used = kits.uses(player.getUniqueId(), def.id());
                if (def.maxUses() > 0) {
                    uses = "Uses: " + Math.max(0, def.maxUses() - used) + "/" + def.maxUses();
                }
            } catch (Exception ignored) {
            }
            String cost = def.cost() > 0 ? "Cost: $" + String.format("%.2f", def.cost()) : "Free";
            inv.setItem(slot, YapMenuHolder.icon(icon, entry.getKey(),
                    remain, uses, cost, "Items: " + def.itemCount(),
                    "Click to claim · Shift: preview"));
            meta.put(slot, entry.getKey());
            slot++;
        }
        inv.setItem(40, YapMenuHolder.icon(Material.ARROW, "Back"));
        clickMeta.put(player.getUniqueId(), meta);
        player.openInventory(inv);
    }

    public void openKitPreview(Player player, String kitId) {
        KitDef def = config.kits().get(kitId.toLowerCase());
        if (def == null) {
            player.sendMessage("§cUnknown kit.");
            return;
        }
        YapMenuHolder holder = new YapMenuHolder(YapMenuHolder.Kind.KIT_PREVIEW, kitId);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Kit: " + def.id(), NamedTextColor.YELLOW));
        holder.bind(inv);
        int slot = 0;
        if (def.helmet() != null) {
            inv.setItem(slot++, def.helmet().clone());
        }
        if (def.chestplate() != null) {
            inv.setItem(slot++, def.chestplate().clone());
        }
        if (def.leggings() != null) {
            inv.setItem(slot++, def.leggings().clone());
        }
        if (def.boots() != null) {
            inv.setItem(slot++, def.boots().clone());
        }
        if (def.offhand() != null) {
            inv.setItem(slot++, def.offhand().clone());
        }
        for (ItemStack stack : def.items()) {
            if (slot >= 45) {
                break;
            }
            if (stack != null) {
                inv.setItem(slot++, stack.clone());
            }
        }
        inv.setItem(49, YapMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(53, YapMenuHolder.icon(Material.CHEST, NamedTextColor.GREEN, "Claim",
                def.cost() > 0 ? "Cost: $" + String.format("%.2f", def.cost()) : "Free",
                "Delay: " + def.delaySeconds() + "s"));
        clickMeta.put(player.getUniqueId(), java.util.Map.of(53, def.id()));
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

    private final MenuClickHandler clicks = new MenuClickHandler(this);

    public boolean handleClick(Player player, YapMenuHolder holder, int slot, boolean shift) {
        return clicks.handleClick(player, holder, slot, shift);
    }

    public void clearMeta(Player player) {
        clickMeta.remove(player.getUniqueId());
    }
}