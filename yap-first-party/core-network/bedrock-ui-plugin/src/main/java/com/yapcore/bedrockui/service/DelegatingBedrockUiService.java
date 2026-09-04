package com.yapcore.bedrockui.service;

import com.yapcore.bedrock.ui.BedrockFormResult;
import com.yapcore.bedrock.ui.BedrockUiBackend;
import com.yapcore.bedrock.ui.BedrockUiService;
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

/** Delegates to YaPcore chassis when available; otherwise Paper + Floodgate heuristics. */
public final class DelegatingBedrockUiService implements BedrockUiService {

    private final JavaPlugin plugin;
    private final ChassisFormBridge chassis;

    public DelegatingBedrockUiService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.chassis = new ChassisFormBridge();
        BedrockUiBackend.install(this);
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
        if (!hasNativeSession(player)) {
            notifyFormsNeedNative(player);
            return -1;
        }
        return chassis.sendSimple(player.getName(), formTitle, content, onResult, buttons);
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
        if (!hasNativeSession(player)) {
            notifyFormsNeedNative(player);
            return -1;
        }
        return chassis.sendCustom(player.getName(), formTitle, jsonContentArray, onResult);
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
        if (!hasNativeSession(player)) {
            notifyFormsNeedNative(player);
            return -1;
        }
        return chassis.sendModal(player.getName(), title, content, button1, button2, onResult);
    }

    private void notifyFormsNeedNative(Player player) {
        YapSched.entity(plugin, player, () ->
                player.sendMessage("§eBedrock forms need a native YaPcore Bedrock session "
                        + "(UDP dual-stack). Floodgate-only joins use action bar / scoreboard fallback."));
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
