package com.yapcore.playerdata.gui;

import com.yapcore.bedrock.ui.BedrockFormResult;
import com.yapcore.bedrock.ui.BedrockUiService;
import com.yapcore.bedrock.ui.BedrockUiServices;
import com.yapcore.playerdata.cmd.Perms;
import com.yapcore.playerdata.db.LocationRow;
import com.yapcore.playerdata.kit.CooldownFormat;
import com.yapcore.playerdata.kit.KitDef;
import com.yapcore.playerdata.util.Teleports;
import com.yapcore.sched.YapSched;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Soft-dep Bedrock form hubs for /menu, /kits, /homes, /warps.
 * When YaPBedrockUI is present and the player is Bedrock, prefer forms over JE chest GUIs.
 */
public final class PlayerDataBedrockForms {

    private PlayerDataBedrockForms() {
    }

    /** @return true if a Bedrock form was opened (caller should skip chest GUI). */
    public static boolean tryOpenHub(Menus menus, Player player) {
        Optional<BedrockUiService> uiOpt = BedrockUiServices.find();
        if (uiOpt.isEmpty()) {
            return false;
        }
        BedrockUiService ui = uiOpt.get();
        if (!ui.isBedrock(player)) {
            return false;
        }

        List<String> buttons = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        if (menus.config.economyEnabled()) {
            double bal = menus.balances.getBalance(player.getUniqueId());
            buttons.add("Balance ($" + String.format("%.2f", bal) + ")");
            actions.add(() -> showBalance(menus, ui, player, bal));
        }
        if (menus.config.featureBackpack() && menus.backpack != null && player.hasPermission("yapdata.bag")) {
            buttons.add("Bag");
            actions.add(() -> {
                player.sendMessage("§aOpening bag — use §f/bag §aanytime.");
                menus.backpack.openOwn(player, 1);
            });
        }
        if (menus.config.featureHomes() && player.hasPermission("yapdata.home")) {
            buttons.add("Homes");
            actions.add(() -> {
                if (!tryOpenHomes(menus, player)) {
                    menus.openHomesInventory(player);
                }
            });
        }
        if (menus.config.featureWarps() && player.hasPermission("yapdata.warp")) {
            buttons.add("Warps");
            actions.add(() -> {
                if (!tryOpenWarps(menus, player)) {
                    menus.openWarpsInventory(player);
                }
            });
        }
        if (menus.config.featureKits() && player.hasPermission("yapdata.kit")) {
            buttons.add("Kits");
            actions.add(() -> {
                if (!tryOpenKits(menus, player)) {
                    menus.openKitsInventory(player);
                }
            });
        }
        if (menus.config.featureJobs() && player.hasPermission("yapdata.jobs")) {
            buttons.add("Jobs");
            actions.add(() -> menus.openJobs(player));
        }
        if (menus.config.featureAuctions() && player.hasPermission("yapdata.ah")) {
            buttons.add("Auctions");
            actions.add(() -> menus.openAuctions(player));
        }
        if (menus.config.featureMail() && player.hasPermission("yapdata.mail")) {
            buttons.add("Mail");
            actions.add(() -> menus.openMail(player));
        }
        if (menus.config.featureClaims() && menus.claims != null && player.hasPermission("yapdata.claim")) {
            buttons.add("Claims");
            actions.add(() -> menus.openClaims(player));
        }
        if (player.hasPermission("yapadmin.menu")
                && org.bukkit.Bukkit.getPluginManager().getPlugin("YaPAdmin") != null) {
            buttons.add("Staff");
            actions.add(() -> {
                player.closeInventory();
                YapSched.entity(menus.plugin, player, () -> player.performCommand("yapadmin"));
            });
        }
        buttons.add("Close");
        actions.add(() -> {
        });

        String content = ui.hasNativeSession(player)
                ? "Pick a section (native Bedrock form)."
                : "Pick a section (Floodgate form).";
        int id = ui.sendSimpleForm(
                player,
                "YaP Menu",
                content,
                result -> handleIndexed(menus, player, result, actions),
                buttons.toArray(String[]::new));
        return id >= 0;
    }

