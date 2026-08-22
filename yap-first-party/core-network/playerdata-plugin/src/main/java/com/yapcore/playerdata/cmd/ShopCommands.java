package com.yapcore.playerdata.cmd;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.db.ShopRepository;
import com.yapcore.playerdata.economy.BalanceStore;
import com.yapcore.playerdata.sync.SyncService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/** Chest shops: look at a chest, /shop create <price> while holding the item. */
public final class ShopCommands implements CommandExecutor, TabCompleter {
    private final PlayerDataConfig config;
    private final ShopRepository shops;
    private final BalanceStore balances;
    private final SyncService sync;

    public ShopCommands(PlayerDataConfig config, ShopRepository shops,
                        BalanceStore balances, SyncService sync) {
        this.config = config;
        this.shops = shops;
        this.balances = balances;
        this.sync = sync;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!Perms.require(sender, "yapdata.shop")) {
            return true;
        }
        if (!sync.isReady(player.getUniqueId())) {
            player.sendMessage("§cStill loading your data…");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage("Usage: /shop <create <price>|remove|info>");
            return true;
        }
        try {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create" -> create(player, args);
                case "remove", "delete" -> remove(player);
                case "info" -> info(player);
                default -> {
                    player.sendMessage("Usage: /shop <create <price>|remove|info>");
                    yield true;
                }
            };
        } catch (Exception e) {
            player.sendMessage("§cError: " + e.getMessage());
            return true;
        }
    }

    private boolean create(Player player, String[] args) throws Exception {
        if (args.length < 2) {
            player.sendMessage("Usage: /shop create <price>");
            return true;
        }
        double price = Double.parseDouble(args[1]);
        if (price <= 0) {
            player.sendMessage("§cPrice must be positive.");
            return true;
        }
        Block target = player.getTargetBlockExact(5);
        if (target == null || !(target.getState() instanceof Chest)) {
            player.sendMessage("§cLook at a chest within 5 blocks.");
            return true;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage("§cHold the item you want to sell.");
            return true;
        }
        shops.upsert(new ShopRepository.Shop(
                0,
                player.getUniqueId(),
                config.serverId(),
                target.getWorld().getName(),
                target.getX(),
                target.getY(),
                target.getZ(),
                hand.getType(),
                Math.max(1, hand.getAmount()),
                price));
        player.sendMessage("§aShop created: §f" + hand.getAmount() + "x " + hand.getType()
                + " §afor §f$" + String.format("%.2f", price)
                + " §7(stock comes from the chest)");
        return true;
    }

    private boolean remove(Player player) throws Exception {
        Block target = player.getTargetBlockExact(5);
        if (target == null) {
            player.sendMessage("§cLook at a shop chest.");
            return true;
        }
        var opt = shops.findAt(config.serverId(), target.getWorld().getName(),
                target.getX(), target.getY(), target.getZ());
        if (opt.isEmpty()) {
            player.sendMessage("§cNo shop here.");
            return true;
        }
        if (!opt.get().owner().equals(player.getUniqueId()) && !player.hasPermission("yapdata.admin")) {
            player.sendMessage("§cNot your shop.");
            return true;
        }
        shops.delete(config.serverId(), target.getWorld().getName(),
                target.getX(), target.getY(), target.getZ());
        player.sendMessage("§aShop removed.");
        return true;
    }

    private boolean info(Player player) throws Exception {
        Block target = player.getTargetBlockExact(5);
        if (target == null) {
            player.sendMessage("§cLook at a shop chest.");
            return true;
        }
        var opt = shops.findAt(config.serverId(), target.getWorld().getName(),
                target.getX(), target.getY(), target.getZ());
        if (opt.isEmpty()) {
            player.sendMessage("§cNo shop here.");
            return true;
        }
        ShopRepository.Shop s = opt.get();
        player.sendMessage("§aShop: §f" + s.amount() + "x " + s.material()
                + " §a@ §f$" + String.format("%.2f", s.price()));
        return true;
    }

    /** Called from listener when buyer left-clicks chest. */
    public boolean tryBuy(Player buyer, ShopRepository.Shop shop, Chest chest) throws Exception {
        if (!buyer.hasPermission("yapdata.shop")) {
            buyer.sendMessage("§cNo permission.");
            return false;
        }
        if (shop.owner().equals(buyer.getUniqueId())) {
            buyer.sendMessage("§7That's your shop.");
            return false;
        }
        if (balances.getBalance(buyer.getUniqueId()) < shop.price()) {
            buyer.sendMessage("§cInsufficient funds.");
            return false;
        }
        ItemStack need = new ItemStack(shop.material(), shop.amount());
        if (!chest.getInventory().containsAtLeast(need, shop.amount())) {
            buyer.sendMessage("§cShop out of stock.");
            return false;
        }
        chest.getInventory().removeItem(need);
        buyer.getInventory().addItem(need.clone());
        balances.setBalance(buyer.getUniqueId(), balances.getBalance(buyer.getUniqueId()) - shop.price());
        balances.setBalance(shop.owner(), balances.getBalance(shop.owner()) + shop.price());
        buyer.sendMessage("§aBought §f" + shop.amount() + "x " + shop.material()
                + " §afor §f$" + String.format("%.2f", shop.price()));
        return true;
    }

    public ShopRepository shops() {
        return shops;
    }

    public PlayerDataConfig config() {
        return config;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("create", "remove", "info").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
