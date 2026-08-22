package com.yapcore.playerdata.cmd;

import com.yapcore.playerdata.npc.NpcTraderService;
import com.yapcore.playerdata.sync.SyncService;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TraderCommands implements CommandExecutor, TabCompleter {

    private final NpcTraderService traders;
    private final SyncService sync;

    public TraderCommands(NpcTraderService traders, SyncService sync) {
        this.traders = traders;
        this.sync = sync;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("yapdata.trader.admin") && !player.hasPermission("yapdata.admin")) {
            player.sendMessage("§cNo permission.");
            return true;
        }
        if (!sync.isReady(player.getUniqueId())) {
            player.sendMessage("§cStill loading…");
            return true;
        }
        try {
            if (args.length == 0) {
                player.sendMessage("Usage: /trader <create|remove|addbuy|addsell|deloffer|list|reload>");
                return true;
            }
            String sub = args[0].toLowerCase(Locale.ROOT);
            return switch (sub) {
                case "create" -> {
                    String name = args.length >= 2
                            ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                            : "Trader";
                    long id = traders.createAt(player, name);
                    player.sendMessage("§aCreated NPC trader §f#" + id + " §a«" + name + "»");
                    yield true;
                }
                case "remove", "delete" -> {
                    if (traders.removeNearest(player, 4)) {
                        player.sendMessage("§aRemoved nearest trader.");
                    } else {
                        player.sendMessage("§cNo trader within 4 blocks.");
                    }
                    yield true;
                }
                case "addbuy", "buy" -> {
                    yield addOffer(player, args, "BUY");
                }
                case "addsell", "sell" -> {
                    yield addOffer(player, args, "SELL");
                }
                case "deloffer" -> {
                    if (args.length < 2) {
                        player.sendMessage("Usage: /trader deloffer <offerId>");
                        yield true;
                    }
                    long oid = Long.parseLong(args[1]);
                    if (traders.repo().deleteOffer(oid)) {
                        player.sendMessage("§aDeleted offer §f#" + oid);
                    } else {
                        player.sendMessage("§cUnknown offer.");
                    }
                    yield true;
                }
                case "list" -> {
                    var near = traders.findTraderIdNear(player.getLocation(), 6);
                    if (near.isEmpty()) {
                        player.sendMessage("§cStand near a trader.");
                        yield true;
                    }
                    for (var o : traders.repo().offers(near.get())) {
                        player.sendMessage("§e#" + o.id() + " §f" + o.mode() + " "
                                + o.amount() + "x " + o.material()
                                + " §a$" + String.format("%.2f", o.price())
                                + " §7stock=" + (o.stock() < 0 ? "∞" : o.stock()));
                    }
                    yield true;
                }
                case "reload" -> {
                    traders.respawnAll();
                    player.sendMessage("§aTraders respawned.");
                    yield true;
                }
                default -> {
                    player.sendMessage("Usage: /trader <create|remove|addbuy|addsell|deloffer|list|reload>");
                    yield true;
                }
            };
        } catch (Exception e) {
            player.sendMessage("§cError: " + e.getMessage());
            return true;
        }
    }

    private boolean addOffer(Player player, String[] args, String mode) throws Exception {
        if (args.length < 2) {
            player.sendMessage("Usage: /trader " + args[0] + " <price> [stock=-1]");
            return true;
        }
        double price = Double.parseDouble(args[1]);
        int stock = args.length >= 3 ? Integer.parseInt(args[2]) : -1;
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage("§cHold the item to trade.");
            return true;
        }
        var near = traders.findTraderIdNear(player.getLocation(), 6);
        if (near.isEmpty()) {
            player.sendMessage("§cStand near a trader.");
            return true;
        }
        long oid = traders.repo().addOffer(near.get(), mode, hand.getType(),
                Math.max(1, hand.getAmount()), price, stock);
        player.sendMessage("§aAdded §f" + mode + " §aoffer §f#" + oid
                + " §a" + hand.getAmount() + "x " + hand.getType()
                + " @ $" + String.format("%.2f", price));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String s : List.of("create", "remove", "addbuy", "addsell", "deloffer", "list", "reload")) {
                if (s.startsWith(p)) {
                    out.add(s);
                }
            }
            return out;
        }
        return List.of();
    }
}
