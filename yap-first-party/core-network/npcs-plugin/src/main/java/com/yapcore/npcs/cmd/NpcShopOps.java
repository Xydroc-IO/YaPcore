package com.yapcore.npcs.cmd;

import com.yapcore.npcs.action.NpcActionMutator;
import com.yapcore.npcs.action.NpcActions;
import com.yapcore.npcs.db.NpcRepository;
import com.yapcore.npcs.service.NpcServiceImpl;
import com.yapcore.playerdata.NpcTraderAccess;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
            sender.sendMessage("§e/npc shop apply <preset> <id> [--replace]");
            sender.sendMessage("§e/npc shop presets");
            sender.sendMessage("§e/npc shop addbuy|addsell <id> <material> <amount> <price> [stock=-1]");
            sender.sendMessage("§e/npc shop addbuy|addsell <id> <price> [stock=-1] §7(hold item)");
            sender.sendMessage("§e/npc shop setitem <id> <material> <amount> <buy|-> <sell|-> [stock=-1]");
            sender.sendMessage("§e/npc shop setoffer <offerId> <price> [amount] [stock]");
            sender.sendMessage("§e/npc shop list <id> [json] · clearoffers <id> · deloffer <id> <offerId>");
            sender.sendMessage("§e/npc shop clear <id>");
            return true;
        }
        String op = args[1].toLowerCase(Locale.ROOT);
        return switch (op) {
            case "enable", "create", "attach" -> shopEnable(sender, args);
            case "apply", "preset", "load" -> shopApply(sender, args);
            case "presets", "catalogs" -> shopPresets(sender, args.length >= 3 && "json".equalsIgnoreCase(args[2]));
            case "addbuy", "buy" -> shopAddOffer(sender, args, "BUY");
            case "addsell", "sell" -> shopAddOffer(sender, args, "SELL");
            case "setitem", "item" -> shopSetItem(sender, args);
            case "setoffer", "edit", "setprice" -> shopSetOffer(sender, args);
            case "list" -> shopList(sender, args);
            case "deloffer" -> shopDelOffer(sender, args);
            case "clearoffers", "wipeoffers" -> shopClearOffers(sender, args);
            case "clear", "disable" -> shopClear(sender, args);
            default -> {
                sender.sendMessage("§cUnknown shop op. Try §e/npc shop§c.");
                yield true;
            }
        };
    }

    private boolean shopPresets(CommandSender sender) {
        return shopPresets(sender, false);
    }

    private boolean shopPresets(CommandSender sender, boolean json) {
        NpcTraderAccess traders = traders();
        if (traders == null) {
            sender.sendMessage(json ? "YAPSHOP_PRESETS:[]" : "§cYaPPlayerData traders off — set features.traders: true.");
            return true;
        }
        if (json) {
            StringBuilder sb = new StringBuilder("YAPSHOP_PRESETS:[");
            int i = 0;
            for (String id : traders.shopPresetIds()) {
                if (i++ > 0) {
                    sb.append(',');
                }
                sb.append(NpcCommandParse.q(id));
            }
            sb.append(']');
            sender.sendMessage(sb.toString());
            return true;
        }
        sender.sendMessage("§6Shop presets §7(unlimited stock · sell≈38% of buy):");
        for (String id : traders.shopPresetIds()) {
            sender.sendMessage("§e  " + id);
        }
        sender.sendMessage("§7Apply: §e/npc shop apply <preset> <npcId> [--replace]");
        return true;
    }

    private boolean shopApply(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /npc shop apply <preset> <id> [--replace]");
            sender.sendMessage("§7Also accepts: §e/npc shop apply <id> <preset> [--replace]");
            sender.sendMessage("§7Presets: §e/npc shop presets");
            return true;
        }
        String a = args[2].toLowerCase(Locale.ROOT);
        String b = args[3].toLowerCase(Locale.ROOT);
        boolean replace = false;
        for (int i = 4; i < args.length; i++) {
            if ("--replace".equalsIgnoreCase(args[i]) || "replace".equalsIgnoreCase(args[i])) {
                replace = true;
            }
        }
        NpcTraderAccess traders = traders();
        if (traders == null) {
            sender.sendMessage("§cYaPPlayerData traders off — set features.traders: true.");
            return true;
        }
        // Accept either order: apply food chef  OR  apply chef food
        String preset;
        String npcId;
        boolean aPreset = traders.shopPresetIds().contains(a);
        boolean bPreset = traders.shopPresetIds().contains(b);
        if (aPreset && !bPreset) {
            preset = a;
            npcId = args[3];
        } else if (bPreset && !aPreset) {
            preset = b;
            npcId = args[2];
        } else if (aPreset) {
            preset = a;
            npcId = args[3];
        } else {
            sender.sendMessage("§cUnknown preset §f" + a + " §cor §f" + b);
            sender.sendMessage("§7Try: §e" + String.join("§7, §e", traders.shopPresetIds()));
            return true;
        }
        var opt = npcs.get(npcId);
        if (opt.isEmpty()) {
            sender.sendMessage("§cNPC not found. Create it first: /npc create " + npcId);
            return true;
        }
        long shopId = ensureShop(npcId, opt.get(), traders, sender);
        if (shopId < 0) {
            return true;
        }
        try {
            int n = traders.applyShopPreset(shopId, preset, replace);
            sender.sendMessage("§aApplied preset §f" + preset + " §ato §f" + npcId
                    + " §7→ catalog §f#" + shopId
                    + " §7(" + n + " offers" + (replace ? ", replaced" : "") + ")");
            sender.sendMessage("§7Players buy green · sell aqua · stock ∞ · NPC buy price ≈ 38% of sell");
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c" + e.getMessage());
        } catch (RuntimeException e) {
            sender.sendMessage("§cApply failed: " + e.getMessage());
        }
        return true;
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
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /npc shop " + args[1]
                    + " <id> <material> <amount> <price> [stock=-1]");
            sender.sendMessage("§c   or: /npc shop " + args[1] + " <id> <price> [stock=-1] §7(hold item)");
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

        Material material;
        int amount;
        double price;
        int stock = -1;

        // material form: addbuy <id> <material> <amount> <price> [stock]
        boolean materialForm = args.length >= 6 && !isNumber(args[3]);
        if (materialForm) {
            material = Material.matchMaterial(args[3]);
            if (material == null || material.isAir()) {
                sender.sendMessage("§cUnknown material: " + args[3]);
                return true;
            }
            try {
                amount = Math.max(1, Integer.parseInt(args[4]));
                price = Double.parseDouble(args[5]);
                if (args.length >= 7) {
                    stock = Integer.parseInt(args[6]);
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid amount/price/stock.");
                return true;
            }
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cConsole must use: /npc shop " + args[1]
                        + " <id> <material> <amount> <price> [stock]");
                return true;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) {
                sender.sendMessage("§cHold the item to trade, or pass a material name.");
                return true;
            }
            material = hand.getType();
            amount = Math.max(1, hand.getAmount());
            try {
                price = Double.parseDouble(args[3]);
                if (args.length >= 5) {
                    stock = Integer.parseInt(args[4]);
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid price/stock.");
                return true;
            }
        }

        long oid = traders.addOffer(shopId, mode, material, amount, price, stock);
        sender.sendMessage("§a" + mode + " offer §f#" + oid + " §a"
                + amount + "x " + material
                + " @ $" + String.format(Locale.US, "%.2f", price)
                + " §7on §f" + npcId);
        return true;
    }

    /**
     * Upsert buy+sell for one plain (non-enchanted) material.
     * Pass {@code -} / {@code off} / blank for a side to disable (delete) it.
     * Usage: {@code setitem <id> <material> <amount> <buy|-> <sell|-> [stock=-1]}
     */
    private boolean shopSetItem(CommandSender sender, String[] args) {
        if (args.length < 7) {
            sender.sendMessage("§cUsage: /npc shop setitem <id> <material> <amount> <buyPrice|-> <sellPrice|-> [stock=-1]");
            sender.sendMessage("§7Use §e-§7 for a side to disable it. Stock §e-1§7 = unlimited.");
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
        Material material = Material.matchMaterial(args[3]);
        if (material == null || material.isAir()) {
            sender.sendMessage("§cUnknown material: " + args[3]);
            return true;
        }
        int amount;
        int stock = -1;
        try {
            amount = Math.max(1, Integer.parseInt(args[4]));
            if (args.length >= 8) {
                stock = Integer.parseInt(args[7]);
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid amount/stock.");
            return true;
        }
        boolean buyOff = isDisabledToken(args[5]);
        boolean sellOff = isDisabledToken(args[6]);
        Double buyPrice = null;
        Double sellPrice = null;
        if (!buyOff) {
            try {
                buyPrice = Double.parseDouble(args[5].trim());
                if (buyPrice < 0) {
                    throw new NumberFormatException("neg");
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid buy price (number or -).");
                return true;
            }
        }
        if (!sellOff) {
            try {
                sellPrice = Double.parseDouble(args[6].trim());
                if (sellPrice < 0) {
                    throw new NumberFormatException("neg");
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid sell price (number or -).");
                return true;
            }
        }

        long shopId = ensureShop(npcId, opt.get(), traders, sender);
        if (shopId < 0) {
            return true;
        }

        var offers = traders.listOffers(shopId);
        NpcTraderAccess.OfferView buy = null;
        NpcTraderAccess.OfferView sell = null;
        for (var o : offers) {
            if (!material.name().equalsIgnoreCase(o.material())) {
                continue;
            }
            if (o.metaJson() != null && !o.metaJson().isBlank()) {
                continue; // enchanted / special lines stay separate
            }
            if ("BUY".equalsIgnoreCase(o.mode()) && buy == null) {
                buy = o;
            } else if ("SELL".equalsIgnoreCase(o.mode()) && sell == null) {
                sell = o;
            }
        }

        StringBuilder msg = new StringBuilder("§a").append(material).append(" §7on §f").append(npcId).append("§7:");
        if (buyOff) {
            if (buy != null) {
                traders.deleteOffer(buy.id());
                msg.append(" §cbuy off");
            }
        } else if (buy != null) {
            traders.updateOffer(buy.id(), buyPrice, amount, stock);
            msg.append(" §abuy $").append(String.format(Locale.US, "%.2f", buyPrice));
        } else {
            long oid = traders.addOffer(shopId, "BUY", material, amount, buyPrice, stock);
            msg.append(" §abuy #").append(oid).append(" $").append(String.format(Locale.US, "%.2f", buyPrice));
        }
        if (sellOff) {
            if (sell != null) {
                traders.deleteOffer(sell.id());
                msg.append(" §csell off");
            }
        } else if (sell != null) {
            traders.updateOffer(sell.id(), sellPrice, amount, stock);
            msg.append(" §asell $").append(String.format(Locale.US, "%.2f", sellPrice));
        } else {
            long oid = traders.addOffer(shopId, "SELL", material, amount, sellPrice, stock);
            msg.append(" §asell #").append(oid).append(" $").append(String.format(Locale.US, "%.2f", sellPrice));
        }
        msg.append(" §7· ").append(amount).append("x · stock=")
                .append(stock < 0 ? "∞" : stock);
        sender.sendMessage(msg.toString());
        return true;
    }

    private static boolean isDisabledToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        String t = raw.trim().toLowerCase(Locale.ROOT);
        return "-".equals(t) || "off".equals(t) || "none".equals(t) || "null".equals(t) || "disable".equals(t);
    }

    private boolean shopSetOffer(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /npc shop setoffer <offerId> <price> [amount] [stock]");
            sender.sendMessage("§7Use amount=-1 or stock=-2 to leave unchanged; stock=-1 = unlimited.");
            return true;
        }
        NpcTraderAccess traders = traders();
        if (traders == null) {
            sender.sendMessage("§cYaPPlayerData traders off.");
            return true;
        }
        long oid;
        double price;
        int amount = -1;
        int stock = -2; // leave unchanged
        try {
            oid = Long.parseLong(args[2]);
            price = Double.parseDouble(args[3]);
            if (args.length >= 5) {
                amount = Integer.parseInt(args[4]);
            }
            if (args.length >= 6) {
                stock = Integer.parseInt(args[5]);
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid offerId/price/amount/stock.");
            return true;
        }
        if (traders.updateOffer(oid, price, amount, stock)) {
            sender.sendMessage("§aUpdated offer §f#" + oid
                    + " §7price=$" + String.format(Locale.US, "%.2f", price)
                    + (amount > 0 ? " amount=" + amount : "")
                    + (stock >= -1 ? " stock=" + (stock < 0 ? "∞" : stock) : ""));
        } else {
            sender.sendMessage("§cUnknown offer.");
        }
        return true;
    }

    private boolean shopList(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /npc shop list <id> [json]");
            return true;
        }
        String npcId = args[2];
        boolean json = args.length >= 4 && "json".equalsIgnoreCase(args[3]);
        var opt = npcs.get(npcId);
        if (opt.isEmpty()) {
            sender.sendMessage(json ? "YAPSHOP_JSON:{}" : "§cNPC not found.");
            return true;
        }
        NpcTraderAccess traders = traders();
        if (traders == null) {
            sender.sendMessage(json ? "YAPSHOP_JSON:{}" : "§cYaPPlayerData traders off.");
            return true;
        }
        Optional<Long> shopId = NpcActionMutator.shopId(opt.get().action());
        if (shopId.isEmpty()) {
            if (json) {
                sender.sendMessage("YAPSHOP_JSON:{\"npcId\":" + NpcCommandParse.q(npcId)
                        + ",\"catalogId\":null,\"offers\":[]}");
            } else {
                sender.sendMessage("§7No shop on this NPC. §e/npc shop enable " + npcId);
            }
            return true;
        }
        var offers = traders.listOffers(shopId.get());
        if (json) {
            StringBuilder sb = new StringBuilder("YAPSHOP_JSON:{");
            sb.append("\"npcId\":").append(NpcCommandParse.q(npcId)).append(',');
            sb.append("\"displayName\":").append(NpcCommandParse.q(opt.get().displayName())).append(',');
            sb.append("\"catalogId\":").append(shopId.get()).append(',');
            sb.append("\"offers\":[");
            for (int i = 0; i < offers.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                var o = offers.get(i);
                sb.append('{')
                        .append("\"id\":").append(o.id()).append(',')
                        .append("\"mode\":").append(NpcCommandParse.q(o.mode())).append(',')
                        .append("\"material\":").append(NpcCommandParse.q(o.material())).append(',')
                        .append("\"amount\":").append(o.amount()).append(',')
                        .append("\"price\":").append(o.price()).append(',')
                        .append("\"stock\":").append(o.stock()).append(',')
                        .append("\"meta\":").append(NpcCommandParse.q(o.metaJson()))
                        .append('}');
            }
            sb.append("]}");
            sender.sendMessage(sb.toString());
            return true;
        }
        sender.sendMessage("§6Shop §f#" + shopId.get() + " §7on §f" + npcId
                + " §7(" + offers.size() + " offers)");
        int shown = 0;
        for (var o : offers) {
            if (shown >= 40) {
                sender.sendMessage("§7… +" + (offers.size() - shown) + " more (open the NPC GUI)");
                break;
            }
            String enchant = (o.metaJson() != null && !o.metaJson().isBlank()) ? " §d✦" : "";
            sender.sendMessage("§e#" + o.id() + " §f" + o.mode() + " "
                    + o.amount() + "x " + o.material() + enchant
                    + " §a$" + String.format(Locale.US, "%.2f", o.price())
                    + " §7stock=" + (o.stock() < 0 ? "∞" : o.stock()));
            shown++;
        }
        return true;
    }

    private boolean shopClearOffers(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /npc shop clearoffers <id>");
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
        Optional<Long> shopId = NpcActionMutator.shopId(opt.get().action());
        if (shopId.isEmpty()) {
            sender.sendMessage("§7No shop on this NPC.");
            return true;
        }
        int n = traders.clearOffers(shopId.get());
        sender.sendMessage("§aCleared §f" + n + " §aoffers from §f" + npcId
                + " §7(catalog §f#" + shopId.get() + " §7kept)");
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

    private static boolean isNumber(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
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
