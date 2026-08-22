package com.yapcore.bedrockui;

import com.yapcore.bedrock.ui.BedrockUiBackend;
import com.yapcore.bedrock.ui.BedrockUiService;
import com.yapcore.bedrockui.service.DelegatingBedrockUiService;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class BedrockUiPlugin extends JavaPlugin {

    private DelegatingBedrockUiService service;

    @Override
    public void onEnable() {
        service = new DelegatingBedrockUiService(this);
        getServer().getServicesManager().register(
                BedrockUiService.class, service, this, ServicePriority.Normal);
        getLogger().info("YaP Bedrock UI bridge ready");
    }

    @Override
    public void onDisable() {
        if (service != null) {
            getServer().getServicesManager().unregister(BedrockUiService.class, service);
        }
        BedrockUiBackend.clear();
    }
}