    /** @return true if a Bedrock form was opened. */
    public static boolean tryOpenKits(Menus menus, Player player) {
        Optional<BedrockUiService> uiOpt = BedrockUiServices.find();
        if (uiOpt.isEmpty()) {
            return false;
        }
        BedrockUiService ui = uiOpt.get();
        if (!ui.isBedrock(player)) {
            return false;
        }

        List<String> buttons = new ArrayList<>();
        List<String> kitIds = new ArrayList<>();
        for (var entry : menus.config.kits().entrySet()) {
            if (!Perms.hasKit(player, entry.getKey())) {
                continue;
            }
            if (buttons.size() >= 20) {
                break;
            }
            KitDef def = entry.getValue();
            String remain = "Ready";
            try {
                var last = menus.kits.lastClaim(player.getUniqueId(), def.id());
                if (last.isPresent() && def.delaySeconds() > 0) {
                    long secs = java.time.Duration.between(java.time.Instant.now(),
                            last.get().plusSeconds(def.delaySeconds())).getSeconds();
                    if (secs > 0) {
                        remain = "CD " + CooldownFormat.formatSeconds(secs);
                    }
                }
            } catch (Exception ignored) {
            }
            String cost = def.cost() > 0 ? " · $" + String.format("%.2f", def.cost()) : "";
            buttons.add(entry.getKey() + " (" + remain + cost + ")");
            kitIds.add(entry.getKey());
        }
        buttons.add("Back");
        buttons.add("Close");

        String content = kitIds.isEmpty()
                ? "No kits available."
                : "Tap a kit to claim.";
        int id = ui.sendSimpleForm(
                player,
                "Kits",
                content,
                result -> handleKitsResult(menus, player, result, kitIds),
                buttons.toArray(String[]::new));
        return id >= 0;
    }

    /** @return true if a Bedrock form was opened. */
    public static boolean tryOpenHomes(Menus menus, Player player) {
        Optional<BedrockUiService> uiOpt = BedrockUiServices.find();
        if (uiOpt.isEmpty()) {
            return false;
        }
        BedrockUiService ui = uiOpt.get();
        if (!ui.isBedrock(player)) {
            return false;
        }

        List<String> names = new ArrayList<>();
        List<String> buttons = new ArrayList<>();
        try {
            for (LocationRow h : menus.homes.list(player.getUniqueId())) {
                if (buttons.size() >= 20) {
                    break;
                }
                names.add(h.name());
                buttons.add(h.name() + " (" + h.serverId() + ")");
            }
        } catch (Exception e) {
            menus.plugin.getLogger().log(Level.WARNING, "homes bedrock form", e);
            return false;
        }
        buttons.add("Back");
        buttons.add("Close");

        int id = ui.sendSimpleForm(
                player,
                "Homes",
                names.isEmpty() ? "No homes set. Use /sethome." : "Tap a home to teleport.",
                result -> handleHomesResult(menus, player, result, names),
                buttons.toArray(String[]::new));
        return id >= 0;
    }

    /** @return true if a Bedrock form was opened. */
    public static boolean tryOpenWarps(Menus menus, Player player) {
        Optional<BedrockUiService> uiOpt = BedrockUiServices.find();
        if (uiOpt.isEmpty()) {
            return false;
        }
        BedrockUiService ui = uiOpt.get();
        if (!ui.isBedrock(player)) {
            return false;
        }

        List<String> names = new ArrayList<>();
        List<String> buttons = new ArrayList<>();
        try {
            for (LocationRow w : menus.warps.list()) {
                if (buttons.size() >= 20) {
                    break;
                }
                names.add(w.name());
                buttons.add(w.name() + " (" + w.serverId() + ")");
            }
        } catch (Exception e) {
            menus.plugin.getLogger().log(Level.WARNING, "warps bedrock form", e);
            return false;
        }
        buttons.add("Back");
        buttons.add("Close");

        int id = ui.sendSimpleForm(
                player,
                "Warps",
                names.isEmpty() ? "No warps." : "Tap a warp to teleport.",
                result -> handleWarpsResult(menus, player, result, names),
                buttons.toArray(String[]::new));
        return id >= 0;
    }

