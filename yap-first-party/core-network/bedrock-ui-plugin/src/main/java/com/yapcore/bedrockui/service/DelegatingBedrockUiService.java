package com.yapcore.bedrockui.service;

import com.yapcore.bedrock.ui.BedrockFormResult;
import com.yapcore.bedrock.ui.BedrockUiBackend;
import com.yapcore.bedrock.ui.BedrockUiService;
import com.yapcore.bedrockui.form.FloodgateFormRelay;
import com.yapcore.floodgate.paper.FloodgatePlugin;
import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.List;
import java.util.function.Consumer;

/**
 * Delegates to YaPcore chassis when a native UDP session exists; otherwise uses
 * Floodgate {@code floodgate:form} relay for forms and Paper for action bar / sidebar.
 */
public final class DelegatingBedrockUiService implements BedrockUiService {

    private final JavaPlugin plugin;
    private final ChassisFormAdapter chassis;
    private final FloodgateFormRelay floodgateForms;

    public DelegatingBedrockUiService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.chassis = new ChassisFormAdapter();
        this.floodgateForms = new FloodgateFormRelay(plugin);
        BedrockUiBackend.install(this);
    }

    public void registerChannels() {
        floodgateForms.register();
    }

    public void unregisterChannels() {
        floodgateForms.unregister();
    }

    @Override
    public boolean isBedrock(Player player) {
        if (player == null) {
            return false;
        }
        FloodgatePlugin fg = floodgate();
        if (fg != null && fg.isBedrock(player)) {
            return true;
        }
        return player.getUniqueId().getMostSignificantBits() == 0L;
    }

    @Override
    public boolean hasNativeSession(Player player) {
        return player != null && chassis.hasSession(player.getName());
    }

    @Override
    public void sendActionBar(Player player, String text) {
        if (player == null || text == null || !isBedrock(player)) {
            return;
        }
        YapSched.entity(plugin, player, () -> {
            if (chassis.pushActionBar(player.getName(), text)) {
                return;
            }
            player.sendActionBar(Component.text(stripColor(text)));
        });
    }

    @Override
    public void updateSidebar(Player player, String objectiveId, String title, List<String> lines) {
        if (player == null || lines == null || !isBedrock(player)) {
            return;
        }
        YapSched.entity(plugin, player, () -> {
            if (chassis.pushSidebar(player.getName(), objectiveId, title, lines)) {
                return;
            }
            applyPaperSidebar(player, objectiveId, title, lines);
        });
    }

    @Override
    public int sendSimpleForm(
            Player player,
            String formTitle,
            String content,
            Consumer<BedrockFormResult> onResult,
            String... buttons) {
        if (player == null || !isBedrock(player)) {
            return -1;
        }
        if (hasNativeSession(player)) {
            return chassis.sendSimple(player.getName(), formTitle, content, onResult, buttons);
        }
        return floodgateForms.sendSimple(player, formTitle, content, onResult, buttons);
    }

    @Override
    public int sendCustomForm(
            Player player,
            String formTitle,
            String jsonContentArray,
            Consumer<BedrockFormResult> onResult) {
        if (player == null || !isBedrock(player)) {
            return -1;
        }
        if (hasNativeSession(player)) {
            return chassis.sendCustom(player.getName(), formTitle, jsonContentArray, onResult);
        }
        return floodgateForms.sendCustom(player, formTitle, jsonContentArray, onResult);
    }

    @Override
    public int sendModalForm(
            Player player,
            String title,
            String content,
            String button1,
            String button2,
            Consumer<BedrockFormResult> onResult) {
        if (player == null || !isBedrock(player)) {
            return -1;
        }
        if (hasNativeSession(player)) {
            return chassis.sendModal(player.getName(), title, content, button1, button2, onResult);
        }
        return floodgateForms.sendModal(player, title, content, button1, button2, onResult);
    }

    private FloodgatePlugin floodgate() {
        var plug = Bukkit.getPluginManager().getPlugin("YaPFloodgate");
        return plug instanceof FloodgatePlugin fg ? fg : null;
    }

    private static void applyPaperSidebar(Player player, String objectiveId, String title, List<String> lines) {
        String objName = objectiveId == null || objectiveId.isBlank() ? "yapmmo" : objectiveId;
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective(
                objName, Criteria.DUMMY, Component.text(title == null ? "YaP MMO" : title));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        int score = lines.size();
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                score--;
                continue;
            }
            String entry = stripColor(line);
            if (entry.length() > 40) {
                entry = entry.substring(0, 40);
            }
            obj.getScore(entry).setScore(score--);
        }
        player.setScoreboard(board);
    }

    private static String stripColor(String text) {
        return text == null ? "" : text.replaceAll("§.", "");
    }
}
