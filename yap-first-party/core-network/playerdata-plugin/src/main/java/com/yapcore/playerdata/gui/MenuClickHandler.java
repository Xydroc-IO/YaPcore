package com.yapcore.playerdata.gui;

import com.yapcore.playerdata.claims.Claim;
import com.yapcore.playerdata.claims.ClaimVisualizer;
import com.yapcore.playerdata.cmd.Perms;
import com.yapcore.playerdata.util.Teleports;
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
            player.sendMessage("§cStill loading…");
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
                case KITS -> kitsClick(player, slot, name);
                case JOBS -> jobsClick(player, slot, name);
                case AUCTIONS -> auctionsClick(player, slot, name);
                case MAIL -> mailClick(player, name);
                case CLAIMS -> claimsClick(player, slot, shift, name);
                case NPC_TRADER -> {
                    Long traderId = holder.context();
                    if (traderId != null) {
                        // routed via NpcTraderService from MenuListener
                        yield false;
                    }
                    yield true;
                }
                default -> true;
            };
        } catch (Exception e) {
            player.sendMessage("§cError: " + e.getMessage());
            menus.plugin.getLogger().log(Level.WARNING, "menu click", e);
            return true;
        }
    }

    boolean hubClick(Player player, String name) {
        return switch (name) {
            case "Homes" -> {
                if (menus.config.featureHomes()) {
                    menus.openHomes(player);
                }
                yield true;
            }
            case "Warps" -> {
                if (menus.config.featureWarps()) {
                    menus.openWarps(player);
                }
                yield true;
            }
            case "Kits" -> {
                if (menus.config.featureKits()) {
                    menus.openKits(player);
                }
                yield true;
            }
            case "Jobs" -> {
                if (menus.config.featureJobs()) {
                    menus.openJobs(player);
                }
                yield true;
            }
            case "Auctions" -> {
                if (menus.config.featureAuctions()) {
                    menus.openAuctions(player);
                }
                yield true;
            }
            case "Mail" -> {
                if (menus.config.featureMail()) {
                    menus.openMail(player);
                }
                yield true;
            }
            case "Claims" -> {
                if (menus.config.featureClaims()) {
                    menus.openClaims(player);
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

    boolean homesClick(Player player, int slot, boolean shift, String name) throws Exception {
        if ("Back".equals(name)) {
            menus.openHub(player);
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
        if ("Back".equals(name)) {
            menus.openHub(player);
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

    boolean kitsClick(Player player, int slot, String name) throws Exception {
        if ("Back".equals(name)) {
            menus.openHub(player);
            return true;
        }
        Map<Integer, String> meta = menus.clickMeta.getOrDefault(player.getUniqueId(), Map.of());
        String kit = meta.get(slot);
        if (kit == null) {
            return true;
        }
        player.closeInventory();
        player.performCommand("kit " + kit);
        return true;
    }

    boolean jobsClick(Player player, int slot, String name) throws Exception {
        if ("Back".equals(name)) {
            menus.openHub(player);
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
                player.sendMessage("§cNo permission for that job.");
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

    boolean auctionsClick(Player player, int slot, String name) throws Exception {
        if ("Back".equals(name)) {
            menus.openHub(player);
            return true;
        }
        if (name.startsWith("Sell")) {
            player.closeInventory();
            player.sendMessage("§7Hold an item and use §f/ah sell <price>");
            return true;
        }
        Map<Integer, String> meta = menus.clickMeta.getOrDefault(player.getUniqueId(), Map.of());
        String action = meta.get(slot);
        if (action == null || !action.startsWith("buy:")) {
            return true;
        }
        long id = Long.parseLong(action.substring(4));
        player.closeInventory();
        player.performCommand("ah buy " + id);
        return true;
    }

    boolean mailClick(Player player, String name) throws Exception {
        if ("Back".equals(name)) {
            menus.openHub(player);
            return true;
        }
        if ("Clear all".equals(name)) {
            menus.mail.clear(player.getUniqueId());
            player.sendMessage("§aMail cleared.");
            menus.openMail(player);
        }
        return true;
    }

    boolean claimsClick(Player player, int slot, boolean shift, String name) throws Exception {
        if (menus.claims == null) {
            return true;
        }
        if ("Back".equals(name)) {
            menus.openHub(player);
            return true;
        }
        Map<Integer, String> meta = menus.clickMeta.getOrDefault(player.getUniqueId(), Map.of());
        String idStr = meta.get(slot);
        if (idStr == null) {
            return true;
        }
        long id = Long.parseLong(idStr);
        var opt = menus.claims.repo().get(id);
        if (opt.isEmpty()) {
            menus.openClaims(player);
            return true;
        }
        Claim c = opt.get();
        if (shift) {
            if (menus.claims.abandon(player, c)) {
                player.sendMessage("§aAbandoned claim §f#" + id);
            } else {
                player.sendMessage("§cCannot abandon.");
            }
            menus.openClaims(player);
            return true;
        }
        player.closeInventory();
        if (c.serverId().equals(menus.config.serverId()) && player.getWorld().getName().equals(c.world())) {
            ClaimVisualizer.show(menus.plugin, player, c, menus.config.claimsVisualSeconds());
            player.sendMessage("§aShowing claim §f#" + id);
        } else {
            player.sendMessage("§cClaim is on §f" + c.serverId() + "/" + c.world());
        }
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