    private static void showBalance(Menus menus, BedrockUiService ui, Player player, double bal) {
        ui.sendModalForm(
                player,
                "Balance",
                "Your balance: $" + String.format("%.2f", bal)
                        + "\nProfile: " + menus.config.inventoryProfile()
                        + "\n\nUse /pay <player> <amount> to send money.",
                "Back",
                "Close",
                result -> {
                    if (result != null && !result.cancelled() && result.buttonIndex() == 0) {
                        if (!tryOpenHub(menus, player)) {
                            menus.openHubInventory(player);
                        }
                    }
                });
    }

    private static void handleIndexed(Menus menus, Player player, BedrockFormResult result,
                                      List<Runnable> actions) {
        if (result == null || result.cancelled()) {
            return;
        }
        int idx = result.buttonIndex();
        if (idx < 0 || idx >= actions.size()) {
            return;
        }
        try {
            actions.get(idx).run();
        } catch (Exception e) {
            menus.plugin.getLogger().log(Level.FINE, "bedrock menu hub", e);
            player.sendMessage("§cMenu action failed: " + e.getMessage());
            menus.openHubInventory(player);
        }
    }

    private static void handleKitsResult(Menus menus, Player player, BedrockFormResult result,
                                         List<String> kitIds) {
        if (result == null || result.cancelled()) {
            return;
        }
        int idx = result.buttonIndex();
        if (idx < 0) {
            return;
        }
        if (idx < kitIds.size()) {
            String kit = kitIds.get(idx);
            YapSched.entity(menus.plugin, player, () -> player.performCommand("kit " + kit));
            return;
        }
        if (idx == kitIds.size()) {
            // Back
            if (!tryOpenHub(menus, player)) {
                menus.openHubInventory(player);
            }
        }
        // Close = ignore
    }

    private static void handleHomesResult(Menus menus, Player player, BedrockFormResult result,
                                          List<String> names) {
        if (result == null || result.cancelled()) {
            return;
        }
        int idx = result.buttonIndex();
        if (idx < 0) {
            return;
        }
        if (idx < names.size()) {
            String home = names.get(idx);
            try {
                var opt = menus.homes.get(player.getUniqueId(), home);
                if (opt.isPresent() && Teleports.tryTeleport(player, opt.get(), menus.config.serverId())) {
                    player.sendMessage("§aTeleported to §f" + home);
                } else if (opt.isEmpty()) {
                    player.sendMessage("§cUnknown home §f" + home);
                }
            } catch (Exception e) {
                player.sendMessage("§cHome failed: " + e.getMessage());
            }
            return;
        }
        if (idx == names.size()) {
            if (!tryOpenHub(menus, player)) {
                menus.openHubInventory(player);
            }
        }
    }

    private static void handleWarpsResult(Menus menus, Player player, BedrockFormResult result,
                                          List<String> names) {
        if (result == null || result.cancelled()) {
            return;
        }
        int idx = result.buttonIndex();
        if (idx < 0) {
            return;
        }
        if (idx < names.size()) {
            String warp = names.get(idx);
            try {
                var opt = menus.warps.get(warp);
                if (opt.isPresent() && Teleports.tryTeleport(player, opt.get(), menus.config.serverId())) {
                    player.sendMessage("§aWarped to §f" + warp);
                } else if (opt.isEmpty()) {
                    player.sendMessage("§cUnknown warp §f" + warp);
                }
            } catch (Exception e) {
                player.sendMessage("§cWarp failed: " + e.getMessage());
            }
            return;
        }
        if (idx == names.size()) {
            if (!tryOpenHub(menus, player)) {
                menus.openHubInventory(player);
            }
        }
    }
}
