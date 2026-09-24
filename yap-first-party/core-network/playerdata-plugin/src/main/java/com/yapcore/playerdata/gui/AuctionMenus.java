package com.yapcore.playerdata.gui;

import com.yapcore.playerdata.cmd.Perms;
import com.yapcore.playerdata.db.AuctionRepository;
import com.yapcore.playerdata.sync.ItemSerializer;
import com.yapcore.sched.YapSched;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Auction House browse / sell / my-listings GUIs. */
final class AuctionMenus {

    private static final double[] SELL_PRICES = {
            10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000, 25000, 50000
    };

    private final Menus menus;
    private final Set<UUID> customPricePrompt = ConcurrentHashMap.newKeySet();

    AuctionMenus(Menus menus) {
        this.menus = menus;
    }

    void openBrowse(Player player) {
        if (!menus.config.featureAuctions()) {
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
            for (var a : menus.auctions.listActive(28)) {
                while (slot % 9 == 0 || slot % 9 == 8) {
                    slot++;
                }
                if (slot >= 44) {
                    break;
                }
                ItemStack display = listingIcon(a, false);
                inv.setItem(slot, display);
                meta.put(slot, "buy:" + a.id());
                slot++;
            }
            if (meta.isEmpty()) {
                inv.setItem(22, YapMenuHolder.icon(Material.BARRIER, NamedTextColor.GRAY,
                        "No listings", "Sell held item to create one"));
            }
        } catch (Exception e) {
            menus.plugin.getLogger().log(Level.WARNING, "ah gui", e);
        }
        inv.setItem(45, YapMenuHolder.icon(Material.EMERALD, NamedTextColor.GREEN,
                "Sell held item", "Open price picker", "Hold the item in your main hand"));
        inv.setItem(46, YapMenuHolder.icon(Material.CHEST, NamedTextColor.AQUA,
                "My listings", "Cancel your active sales"));
        inv.setItem(47, YapMenuHolder.icon(Material.SUNFLOWER, NamedTextColor.YELLOW,
                "Refresh", "Reload the auction board"));
        menus.placeNavButton(inv, 49, player);
        menus.clickMeta.put(player.getUniqueId(), meta);
        player.openInventory(inv);
    }

