package com.yapcore.playerdata.gui;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.PlayerDataPlugin;
import com.yapcore.playerdata.bag.BackpackService;
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
import com.yapcore.playerdata.sync.SyncService;
import com.yapcore.playerdata.util.Teleports;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Fancy inventory menus for hub + features.
 */
public final class Menus {

    final PlayerDataPlugin plugin;
    final PlayerDataConfig config;
    final SyncService sync;
    final BalanceStore balances;
    final HomesRepository homes;
    final WarpsRepository warps;
    final KitRepository kits;
    final JobRepository jobs;
    final AuctionRepository auctions;
    final MailRepository mail;
    BackpackService backpack;

    /** slot → auction id / home name / etc for click routing */
    final Map<UUID, Map<Integer, String>> clickMeta = new HashMap<>();
    /** When true, feature GUI Back returns to YaP Menu; false = NPC/command → Close. */
    private final Map<UUID, Boolean> openedFromHub = new ConcurrentHashMap<>();

    public Menus(PlayerDataPlugin plugin, PlayerDataConfig config, SyncService sync,
                 BalanceStore balances, HomesRepository homes, WarpsRepository warps,
                 KitRepository kits, JobRepository jobs, AuctionRepository auctions,
                 MailRepository mail) {
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
    }

    public void bindBackpack(BackpackService backpack) {
        this.backpack = backpack;
    }

    /** Next feature GUI was opened from YaP Menu (Back returns to hub). */
    public void markOpenedFromHub(Player player) {
        openedFromHub.put(player.getUniqueId(), true);
    }

    /** Next feature GUI was opened from NPC or /command (footer is Close). */
    public void markOpenedStandalone(Player player) {
        openedFromHub.put(player.getUniqueId(), false);
    }

    public boolean openedFromHub(Player player) {
        return Boolean.TRUE.equals(openedFromHub.get(player.getUniqueId()));
    }

    void placeNavButton(Inventory inv, int slot, Player player) {
        // Feature GUIs (kits/AH/mail/…) never link back to YaP Menu — NPC shops included.
        // Hub is only reachable via /menu.
        inv.setItem(slot, YapMenuHolder.icon(Material.BARRIER, NamedTextColor.RED, "Close"));
    }

    /** Close always dismisses. Never opens YaP Menu from feature GUIs. */
    boolean navBackOrClose(Player player, String name) {
        if ("Back".equals(name) || "Close".equals(name)) {
            player.closeInventory();
            return true;
        }
        return false;
    }

    final AuctionMenus auctionMenus = new AuctionMenus(this);
    final MailMenus mailMenus = new MailMenus(this);

    public void openHub(Player player) {
        if (!Perms.require(player, "yapdata.menu")) {
            return;
        }
        if (PlayerDataBedrockForms.tryOpenHub(this, player)) {
            return;
        }
        openHubInventory(player);
    }

