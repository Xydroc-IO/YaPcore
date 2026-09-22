package com.yapcore.admin.gui;

import com.yapcore.admin.AdminPlugin;
import com.yapcore.bedrock.ui.BedrockFormResult;
import com.yapcore.bedrock.ui.BedrockUiService;
import com.yapcore.bedrock.ui.BedrockUiServices;
import com.yapcore.messages.YapMessages;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.logging.Level;

/**
 * Soft-dep Bedrock form hub: when the player is Bedrock (native UDP or Floodgate-only),
 * show a simple form instead of the JE chest GUI.
 */
public final class AdminBedrockForms {

    private AdminBedrockForms() {
    }

    /** @return true if a Bedrock form was opened (caller should skip chest GUI). */
    public static boolean tryOpenHub(AdminPlugin plugin, Player player) {
        Optional<BedrockUiService> uiOpt = BedrockUiServices.find();
        if (uiOpt.isEmpty()) {
            return false;
        }
        BedrockUiService ui = uiOpt.get();
        if (!ui.isBedrock(player)) {
            return false;
        }
        String content = ui.hasNativeSession(player)
                ? "Network admin tools"
                : "Network admin tools (Floodgate)";
        int id = ui.sendSimpleForm(
                player,
                "YaP Admin",
                content,
                result -> handleHubResult(plugin, player, result),
                "Players",
                "Self tools",
                "Night vision",
                "Give",
                "Server",
                "World tools",
                "YaP420",
                "Close");
        return id >= 0;
    }

    private static void handleHubResult(AdminPlugin plugin, Player player, BedrockFormResult result) {
        if (result == null || result.cancelled()) {
            return;
        }
        try {
            switch (result.buttonIndex()) {
                case 0 -> plugin.menus().openPlayers(player);
                case 1 -> tryOpenSelfTools(plugin, player, uiOrNull(player));
                case 2 -> tryOpenNightVision(plugin, player, uiOrNull(player));
                case 3 -> {
                    if (player.hasPermission("yapadmin.give")) {
                        plugin.menus().openGiveHub(player);
                    } else {
                        YapMessages.noPermission(player, "yapadmin.give");
                    }
                }
                case 4 -> {
                    if (player.hasPermission("yapadmin.server")) {
                        plugin.menus().openServerOps(player);
                    } else {
                        YapMessages.noPermission(player, "yapadmin.server");
                    }
                }
                case 5 -> {
                    if (plugin.actions().pluginEnabled("YaPWorld")) {
                        plugin.menus().openWorldTools(player);
                    } else {
                        player.sendMessage("§cYaPWorld is not loaded.");
                    }
                }
                case 6 -> plugin.menus().openYap420Hub(player);
                default -> {
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "bedrock admin hub", e);
            player.sendMessage("§cAdmin action failed: " + e.getMessage());
            plugin.menus().openHubInventory(player);
        }
    }

    private static BedrockUiService uiOrNull(Player player) {
        return BedrockUiServices.find().orElse(null);
    }

    private static void tryOpenSelfTools(AdminPlugin plugin, Player player, BedrockUiService ui) {
        if (ui == null || !ui.isBedrock(player)) {
            plugin.menus().openSelfTools(player);
            return;
        }
        int id = ui.sendSimpleForm(
                player,
                "Self tools",
                "Actions apply to you",
                result -> handleSelfToolsResult(plugin, player, result),
                "Fly",
                "God",
                "Vanish",
                "Heal",
                "Feed",
                "Night vision…",
                "Survival",
                "Creative",
                "Back");
        if (id < 0) {
            plugin.menus().openSelfTools(player);
        }
    }

    private static void handleSelfToolsResult(AdminPlugin plugin, Player player, BedrockFormResult result) {
        if (result == null || result.cancelled()) {
            return;
        }
        try {
            switch (result.buttonIndex()) {
                case 0 -> plugin.actions().closeAndRun(player, "fly");
                case 1 -> plugin.actions().closeAndRun(player, "god");
                case 2 -> plugin.actions().closeAndRun(player, "vanish");
                case 3 -> plugin.actions().heal(player, player);
                case 4 -> plugin.actions().feed(player, player);
                case 5 -> tryOpenNightVision(plugin, player, uiOrNull(player));
                case 6 -> plugin.actions().closeAndRun(player, "gms");
                case 7 -> plugin.actions().closeAndRun(player, "gmc");
                case 8 -> plugin.menus().openHub(player);
                default -> {
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "bedrock self tools", e);
            plugin.menus().openSelfTools(player);
        }
    }

    private static void tryOpenNightVision(AdminPlugin plugin, Player player, BedrockUiService ui) {
        if (ui == null || !ui.isBedrock(player)) {
            plugin.menus().openNvPicker(player, null);
            return;
        }
        int id = ui.sendSimpleForm(
                player,
                "Night vision",
                "Pick a duration for yourself",
                result -> handleNvResult(plugin, player, result),
                "15 minutes",
                "1 hour",
                "Unlimited",
                "Turn off",
                "Back");
        if (id < 0) {
            plugin.menus().openNvPicker(player, null);
        }
    }

    private static void handleNvResult(AdminPlugin plugin, Player player, BedrockFormResult result) {
        if (result == null || result.cancelled()) {
            return;
        }
        try {
            switch (result.buttonIndex()) {
                case 0 -> plugin.actions().setNightVision(
                        player, player, com.yapcore.admin.action.AdminNightVision.Mode.MINUTES_15);
                case 1 -> plugin.actions().setNightVision(
                        player, player, com.yapcore.admin.action.AdminNightVision.Mode.HOUR);
                case 2 -> plugin.actions().setNightVision(
                        player, player, com.yapcore.admin.action.AdminNightVision.Mode.UNLIMITED);
                case 3 -> plugin.actions().setNightVision(
                        player, player, com.yapcore.admin.action.AdminNightVision.Mode.OFF);
                case 4 -> plugin.menus().openHub(player);
                default -> {
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "bedrock night vision", e);
            plugin.menus().openNvPicker(player, null);
        }
    }
}
