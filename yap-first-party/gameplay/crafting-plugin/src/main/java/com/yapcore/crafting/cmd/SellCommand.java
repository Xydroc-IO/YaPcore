package com.yapcore.crafting.cmd;

import com.yapcore.crafting.CraftingConfig;
import com.yapcore.crafting.economy.SellPriceRegistry;
import com.yapcore.playerdata.PlayerDataService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class SellCommand implements CommandExecutor, TabCompleter {

    private final CraftingConfig config;
    private final SellPriceRegistry prices;

    public SellCommand(CraftingConfig config, SellPriceRegistry prices) {
        this.config = config;
        this.prices = prices;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("yapcraft.sell")) {
            player.sendMessage("§cNo permission.");
            return true;
        }
        if (!config.economyEnabled()) {
            player.sendMessage("§cSelling is disabled.");
            return true;
        }
        PlayerDataService economy = economy();
        if (economy == null || !economy.economyEnabled()) {
            player.sendMessage("§cEconomy unavailable — install YaPPlayerData with economy enabled.");
            return true;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage("§cHold an item to sell.");
            return true;
        }
        double unitPrice = prices.priceFor(hand);
        if (unitPrice <= 0) {
            player.sendMessage("§cThat item cannot be sold.");
            return true;
        }
        int amount = hand.getAmount();
        if (args.length >= 1) {
            try {
                amount = Math.min(amount, Math.max(1, Integer.parseInt(args[0])));
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid amount.");
                return true;
            }
        }
        double total = unitPrice * amount;
        ItemStack toRemove = hand.clone();
        toRemove.setAmount(amount);
        hand.setAmount(hand.getAmount() - amount);
        if (hand.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        }
        Optional<Double> deposited = economy.deposit(player.getUniqueId(), total);
        if (deposited.isEmpty()) {
            player.getInventory().addItem(toRemove);
            player.sendMessage("§cSale failed.");
            return true;
        }
        player.sendMessage("§aSold §f" + amount + "x " + toRemove.getType().name().toLowerCase(Locale.ROOT)
                + " §afor §f" + economy.formatMoney(total));
        return true;
    }

    private PlayerDataService economy() {
        if (config.requirePlayerData() && Bukkit.getPluginManager().getPlugin("YaPPlayerData") == null) {
            return null;
        }
        return Bukkit.getServicesManager().load(PlayerDataService.class);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