    void openSell(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.sendMessage("§cHold an item in your main hand to list it.");
            openBrowse(player);
            return;
        }
        YapMenuHolder holder = new YapMenuHolder(YapMenuHolder.Kind.AUCTIONS_SELL);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("AH · Sell", NamedTextColor.GREEN));
        holder.bind(inv);
        YapMenuHolder.fillBorder(inv);
        ItemStack preview = hand.clone();
        inv.setItem(13, preview);
        inv.setItem(4, YapMenuHolder.icon(Material.NAME_TAG, NamedTextColor.YELLOW,
                "Pick a price",
                "Lists your held item",
                "Expires in " + menus.config.auctionHours() + "h",
                menus.config.auctionFeePercent() > 0
                        ? "Fee: " + menus.config.auctionFeePercent() + "%"
                        : "No listing fee"));
        Map<Integer, String> meta = new HashMap<>();
        int slot = 19;
        for (double price : SELL_PRICES) {
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 44) {
                break;
            }
            inv.setItem(slot, YapMenuHolder.icon(Material.GOLD_INGOT, NamedTextColor.GOLD,
                    "$" + formatMoney(price),
                    "Click to list for $" + formatMoney(price)));
            meta.put(slot, "price:" + price);
            slot++;
        }
        inv.setItem(48, YapMenuHolder.icon(Material.ARROW, "Back to board"));
        inv.setItem(50, YapMenuHolder.icon(Material.PAPER, NamedTextColor.AQUA,
                "Custom price", "Type any price in chat", "Type cancel to abort"));
        menus.clickMeta.put(player.getUniqueId(), meta);
        player.openInventory(inv);
    }

    void openMine(Player player) {
        YapMenuHolder holder = new YapMenuHolder(YapMenuHolder.Kind.AUCTIONS_MINE);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("AH · My listings", NamedTextColor.AQUA));
        holder.bind(inv);
        YapMenuHolder.fillBorder(inv);
        Map<Integer, String> meta = new HashMap<>();
        try {
            List<AuctionRepository.Listing> mine = menus.auctions.listBySeller(player.getUniqueId(), 28);
            if (mine.isEmpty()) {
                inv.setItem(22, YapMenuHolder.icon(Material.BARRIER, NamedTextColor.GRAY,
                        "No active listings", "Sell held item from the main board"));
            }
            int slot = 10;
            for (var a : mine) {
                while (slot % 9 == 0 || slot % 9 == 8) {
                    slot++;
                }
                if (slot >= 44) {
                    break;
                }
                inv.setItem(slot, listingIcon(a, true));
                meta.put(slot, "cancel:" + a.id());
                slot++;
            }
        } catch (Exception e) {
            menus.plugin.getLogger().log(Level.WARNING, "ah mine gui", e);
        }
        inv.setItem(48, YapMenuHolder.icon(Material.ARROW, "Back to board"));
        inv.setItem(50, YapMenuHolder.icon(Material.EMERALD, NamedTextColor.GREEN,
                "Sell held item", "Open price picker"));
        menus.clickMeta.put(player.getUniqueId(), meta);
        player.openInventory(inv);
    }

    boolean handleBrowse(Player player, int slot, String name) throws Exception {
        if (menus.navBackOrClose(player, name)) {
            return true;
        }
        if ("Sell held item".equals(name)) {
            openSell(player);
            return true;
        }
        if ("My listings".equals(name)) {
            openMine(player);
            return true;
        }
        if ("Refresh".equals(name)) {
            openBrowse(player);
            return true;
        }
        Map<Integer, String> meta = menus.clickMeta.getOrDefault(player.getUniqueId(), Map.of());
        String action = meta.get(slot);
        if (action != null && action.startsWith("buy:")) {
            long id = Long.parseLong(action.substring(4));
            player.closeInventory();
            player.performCommand("ah buy " + id);
            YapSched.entity(menus.plugin, player, () -> openBrowse(player));
            return true;
        }
        return true;
    }

    boolean handleSell(Player player, int slot, String name) throws Exception {
        if ("Back to board".equals(name)) {
            openBrowse(player);
            return true;
        }
        if (name != null && name.startsWith("Custom")) {
            beginCustomPrice(player);
            return true;
        }
        Map<Integer, String> meta = menus.clickMeta.getOrDefault(player.getUniqueId(), Map.of());
        String action = meta.get(slot);
        if (action == null || !action.startsWith("price:")) {
            return true;
        }
        double price = Double.parseDouble(action.substring(6));
        listHeld(player, price);
        return true;
    }

    boolean handleMine(Player player, int slot, String name) throws Exception {
        if ("Back to board".equals(name)) {
            openBrowse(player);
            return true;
        }
        if ("Sell held item".equals(name)) {
            openSell(player);
            return true;
        }
        Map<Integer, String> meta = menus.clickMeta.getOrDefault(player.getUniqueId(), Map.of());
        String action = meta.get(slot);
        if (action != null && action.startsWith("cancel:")) {
            long id = Long.parseLong(action.substring(7));
            player.closeInventory();
            player.performCommand("ah cancel " + id);
            YapSched.entity(menus.plugin, player, () -> openMine(player));
            return true;
        }
        return true;
    }

    /** @return true if chat was consumed as an AH custom-price prompt */
    boolean handleChat(Player player, String raw) {
        if (!customPricePrompt.contains(player.getUniqueId())) {
            return false;
        }
        String text = raw.trim();
        YapSched.entity(menus.plugin, player, () -> {
            if (!customPricePrompt.contains(player.getUniqueId())) {
                return;
            }
            if (text.equalsIgnoreCase("cancel") || text.equalsIgnoreCase("c")) {
                customPricePrompt.remove(player.getUniqueId());
                player.sendMessage("§7Listing cancelled.");
                openBrowse(player);
                return;
            }
            double price;
            try {
                price = Double.parseDouble(text.replace("$", "").replace(",", ""));
            } catch (NumberFormatException e) {
                player.sendMessage("§cEnter a number price, or §fcancel§c.");
                return;
            }
            if (price <= 0) {
                player.sendMessage("§cPrice must be positive.");
                return;
            }
            customPricePrompt.remove(player.getUniqueId());
            try {
                listHeld(player, price);
            } catch (Exception e) {
                menus.plugin.getLogger().log(Level.WARNING, "ah custom sell", e);
                player.sendMessage("§cCould not list item.");
                openBrowse(player);
            }
        });
        return true;
    }

    void clearPending(Player player) {
        customPricePrompt.remove(player.getUniqueId());
    }

    private void beginCustomPrice(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.sendMessage("§cHold an item in your main hand first.");
            openBrowse(player);
            return;
        }
        player.closeInventory();
        customPricePrompt.add(player.getUniqueId());
        player.sendMessage("§7Type the sell price in chat (example §f100§7), or §fcancel§7.");
    }

    private void listHeld(Player player, double price) throws Exception {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.sendMessage("§cHold an item in your main hand to list it.");
            openBrowse(player);
            return;
        }
        ItemStack clone = hand.clone();
        player.getInventory().setItemInMainHand(null);
        byte[] blob = ItemSerializer.serialize(new ItemStack[]{clone});
        Instant expires = Instant.now().plusSeconds(menus.config.auctionHours() * 3600L);
        long id = menus.auctions.create(player.getUniqueId(), player.getName(), price, blob, expires);
        player.sendMessage("§aListed auction §f#" + id + " §afor §f$" + formatMoney(price));
        openBrowse(player);
    }

    private static ItemStack listingIcon(AuctionRepository.Listing a, boolean mine) {
        ItemStack[] items = ItemSerializer.deserialize(a.itemBlob(), 1);
        ItemStack display = items.length > 0 && items[0] != null
                ? items[0].clone()
                : YapMenuHolder.icon(Material.PAPER, "#" + a.id());
        display.editMeta(m -> {
            var lore = m.lore() != null ? new java.util.ArrayList<>(m.lore()) : new java.util.ArrayList<Component>();
            lore.add(Component.text("Price: $" + formatMoney(a.price()), NamedTextColor.GREEN));
            lore.add(Component.text("Seller: " + a.sellerName(), NamedTextColor.GRAY));
            lore.add(Component.text("#" + a.id(), NamedTextColor.DARK_GRAY));
            if (mine) {
                lore.add(Component.text("Click to cancel & return item", NamedTextColor.RED));
            } else {
                lore.add(Component.text("Click to buy", NamedTextColor.YELLOW));
            }
            m.lore(lore);
        });
        return display;
    }

    private static String formatMoney(double price) {
        if (price == Math.rint(price)) {
            return String.format("%.0f", price);
        }
        return String.format("%.2f", price);
    }
}
