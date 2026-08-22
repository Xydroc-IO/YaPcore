package com.yapcore.mmobedrock.listener;

import com.yapcore.bedrock.ui.BedrockUiService;
import com.yapcore.bedrock.ui.BedrockUiServices;
import com.yapcore.mmo.SkillFeedbackBridge;
import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.event.SkillLevelUpEvent;
import com.yapcore.mmobedrock.MmoBedrockConfig;
import com.yapcore.mmobedrock.ui.MmoBedrockUi;
import com.yapcore.sched.YapSched;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class MmoBedrockListener implements Listener, SkillFeedbackBridge {

    private final JavaPlugin plugin;
    private final MmoBedrockConfig config;
    private final MmoBedrockUi ui;
    private final BedrockUiService bedrock;

    public MmoBedrockListener(JavaPlugin plugin, MmoBedrockConfig config, MmoBedrockUi ui) {
        this.plugin = plugin;
        this.config = config;
        this.ui = ui;
        this.bedrock = BedrockUiServices.find().orElse(null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSkillsCommand(PlayerCommandPreprocessEvent event) {
        if (!config.interceptSkillsCommand() || bedrock == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!bedrock.isBedrock(player)) {
            return;
        }
        String msg = event.getMessage().toLowerCase().trim();
        if (msg.equals("/skills") || msg.startsWith("/skills ")) {
            event.setCancelled(true);
            YapSched.entity(plugin, player, () -> ui.openHub(player));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (bedrock == null || !bedrock.isBedrock(event.getPlayer())) {
            return;
        }
        YapSched.entityLater(plugin, event.getPlayer(), () -> ui.refreshCombatSidebar(event.getPlayer()), 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // scoreboard clears on quit automatically
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLevelUp(SkillLevelUpEvent event) {
        if (bedrock == null || !bedrock.isBedrock(event.getPlayer())) {
            return;
        }
        YapSched.entity(plugin, event.getPlayer(), () -> ui.refreshCombatSidebar(event.getPlayer()));
    }

    @Override
    public void onXpGain(Player player, SkillId skillId, double amount, String label) {
        if (bedrock == null || !bedrock.isBedrock(player)) {
            return;
        }
        bedrock.sendActionBar(player, label);
    }

    public void refreshAllSidebar() {
        if (bedrock == null) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (bedrock.isBedrock(player)) {
                ui.refreshCombatSidebar(player);
            }
        }
    }
}
