package com.yapcore.mmobedrock;

import com.yapcore.bedrock.ui.BedrockUiService;
import com.yapcore.bedrock.ui.BedrockUiServices;
import com.yapcore.mmo.SkillFeedbackBridge;
import com.yapcore.mmobedrock.cmd.MmoUiCommand;
import com.yapcore.mmobedrock.listener.MmoBedrockListener;
import com.yapcore.mmobedrock.ui.MmoBedrockUi;
import com.yapcore.sched.YapSched;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class MmoBedrockPlugin extends JavaPlugin {

    private MmoBedrockConfig config;
    private MmoBedrockUi ui;
    private MmoBedrockListener listener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new MmoBedrockConfig(this);
        config.reload();

        BedrockUiService bedrock = BedrockUiServices.find().orElse(null);
        if (bedrock == null) {
            getLogger().warning("YaP Bedrock UI not found — install yap-bedrock-ui.jar");
        }

        ui = new MmoBedrockUi(this, config, bedrock != null ? bedrock : noopBedrock());
        listener = new MmoBedrockListener(this, config, ui);
        getServer().getPluginManager().registerEvents(listener, this);
        getServer().getServicesManager().register(
                SkillFeedbackBridge.class, listener, this, ServicePriority.Normal);

        bindCommand("mmoui", new MmoUiCommand(this, ui));

        long ticks = Math.max(20L, config.sidebarRefreshTicks());
        YapSched.globalTimer(this, listener::refreshAllSidebar, ticks, ticks);

        getLogger().info("YaP MMO Bedrock UI ready — bedrockUi=" + (bedrock != null));
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregister(SkillFeedbackBridge.class, listener);
    }

    private static BedrockUiService noopBedrock() {
        return new BedrockUiService() {
            @Override
            public boolean isBedrock(org.bukkit.entity.Player player) {
                return false;
            }

            @Override
            public boolean hasNativeSession(org.bukkit.entity.Player player) {
                return false;
            }

            @Override
            public void sendActionBar(org.bukkit.entity.Player player, String text) {
            }

            @Override
            public void updateSidebar(org.bukkit.entity.Player player, String objectiveId, String title,
                                      java.util.List<String> lines) {
            }

            @Override
            public int sendSimpleForm(org.bukkit.entity.Player player, String title, String content,
                                      java.util.function.Consumer<com.yapcore.bedrock.ui.BedrockFormResult> onResult,
                                      String... buttons) {
                return -1;
            }

            @Override
            public int sendCustomForm(org.bukkit.entity.Player player, String title, String jsonContentArray,
                                      java.util.function.Consumer<com.yapcore.bedrock.ui.BedrockFormResult> onResult) {
                return -1;
            }
        };
    }

    private void bindCommand(String name, Object executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            return;
        }
        cmd.setExecutor((org.bukkit.command.CommandExecutor) executor);
        if (executor instanceof org.bukkit.command.TabCompleter completer) {
            cmd.setTabCompleter(completer);
        }
    }
}
