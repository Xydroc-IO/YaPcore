package com.yapcore.perms.gui;

import com.yapcore.bedrock.ui.BedrockFormResult;
import com.yapcore.bedrock.ui.BedrockUiService;
import com.yapcore.bedrock.ui.BedrockUiServices;
import com.yapcore.messages.YapMessages;
import com.yapcore.perms.EffectiveUser;
import com.yapcore.perms.PermsPlugin;
import com.yapcore.perms.db.PermsRepository;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Soft-dep Bedrock form hub: when the player is Bedrock (native UDP or Floodgate-only),
 * show a simple form instead of the JE chest GUI.
 */
public final class PermsBedrockForms {

    private PermsBedrockForms() {
    }

    /** @return true if a Bedrock form was opened (caller should skip chest GUI). */
    public static boolean tryOpenHub(PermsPlugin plugin, Player player) {
        Optional<BedrockUiService> uiOpt = BedrockUiServices.find();
        if (uiOpt.isEmpty()) {
            return false;
        }
        BedrockUiService ui = uiOpt.get();
        if (!ui.isBedrock(player)) {
            return false;
        }

        RanksGui gui = plugin.ranksGui();
        EffectiveUser self = plugin.resolve(player.getUniqueId(), player.getName());
        List<String> buttons = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        buttons.add("Your info");
        actions.add(() -> ui.sendModalForm(
                player,
                "Your rank",
                "Primary: " + self.primaryGroup()
                        + "\nDisplay: " + self.displayGroup()
                        + "\nWeight: " + self.weight()
                        + "\nPrefix: " + strip(self.prefix()),
                "Back",
                "Close",
                result -> {
                    if (result != null && !result.cancelled() && result.buttonIndex() == 0) {
                        if (!tryOpenHub(plugin, player)) {
                            gui.openHubInventory(player);
                        }
                    }
                }));

        buttons.add("View groups");
        actions.add(() -> {
            if (!tryOpenGroups(plugin, player)) {
                gui.openGroupsInventory(player);
            }
        });

        if (player.hasPermission("yapperm.admin")) {
            buttons.add("Online players");
            actions.add(() -> {
                if (!tryOpenOnlinePlayers(plugin, player)) {
                    gui.openOnlinePlayersInventory(player);
                }
            });
        }

        buttons.add("Promote tip");
        actions.add(() -> {
            player.sendMessage("§eUsage: §f/promote <player> §7or §f/demote <player>");
            player.sendMessage("§7Or open §fOnline players §7to set a group.");
        });

        if (player.hasPermission("yapperm.admin")) {
            buttons.add("Apply starter pack");
            actions.add(() -> YapSched.async(plugin, () -> {
                try {
                    plugin.repository().applyStarterPackFromConfig();
                    YapSched.global(plugin, () -> {
                        plugin.reloadAll();
                        player.sendMessage("§aStarter rank pack applied.");
                    });
                } catch (Exception e) {
                    YapSched.global(plugin, () ->
                            player.sendMessage("§cApply pack failed: " + e.getMessage()));
                }
            }));
            buttons.add("Reload");
            actions.add(() -> {
                plugin.reloadAll();
                YapMessages.reloaded(player, "YaPPerms");
                tryOpenHub(plugin, player);
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
                "YaP Ranks",
                content,
                result -> handleIndexed(plugin, player, result, actions),
                buttons.toArray(String[]::new));
        return id >= 0;
    }

    static boolean tryOpenGroups(PermsPlugin plugin, Player player) {
        Optional<BedrockUiService> uiOpt = BedrockUiServices.find();
        if (uiOpt.isEmpty() || !uiOpt.get().isBedrock(player)) {
            return false;
        }
        BedrockUiService ui = uiOpt.get();
        RanksGui gui = plugin.ranksGui();

        List<PermsRepository.GroupRow> groups = new ArrayList<>(plugin.resolver().groups().values());
        groups.sort(Comparator.comparingInt(PermsRepository.GroupRow::weight));
        List<String> buttons = new ArrayList<>();
        List<PermsRepository.GroupRow> shown = new ArrayList<>();
        for (PermsRepository.GroupRow group : groups) {
            if (buttons.size() >= 20) {
                break;
            }
            shown.add(group);
            buttons.add(group.name() + " (w" + group.weight() + ")");
        }
        buttons.add("Back");
        buttons.add("Close");

        int id = ui.sendSimpleForm(
                player,
                "Rank groups",
                "Tap a group for details.",
                result -> {
                    if (result == null || result.cancelled()) {
                        return;
                    }
                    int idx = result.buttonIndex();
                    if (idx < 0) {
                        return;
                    }
                    if (idx < shown.size()) {
                        PermsRepository.GroupRow row = shown.get(idx);
                        player.sendMessage("§6" + row.name() + " §7weight §f" + row.weight());
                        player.sendMessage("§7Prefix: §r" + row.prefix() + "Name" + row.suffix());
                        player.sendMessage("§7Parents: §f"
                                + (row.parents().isEmpty() ? "—" : String.join(", ", row.parents())));
                        int n = 0;
                        for (var node : row.nodes()) {
                            if (n++ >= 12) {
                                player.sendMessage("§8…");
                                break;
                            }
                            player.sendMessage("  §7" + node.node() + " §f= §a" + node.value()
                                    + (node.world().isBlank() ? "" : " §8world=" + node.world())
                                    + (node.temporary() ? " §8temp" : ""));
                        }
                        return;
                    }
                    if (idx == shown.size()) {
                        if (!tryOpenHub(plugin, player)) {
                            gui.openHubInventory(player);
                        }
                    }
                },
                buttons.toArray(String[]::new));
        return id >= 0;
    }

    static boolean tryOpenOnlinePlayers(PermsPlugin plugin, Player player) {
        if (!player.hasPermission("yapperm.admin")) {
            YapMessages.noPermission(player, "yapperm.admin");
            return true;
        }
        Optional<BedrockUiService> uiOpt = BedrockUiServices.find();
        if (uiOpt.isEmpty() || !uiOpt.get().isBedrock(player)) {
            return false;
        }
        BedrockUiService ui = uiOpt.get();
        RanksGui gui = plugin.ranksGui();

        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        List<String> buttons = new ArrayList<>();
        List<Player> shown = new ArrayList<>();
        for (Player target : online) {
            if (buttons.size() >= 20) {
                break;
            }
            EffectiveUser eff = plugin.resolve(target.getUniqueId(), target.getName());
            shown.add(target);
            buttons.add(target.getName() + " [" + eff.primaryGroup() + "]");
        }
        buttons.add("Back");
        buttons.add("Close");

        int id = ui.sendSimpleForm(
                player,
                "Set player group",
                shown.isEmpty() ? "No online players." : "Tap a player to assign a group.",
                result -> {
                    if (result == null || result.cancelled()) {
                        return;
                    }
                    int idx = result.buttonIndex();
                    if (idx < 0) {
                        return;
                    }
                    if (idx < shown.size()) {
                        Player target = shown.get(idx);
                        if (!tryOpenPickGroup(plugin, player, target.getUniqueId(), target.getName())) {
                            gui.openPickGroupInventory(player, target.getUniqueId(), target.getName());
                        }
                        return;
                    }
                    if (idx == shown.size()) {
                        if (!tryOpenHub(plugin, player)) {
                            gui.openHubInventory(player);
                        }
                    }
                },
                buttons.toArray(String[]::new));
        return id >= 0;
    }

    static boolean tryOpenPickGroup(PermsPlugin plugin, Player admin,
                                    UUID targetUuid, String targetName) {
        Optional<BedrockUiService> uiOpt = BedrockUiServices.find();
        if (uiOpt.isEmpty() || !uiOpt.get().isBedrock(admin)) {
            return false;
        }
        BedrockUiService ui = uiOpt.get();
        RanksGui gui = plugin.ranksGui();

        EffectiveUser eff = plugin.resolve(targetUuid, targetName);
        List<PermsRepository.GroupRow> groups = new ArrayList<>(plugin.resolver().groups().values());
        groups.sort(Comparator.comparingInt(PermsRepository.GroupRow::weight));
        List<String> buttons = new ArrayList<>();
        List<String> groupNames = new ArrayList<>();
        for (PermsRepository.GroupRow group : groups) {
            if (buttons.size() >= 20) {
                break;
            }
            boolean current = group.name().equalsIgnoreCase(eff.primaryGroup());
            groupNames.add(group.name());
            buttons.add((current ? "* " : "") + group.name() + " (w" + group.weight() + ")");
        }
        buttons.add("Back");
        buttons.add("Close");

        int id = ui.sendSimpleForm(
                admin,
                "Group → " + targetName,
                "Current: " + eff.primaryGroup(),
                result -> {
                    if (result == null || result.cancelled()) {
                        return;
                    }
                    int idx = result.buttonIndex();
                    if (idx < 0) {
                        return;
                    }
                    if (idx < groupNames.size()) {
                        String group = groupNames.get(idx).toLowerCase(Locale.ROOT);
                        YapSched.async(plugin, () -> {
                            try {
                                plugin.repository().setPrimaryGroup(targetUuid, targetName, group);
                                YapSched.global(plugin, () -> {
                                    plugin.refreshOnline(targetUuid);
                                    admin.sendMessage("§aSet §f" + targetName + " §ato §f" + group);
                                    if (!tryOpenHub(plugin, admin)) {
                                        gui.openHubInventory(admin);
                                    }
                                });
                            } catch (Exception e) {
                                YapSched.global(plugin, () ->
                                        admin.sendMessage("§cFailed: " + e.getMessage()));
                            }
                        });
                        return;
                    }
                    if (idx == groupNames.size()) {
                        if (!tryOpenOnlinePlayers(plugin, admin)) {
                            gui.openOnlinePlayersInventory(admin);
                        }
                    }
                },
                buttons.toArray(String[]::new));
        return id >= 0;
    }

    private static void handleIndexed(PermsPlugin plugin, Player player,
                                      BedrockFormResult result, List<Runnable> actions) {
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
            plugin.getLogger().log(Level.FINE, "bedrock ranks hub", e);
            player.sendMessage("§cRanks action failed: " + e.getMessage());
            plugin.ranksGui().openHubInventory(player);
        }
    }

    private static String strip(String colored) {
        if (colored == null || colored.isBlank()) {
            return "—";
        }
        return colored.replaceAll("§[0-9a-fk-or]", "");
    }
}
