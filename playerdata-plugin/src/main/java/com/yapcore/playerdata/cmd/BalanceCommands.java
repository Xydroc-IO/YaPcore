package com.yapcore.playerdata.cmd;

import com.yapcore.playerdata.economy.BalanceStore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /bal and /pay
 */
public final class BalanceCommands implements CommandExecutor, TabCompleter {

    private final BalanceStore store;

    public BalanceCommands(BalanceStore store) {
        this.store = store;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("bal") || name.equals("balance") || name.equals("money")) {
            return bal(sender, args);
        }
        if (name.equals("pay")) {
            return pay(sender, args);
        }
        return false;
    }

    private boolean bal(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Usage: /bal <player>");
                return true;
            }
            double bal = store.getBalance(player.getUniqueId());
            sender.sendMessage(String.format("Balance: $%.2f", bal));
            return true;
        }
        if (!sender.hasPermission("yapdata.balance.others") && !(sender instanceof Player p
                && p.getName().equalsIgnoreCase(args[0]))) {
            sender.sendMessage("No permission to view other balances.");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target.getUniqueId() == null) {
            sender.sendMessage("Unknown player.");
            return true;
        }
        double bal = store.getBalance(target.getUniqueId());
        String shown = target.getName() != null ? target.getName() : args[0];
        sender.sendMessage(String.format("%s's balance: $%.2f", shown, bal));
        return true;
    }

    private boolean pay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player from)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /pay <player> <amount>");
            return true;
        }
        Player to = Bukkit.getPlayerExact(args[0]);
        if (to == null) {
            sender.sendMessage("Player must be online.");
            return true;
        }
        if (to.getUniqueId().equals(from.getUniqueId())) {
            sender.sendMessage("You cannot pay yourself.");
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("Invalid amount.");
            return true;
        }
        if (amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            sender.sendMessage("Amount must be positive.");
            return true;
        }
        amount = Math.round(amount * 100.0) / 100.0;
        if (!store.transfer(from.getUniqueId(), to.getUniqueId(), amount)) {
            sender.sendMessage("Insufficient funds.");
            return true;
        }
        from.sendMessage(String.format("Paid $%.2f to %s.", amount, to.getName()));
        to.sendMessage(String.format("Received $%.2f from %s.", amount, from.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}