    /** JE chest hub (also fallback when Bedrock UI is unavailable). */
    public void openHubInventory(Player player) {
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
        } else if (Bukkit.getPluginManager().getPlugin("YaPSkills") != null) {
            inv.setItem(25, YapMenuHolder.icon(Material.EXPERIENCE_BOTTLE, "Skills", "/skills · /stats"));
        }
        if (config.featureAuctions()) {
            inv.setItem(29, YapMenuHolder.icon(Material.GOLDEN_HORSE_ARMOR, "Auctions", "Click to open"));
        }
        if (config.featureMail()) {
            inv.setItem(31, YapMenuHolder.icon(Material.WRITABLE_BOOK, "Mail", "Click to open"));
        }
        if (Bukkit.getPluginManager().getPlugin("YaPClaims") != null
                && player.hasPermission("yapdata.claim")) {
            inv.setItem(33, YapMenuHolder.icon(Material.GOLDEN_SHOVEL, "Claims", "/claim"));
        }
        if (player.hasPermission("yapadmin.menu")
                && Bukkit.getPluginManager().getPlugin("YaPAdmin") != null) {
            inv.setItem(37, YapMenuHolder.icon(Material.COMMAND_BLOCK, NamedTextColor.RED,
                    "Staff", "Open admin super menu", "/yapadmin"));
        }
        inv.setItem(40, YapMenuHolder.icon(Material.BARRIER, NamedTextColor.RED, "Close"));
        player.openInventory(inv);
    }

    public void openHomes(Player player) {
        if (!Perms.require(player, "yapdata.home")) {
            return;
        }
        if (PlayerDataBedrockForms.tryOpenHomes(this, player)) {
            return;
        }
        openHomesInventory(player);
    }

    public void openHomesInventory(Player player) {
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
        placeNavButton(inv, 49, player);
        clickMeta.put(player.getUniqueId(), meta);
        player.openInventory(inv);
    }

    public void openWarps(Player player) {
        if (!Perms.require(player, "yapdata.warp")) {
            return;
        }
        if (PlayerDataBedrockForms.tryOpenWarps(this, player)) {
            return;
        }
        openWarpsInventory(player);
    }

    public void openWarpsInventory(Player player) {
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
        placeNavButton(inv, 49, player);
        clickMeta.put(player.getUniqueId(), meta);
        player.openInventory(inv);
    }

    public void openKits(Player player) {
        if (!Perms.require(player, "yapdata.kit")) {
            return;
        }
        if (PlayerDataBedrockForms.tryOpenKits(this, player)) {
            return;
        }
        openKitsInventory(player);
    }

    public void openKitsInventory(Player player) {
        YapMenuHolder holder = new YapMenuHolder(YapMenuHolder.Kind.KITS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Kits", NamedTextColor.YELLOW));
        holder.bind(inv);
        YapMenuHolder.fillBorder(inv);
        Map<Integer, String> meta = new HashMap<>();
        int slot = 10;
        for (var entry : config.kits().entrySet()) {
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 44) {
                break;
            }
            KitDef def = entry.getValue();
            boolean unlocked = Perms.hasKit(player, entry.getKey());
            Material icon = unlocked
                    ? (def.iconStack() != null ? def.iconStack().getType() : Material.CHEST)
                    : Material.BARRIER;
            String remain = "Ready";
            String uses = def.maxUses() > 0 ? "Uses left: ?" : "Unlimited uses";
            try {
                var last = kits.lastClaim(player.getUniqueId(), def.id());
                if (last.isPresent() && def.delaySeconds() > 0) {
                    long secs = java.time.Duration.between(java.time.Instant.now(),
                            last.get().plusSeconds(def.delaySeconds())).getSeconds();
                    if (secs > 0) {
                        remain = "Cooldown: " + CooldownFormat.formatSeconds(secs);
                        if (unlocked && def.hasExtraCost()) {
                            remain += " · buy $" + String.format("%.0f", def.extraCost());
                        }
                    }
                }
                int used = kits.uses(player.getUniqueId(), def.id());
                if (def.maxUses() > 0) {
                    uses = "Uses: " + Math.max(0, def.maxUses() - used) + "/" + def.maxUses();
                }
            } catch (Exception ignored) {
            }
            String cost = def.cost() > 0 ? "Cost: $" + String.format("%.2f", def.cost())
                    : (def.delaySeconds() > 0 ? "Free (" + CooldownFormat.formatSeconds(def.delaySeconds()) + " CD)" : "Free");
            if (def.hasExtraCost()) {
                cost += " · Extra: $" + String.format("%.2f", def.extraCost());
            }
            NamedTextColor color = unlocked ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY;
            String title = unlocked ? entry.getKey() : entry.getKey() + " [LOCKED]";
            List<String> lore = new ArrayList<>();
            if (!unlocked) {
                lore.add("Requires rank for yapdata.kit." + entry.getKey());
                lore.add("Shift-click to preview");
            } else {
                lore.add(remain);
                lore.add(uses);
                lore.add(cost);
                lore.add("Items: " + def.itemCount());
                lore.add("Click to claim · Shift: preview");
            }
            inv.setItem(slot, YapMenuHolder.icon(icon, color, title, lore.toArray(String[]::new)));
            meta.put(slot, entry.getKey());
            slot++;
        }
        placeNavButton(inv, 49, player);
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
                unlockedClaimLore(player, def)));
        clickMeta.put(player.getUniqueId(), java.util.Map.of(53, def.id()));
        player.openInventory(inv);
    }

    private static String[] unlockedClaimLore(Player player, KitDef def) {
        boolean unlocked = Perms.hasKit(player, def.id());
        if (!unlocked) {
            return new String[]{
                    "Locked for your rank",
                    "Needs yapdata.kit." + def.id(),
                    "Free daily + paid extras require rank"
            };
        }
        List<String> lore = new ArrayList<>();
        lore.add(def.cost() > 0 ? "Cost: $" + String.format("%.2f", def.cost()) : "Free (when ready)");
        if (def.hasExtraCost()) {
            lore.add("Extra while on CD: $" + String.format("%.2f", def.extraCost()));
        }
        lore.add("Delay: " + def.delaySeconds() + "s");
        return lore.toArray(String[]::new);
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
        placeNavButton(inv, 40, player);
        clickMeta.put(player.getUniqueId(), meta);
        player.openInventory(inv);
    }

    public void openAuctions(Player player) {
        auctionMenus.openBrowse(player);
    }

    public void openMail(Player player) {
        mailMenus.openInbox(player);
    }

    private final MenuClickHandler clicks = new MenuClickHandler(this);

    public boolean handleClick(Player player, YapMenuHolder holder, int slot, boolean shift) {
        return clicks.handleClick(player, holder, slot, shift);
    }

    public void clearMeta(Player player) {
        clickMeta.remove(player.getUniqueId());
    }
}