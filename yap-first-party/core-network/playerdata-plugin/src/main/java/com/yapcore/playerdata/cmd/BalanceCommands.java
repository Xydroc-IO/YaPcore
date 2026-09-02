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
 * /bal, /pay, and staff /eco give|take|set|reset
 */
public final class BalanceCommands implements CommandExecutor, TabCompleter {

    private static final List<String> ECO_ACTIONS = List.of("give", "take", "set", "reset");

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
        if (name.equals("eco") || name.equals("economy")) {
            return eco(sender, args);
        }
        return false;
    }

    private boolean bal(CommandSender sender, String[] args) {
        if (!Perms.require(sender, "yapdata.balance")) {
            return true;
        }
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
        if (!Perms.require(sender, "yapdata.pay")) {
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

    private boolean eco(CommandSender sender, String[] args) {
        if (!Perms.require(sender, "yapdata.eco")) {
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§e/eco <give|take|set|reset> <player> [amount]");
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (!ECO_ACTIONS.contains(action)) {
            sender.sendMessage("§cUnknown action. Use give, take, set, or reset.");
            return true;
        }
        OfflinePlayer target = resolvePlayer(args[1]);
        String shown = target.getName() != null ? target.getName() : args[1];
        double current = store.getBalance(target.getUniqueId());
        if ("reset".equals(action)) {
            store.setBalance(target.getUniqueId(), 0.0);
            tellEco(sender, target, 0.0, "Reset " + shown + "'s balance to $0.00.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§e/eco " + action + " <player> <amount>");
            return true;
        }
        Double amount = parseAmount(args[2]);
        if (amount == null) {
            sender.sendMessage("§cInvalid amount.");
            return true;
        }
        double next;
        switch (action) {
            case "give" -> next = current + amount;
            case "take" -> {
                if (current < amount) {
                    sender.sendMessage(String.format("§c%s only has $%.2f.", shown, current));
                    return true;
                }
                next = current - amount;
            }
            case "set" -> next = amount;
            default -> {
                return true;
            }
        }
        store.setBalance(target.getUniqueId(), next);
        String verb = switch (action) {
            case "give" -> String.format("Gave $%.2f to %s.", amount, shown);
            case "take" -> String.format("Took $%.2f from %s.", amount, shown);
            default -> String.format("Set %s's balance to $%.2f.", shown, next);
        };
        tellEco(sender, target, next, verb + String.format(" New balance: $%.2f", next));
        return true;
    }

    private static void tellEco(CommandSender sender, OfflinePlayer target,
                                double newBal, String staffMsg) {
        sender.sendMessage("§a" + staffMsg);
        Player online = target.getPlayer();
        if (online != null && !online.equals(sender)) {
            online.sendMessage(String.format("§aYour balance is now $%.2f.", newBal));
        }
    }

    private static OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline;
    }

    private static Double parseAmount(String raw) {
        try {
            double amount = Double.parseDouble(raw);
            if (amount < 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
                return null;
            }
            return Math.round(amount * 100.0) / 100.0;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String name = command.getName().toLowerCase(Locale.ROOT);
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        if ((name.equals("eco") || name.equals("economy")) && args.length == 1) {
            for (String action : ECO_ACTIONS) {
                if (action.startsWith(prefix)) {
                    out.add(action);
                }
            }
            return out;
        }
        int playerArg = (name.equals("eco") || name.equals("economy")) ? 2 : 1;
        if (args.length == playerArg) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}
