package com.yapcore.playerdata.cmd;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.db.AuctionRepository;
import com.yapcore.playerdata.economy.BalanceStore;
import com.yapcore.playerdata.sync.ItemSerializer;
import com.yapcore.playerdata.sync.SyncService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

public final class AuctionCommands implements CommandExecutor, TabCompleter {
    private final PlayerDataConfig config;
    private final AuctionRepository auctions;
    private final BalanceStore balances;
    private final SyncService sync;

    public AuctionCommands(PlayerDataConfig config, AuctionRepository auctions,
                           BalanceStore balances, SyncService sync) {
        this.config = config;
        this.auctions = auctions;
        this.balances = balances;
        this.sync = sync;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!sync.isReady(player.getUniqueId())) {
            player.sendMessage("§cStill loading your data…");
            return true;
        }
        try {
            if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
                var list = auctions.listActive(20);
                if (list.isEmpty()) {
                    player.sendMessage("§7No active auctions. §f/ah sell <price>");
                    return true;
                }
                for (var a : list) {
                    ItemStack[] items = ItemSerializer.deserialize(a.itemBlob(), 1);
                    String name = items.length > 0 && items[0] != null
                            ? items[0].getAmount() + "x " + items[0].getType()
                            : "item";
                    player.sendMessage("§e#" + a.id() + " §f" + name
                            + " §a$" + String.format("%.2f", a.price())
                            + " §7by " + a.sellerName());
                }
                player.sendMessage("§7Buy with §f/ah buy <id>");
                return true;
            }
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("sell") && args.length >= 2) {
                double price = Double.parseDouble(args[1]);
                if (price <= 0) {
                    player.sendMessage("§cInvalid price.");
                    return true;
                }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    player.sendMessage("§cHold an item to sell.");
                    return true;
                }
                ItemStack clone = hand.clone();
                player.getInventory().setItemInMainHand(null);
                byte[] blob = ItemSerializer.serialize(new ItemStack[]{clone});
                Instant expires = Instant.now().plusSeconds(config.auctionHours() * 3600L);
                long id = auctions.create(player.getUniqueId(), player.getName(), price, blob, expires);
                player.sendMessage("§aListed auction §f#" + id + " §afor §f$" + String.format("%.2f", price));
                return true;
            }
            if (sub.equals("buy") && args.length >= 2) {
                long id = Long.parseLong(args[1]);
                var opt = auctions.get(id);
                if (opt.isEmpty() || opt.get().expires().isBefore(Instant.now())) {
                    player.sendMessage("§cListing not found or expired.");
                    return true;
                }
                var listing = opt.get();
                if (listing.seller().equals(player.getUniqueId())) {
                    player.sendMessage("§cCannot buy your own listing.");
                    return true;
                }
                if (balances.getBalance(player.getUniqueId()) < listing.price()) {
                    player.sendMessage("§cInsufficient funds.");
                    return true;
                }
                if (!auctions.delete(id)) {
                    player.sendMessage("§cListing already sold.");
                    return true;
                }
                double fee = listing.price() * (config.auctionFeePercent() / 100.0);
                double sellerGets = listing.price() - fee;
                balances.setBalance(player.getUniqueId(),
                        balances.getBalance(player.getUniqueId()) - listing.price());
                balances.setBalance(listing.seller(),
                        balances.getBalance(listing.seller()) + sellerGets);
                ItemStack[] items = ItemSerializer.deserialize(listing.itemBlob(), 1);
                for (ItemStack stack : items) {
                    if (stack != null) {
                        player.getInventory().addItem(stack);
                    }
                }
                player.sendMessage("§aPurchased auction §f#" + id);
                return true;
            }
            if (sub.equals("cancel") && args.length >= 2) {
                long id = Long.parseLong(args[1]);
                var opt = auctions.get(id);
                if (opt.isEmpty()) {
                    player.sendMessage("§cListing not found.");
                    return true;
                }
                if (!opt.get().seller().equals(player.getUniqueId()) && !player.hasPermission("yapdata.admin")) {
                    player.sendMessage("§cNot your listing.");
                    return true;
                }
                auctions.delete(id);
                ItemStack[] items = ItemSerializer.deserialize(opt.get().itemBlob(), 1);
                for (ItemStack stack : items) {
                    if (stack != null) {
                        player.getInventory().addItem(stack);
                    }
                }
                player.sendMessage("§aCancelled auction §f#" + id);
                return true;
            }
            player.sendMessage("Usage: /ah [list|sell <price>|buy <id>|cancel <id>]");
            return true;
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid number.");
            return true;
        } catch (Exception e) {
            player.sendMessage("§cError: " + e.getMessage());
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("list", "sell", "buy", "cancel").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
