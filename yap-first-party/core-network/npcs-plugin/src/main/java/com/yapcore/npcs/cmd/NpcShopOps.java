package com.yapcore.npcs.cmd;

import com.yapcore.npcs.action.NpcActionMutator;
import com.yapcore.npcs.action.NpcActions;
import com.yapcore.npcs.db.NpcRepository;
import com.yapcore.npcs.service.NpcServiceImpl;
import com.yapcore.playerdata.NpcTraderAccess;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Locale;
import java.util.Optional;

/** Shop catalog handlers for {@link NpcCommands}. */
final class NpcShopOps {

    private final NpcServiceImpl npcs;

    NpcShopOps(NpcServiceImpl npcs) {
        this.npcs = npcs;
    }

    boolean handleShop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§e/npc shop enable <id> [catalogName]");
            sender.sendMessage("§e/npc shop addbuy|addsell <id> <price> [stock=-1] §7(hold item)");
            sender.sendMessage("§e/npc shop list|clear <id> §7· §e/npc shop deloffer <id> <offerId>");
            return true;
        }
        String op = args[1].toLowerCase(Locale.ROOT);
        return switch (op) {
            case "enable", "create", "attach" -> shopEnable(sender, args);
            case "addbuy", "buy" -> shopAddOffer(sender, args, "BUY");
            case "addsell", "sell" -> shopAddOffer(sender, args, "SELL");
            case "list" -> shopList(sender, args);
            case "deloffer" -> shopDelOffer(sender, args);
            case "clear", "disable" -> shopClear(sender, args);
            default -> {
                sender.sendMessage("§cUnknown shop op. Try §e/npc shop§c.");
                yield true;
            }
        };
    }

    private boolean shopEnable(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /npc shop enable <id> [catalogName]");
            return true;
        }
        String npcId = args[2];
        var opt = npcs.get(npcId);
        if (opt.isEmpty()) {
            sender.sendMessage("§cNPC not found. Create it first: /npc create " + npcId);
            return true;
        }
        NpcTraderAccess traders = traders();
        if (traders == null) {
            sender.sendMessage("§cYaPPlayerData traders off — set features.traders: true.");
            return true;
        }
        Optional<Long> existing = NpcActionMutator.shopId(opt.get().action());
        if (existing.isPresent() && traders.traderExists(existing.get())) {
            sender.sendMessage("§aShop already linked §f#" + existing.get()
                    + " §7— hold item · §e/npc shop addbuy " + npcId + " <price>");
            return true;
        }
        String catalogName = args.length >= 4
                ? String.join(" ", NpcCommandParse.copyFrom(args, 3))
                : (opt.get().displayName() == null ? npcId : opt.get().displayName());
        long shopId = traders.createCatalog(catalogName);
        String next = NpcActionMutator.replaceKind(opt.get().action(), NpcActions.Kind.SHOP, "shop:" + shopId);
        npcs.setAction(npcId, next);
        sender.sendMessage("§aShop enabled on §f" + npcId + " §7→ catalog §f#" + shopId);
        sender.sendMessage("§7Hold item · §e/npc shop addbuy " + npcId + " <price> [stock]");
        return true;
    }

    private boolean shopAddOffer(CommandSender sender, String[] args, String mode) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only (need held item).");
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /npc shop " + args[1] + " <id> <price> [stock=-1]");
            return true;
        }
        String npcId = args[2];
        var opt = npcs.get(npcId);
        if (opt.isEmpty()) {
            sender.sendMessage("§cNPC not found.");
            return true;
        }
        NpcTraderAccess traders = traders();
        if (traders == null) {
            sender.sendMessage("§cYaPPlayerData traders off.");
            return true;
        }
        long shopId = ensureShop(npcId, opt.get(), traders, sender);
        if (shopId < 0) {
            return true;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            sender.sendMessage("§cHold the item to trade.");
            return true;
        }
        double price;
        int stock = -1;
        try {
            price = Double.parseDouble(args[3]);
            if (args.length >= 5) {
                stock = Integer.parseInt(args[4]);
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid price/stock.");
            return true;
        }
        long oid = traders.addOffer(shopId, mode, hand.getType(),
                Math.max(1, hand.getAmount()), price, stock);
        sender.sendMessage("§a" + mode + " offer §f#" + oid + " §a"
                + hand.getAmount() + "x " + hand.getType()
                + " @ $" + String.format("%.2f", price)
                + " §7on §f" + npcId);
        return true;
    }

    private boolean shopList(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /npc shop list <id>");
            return true;
        }
        var opt = npcs.get(args[2]);
        if (opt.isEmpty()) {
            sender.sendMessage("§cNPC not found.");
            return true;
        }
        NpcTraderAccess traders = traders();
        if (traders == null) {
            sender.sendMessage("§cYaPPlayerData traders off.");
            return true;
        }
        Optional<Long> shopId = NpcActionMutator.shopId(opt.get().action());
        if (shopId.isEmpty()) {
            sender.sendMessage("§7No shop on this NPC. §e/npc shop enable " + args[2]);
            return true;
        }
        var offers = traders.listOffers(shopId.get());
        sender.sendMessage("§6Shop §f#" + shopId.get() + " §7on §f" + args[2]
                + " §7(" + offers.size() + " offers)");
        for (var o : offers) {
            sender.sendMessage("§e#" + o.id() + " §f" + o.mode() + " "
                    + o.amount() + "x " + o.material()
                    + " §a$" + String.format("%.2f", o.price())
                    + " §7stock=" + (o.stock() < 0 ? "∞" : o.stock()));
        }
        return true;
    }

    private boolean shopDelOffer(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /npc shop deloffer <id> <offerId>");
            return true;
        }
        NpcTraderAccess traders = traders();
        if (traders == null) {
            sender.sendMessage("§cYaPPlayerData traders off.");
            return true;
        }
        long oid;
        try {
            oid = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid offer id.");
            return true;
        }
        if (traders.deleteOffer(oid)) {
            sender.sendMessage("§aDeleted offer §f#" + oid);
        } else {
            sender.sendMessage("§cUnknown offer.");
        }
        return true;
    }

    private boolean shopClear(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /npc shop clear <id>");
            return true;
        }
        String npcId = args[2];
        var opt = npcs.get(npcId);
        if (opt.isEmpty()) {
            sender.sendMessage("§cNPC not found.");
            return true;
        }
        NpcTraderAccess traders = traders();
        Optional<Long> shopId = NpcActionMutator.shopId(opt.get().action());
        if (shopId.isPresent() && traders != null) {
            traders.deleteCatalog(shopId.get());
        }
        String next = NpcActionMutator.replaceKind(opt.get().action(), NpcActions.Kind.SHOP, null);
        npcs.setAction(npcId, next);
        sender.sendMessage("§aShop cleared from §f" + npcId);
        return true;
    }

    private long ensureShop(String npcId, NpcRepository.NpcRecord npc, NpcTraderAccess traders,
                            CommandSender sender) {
        Optional<Long> existing = NpcActionMutator.shopId(npc.action());
        if (existing.isPresent() && traders.traderExists(existing.get())) {
            return existing.get();
        }
        long shopId = traders.createCatalog(npc.displayName() == null ? npcId : npc.displayName());
        String next = NpcActionMutator.replaceKind(npc.action(), NpcActions.Kind.SHOP, "shop:" + shopId);
        npcs.setAction(npcId, next);
        sender.sendMessage("§7Auto-enabled shop catalog §f#" + shopId + " §7on §f" + npcId);
        return shopId;
    }

    static NpcTraderAccess traders() {
        RegisteredServiceProvider<NpcTraderAccess> reg =
                Bukkit.getServicesManager().getRegistration(NpcTraderAccess.class);
        return reg == null ? null : reg.getProvider();
    }
}
