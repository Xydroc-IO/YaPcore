package com.yapcore.npcs.cmd;

import com.yapcore.npcs.action.NpcActionMutator;
import com.yapcore.npcs.action.NpcActions;
import com.yapcore.npcs.service.NpcServiceImpl;
import com.yapcore.playerdata.NpcTraderAccess;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

import java.util.Locale;
import java.util.Optional;

/** Catalog mutation/list ops for {@link NpcShopOps}. */
final class NpcShopCatalogOps {

    private final NpcServiceImpl npcs;

    NpcShopCatalogOps(NpcServiceImpl npcs) {
        this.npcs = npcs;
    }

    /**
     * Upsert buy+sell for one plain (non-enchanted) material.
     * Pass {@code -} / {@code off} / blank for a side to disable (delete) it.
     * Usage: {@code setitem <id> <material> <amount> <buy|-> <sell|-> [stock=-1]}
     */
    boolean shopSetItem(CommandSender sender, String[] args) {
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
        NpcTraderAccess traders = NpcShopOps.traders();
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

        long shopId = NpcShopOps.ensureShop(npcs, npcId, opt.get(), traders, sender);
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

    boolean shopSetOffer(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /npc shop setoffer <offerId> <price> [amount] [stock]");
            sender.sendMessage("§7Use amount=-1 or stock=-2 to leave unchanged; stock=-1 = unlimited.");
            return true;
        }
        NpcTraderAccess traders = NpcShopOps.traders();
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

    boolean shopList(CommandSender sender, String[] args) {
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
        NpcTraderAccess traders = NpcShopOps.traders();
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

    boolean shopClearOffers(CommandSender sender, String[] args) {
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
        NpcTraderAccess traders = NpcShopOps.traders();
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

    boolean shopDelOffer(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /npc shop deloffer <id> <offerId>");
            return true;
        }
        NpcTraderAccess traders = NpcShopOps.traders();
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

    boolean shopClear(CommandSender sender, String[] args) {
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
        NpcTraderAccess traders = NpcShopOps.traders();
        Optional<Long> shopId = NpcActionMutator.shopId(opt.get().action());
        if (shopId.isPresent() && traders != null) {
            traders.deleteCatalog(shopId.get());
        }
        String next = NpcActionMutator.replaceKind(opt.get().action(), NpcActions.Kind.SHOP, null);
        npcs.setAction(npcId, next);
        sender.sendMessage("§aShop cleared from §f" + npcId);
        return true;
    }
}
