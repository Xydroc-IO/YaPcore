package com.yapcore.playerdata.gui;

import com.yapcore.playerdata.cmd.Perms;
import com.yapcore.playerdata.util.Teleports;
import com.yapcore.messages.YapMessages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.logging.Level;

final class MenuClickHandler {
    private final Menus menus;

    MenuClickHandler(Menus menus) {
        this.menus = menus;
    }

    boolean handleClick(Player player, YapMenuHolder holder, int slot, boolean shift) {
        if (!menus.sync.isReady(player.getUniqueId()) && holder.kind() != YapMenuHolder.Kind.HUB) {
            com.yapcore.messages.YapMessages.profileLoading(player);
            return true;
        }
        ItemStack clicked = holder.getInventory().getItem(slot);
        if (clicked == null || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return true;
        }
        String name = plainName(clicked);

        try {
            return switch (holder.kind()) {
                case HUB -> hubClick(player, name);
                case HOMES -> homesClick(player, slot, shift, name);
                case WARPS -> warpsClick(player, slot, name);
                case KITS -> kitsClick(player, slot, shift, name);
                case KIT_PREVIEW -> kitPreviewClick(player, slot, name);
                case JOBS -> jobsClick(player, slot, name);
                case AUCTIONS -> menus.auctionMenus.handleBrowse(player, slot, name);
                case AUCTIONS_SELL -> menus.auctionMenus.handleSell(player, slot, name);
                case AUCTIONS_MINE -> menus.auctionMenus.handleMine(player, slot, name);
                case MAIL -> menus.mailMenus.handleInbox(player, slot, name);
                case MAIL_SEND -> menus.mailMenus.handleCompose(player, slot, name);
                case NPC_TRADER, NPC_TRADER_QTY -> {
                    // routed via NpcTraderService from MenuListener
                    yield false;
                }
                default -> true;
            };
        } catch (Exception e) {
            com.yapcore.messages.YapMessages.commandFailed(player, e);
            menus.plugin.getLogger().log(Level.WARNING, "menu click", e);
            return true;
        }
    }

    boolean hubClick(Player player, String name) {
        return switch (name) {
            case "Bag" -> {
                if (menus.config.featureBackpack() && menus.backpack != null) {
                    player.closeInventory();
                    menus.backpack.openOwn(player, 1);
                }
                yield true;
            }
            case "Homes" -> {
                if (menus.config.featureHomes()) {
                    menus.markOpenedFromHub(player);
                    menus.openHomes(player);
                }
                yield true;
            }
            case "Warps" -> {
                if (menus.config.featureWarps()) {
                    menus.markOpenedFromHub(player);
                    menus.openWarps(player);
                }
                yield true;
            }
            case "Kits" -> {
                if (menus.config.featureKits()) {
                    menus.markOpenedFromHub(player);
                    menus.openKits(player);
                }
                yield true;
            }
            case "Jobs" -> {
                if (menus.config.featureJobs()) {
                    menus.markOpenedFromHub(player);
                    menus.openJobs(player);
                }
                yield true;
            }
            case "Skills" -> {
                player.closeInventory();
                player.performCommand("skills");
                yield true;
            }
            case "Auctions" -> {
                if (menus.config.featureAuctions()) {
                    menus.markOpenedFromHub(player);
                    menus.openAuctions(player);
                }
                yield true;
            }
            case "Mail" -> {
                if (menus.config.featureMail()) {
                    menus.markOpenedFromHub(player);
                    menus.openMail(player);
                }
                yield true;
            }
            case "Claims" -> {
                player.closeInventory();
                player.performCommand("claim");
                yield true;
            }
            case "Staff" -> {
                if (player.hasPermission("yapadmin.menu")) {
                    player.closeInventory();
                    player.performCommand("yapadmin");
                }
                yield true;
            }
            case "Close" -> {
                player.closeInventory();
                yield true;
            }
            default -> true;
        };
    }

