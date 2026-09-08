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
                "Give",
                "Server",
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
                case 1 -> plugin.menus().openSelfTools(player);
                case 2 -> {
                    if (player.hasPermission("yapadmin.give")) {
                        plugin.menus().openGiveHub(player);
                    } else {
                        YapMessages.noPermission(player, "yapadmin.give");
                    }
                }
                case 3 -> {
                    if (player.hasPermission("yapadmin.server")) {
                        plugin.menus().openServerOps(player);
                    } else {
                        YapMessages.noPermission(player, "yapadmin.server");
                    }
                }
                default -> {
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "bedrock admin hub", e);
            player.sendMessage("§cAdmin action failed: " + e.getMessage());
            plugin.menus().openHubInventory(player);
        }
    }
}