    /** Shared footer: Back → YaP Menu when opened from hub; Close dismisses NPC/command GUIs. */
    boolean navBackOrClose(Player player, String name) {
        return menus.navBackOrClose(player, name);
    }

    boolean homesClick(Player player, int slot, boolean shift, String name) throws Exception {
        if (navBackOrClose(player, name)) {
            return true;
        }
        Map<Integer, String> meta = menus.clickMeta.getOrDefault(player.getUniqueId(), Map.of());
        String home = meta.get(slot);
        if (home == null) {
            return true;
        }
        if (shift) {
            menus.homes.delete(player.getUniqueId(), home);
            player.sendMessage("§aDeleted home §f" + home);
            menus.openHomes(player);
            return true;
        }
        var opt = menus.homes.get(player.getUniqueId(), home);
        player.closeInventory();
        if (opt.isPresent() && Teleports.tryTeleport(player, opt.get(), menus.config.serverId())) {
            player.sendMessage("§aTeleported to §f" + home);
        }
        return true;
    }

    boolean warpsClick(Player player, int slot, String name) throws Exception {
        if (navBackOrClose(player, name)) {
            return true;
        }
        Map<Integer, String> meta = menus.clickMeta.getOrDefault(player.getUniqueId(), Map.of());
        String warp = meta.get(slot);
        if (warp == null) {
            return true;
        }
        var opt = menus.warps.get(warp);
        player.closeInventory();
        if (opt.isPresent() && Teleports.tryTeleport(player, opt.get(), menus.config.serverId())) {
            player.sendMessage("§aWarped to §f" + warp);
        }
        return true;
    }

    boolean kitsClick(Player player, int slot, boolean shift, String name) throws Exception {
        if (navBackOrClose(player, name)) {
            return true;
        }
        Map<Integer, String> meta = menus.clickMeta.getOrDefault(player.getUniqueId(), Map.of());
        String kit = meta.get(slot);
        if (kit == null) {
            return true;
        }
        if (shift || name != null && name.contains("[LOCKED]")) {
            menus.openKitPreview(player, kit);
            return true;
        }
        if (!Perms.hasKit(player, kit)) {
            player.sendMessage("§cThat kit is locked for your rank. Shift-click to preview.");
            return true;
        }
        player.closeInventory();
        player.performCommand("kit " + kit);
        return true;
    }

    boolean kitPreviewClick(Player player, int slot, String name) {
        if ("Back".equals(name)) {
            menus.openKits(player);
            return true;
        }
        if ("Claim".equals(name)) {
            Map<Integer, String> meta = menus.clickMeta.getOrDefault(player.getUniqueId(), Map.of());
            String kit = meta.get(53);
            if (kit == null) {
                return true;
            }
            if (!Perms.hasKit(player, kit)) {
                player.sendMessage("§cThat kit is locked for your rank.");
                return true;
            }
            player.closeInventory();
            player.performCommand("kit " + kit);
        }
        return true;
    }

    boolean jobsClick(Player player, int slot, String name) throws Exception {
        if (navBackOrClose(player, name)) {
            return true;
        }
        Map<Integer, String> meta = menus.clickMeta.getOrDefault(player.getUniqueId(), Map.of());
        String action = meta.get(slot);
        if (action == null) {
            return true;
        }
        if (action.startsWith("join:")) {
            String id = action.substring(5);
            if (!Perms.hasJob(player, id)) {
                YapMessages.noPermission(player);
                return true;
            }
            menus.jobs.join(player.getUniqueId(), id);
            player.sendMessage("§aJoined §f" + id);
        } else if (action.startsWith("leave:")) {
            menus.jobs.leave(player.getUniqueId(), action.substring(6));
            player.sendMessage("§aLeft §f" + action.substring(6));
        }
        menus.openJobs(player);
        return true;
    }

    static String plainName(ItemStack stack) {
        if (stack.getItemMeta() == null || stack.getItemMeta().displayName() == null) {
            return "";
        }
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(stack.getItemMeta().displayName());
    }

}
