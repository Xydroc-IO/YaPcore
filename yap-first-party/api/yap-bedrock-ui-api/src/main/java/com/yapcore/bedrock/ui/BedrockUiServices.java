package com.yapcore.bedrock.ui;

import org.bukkit.Bukkit;

import java.util.Optional;

public final class BedrockUiServices {

    private BedrockUiServices() {
    }

    public static Optional<BedrockUiService> find() {
        var reg = Bukkit.getServicesManager().getRegistration(BedrockUiService.class);
        if (reg != null) {
            return Optional.of(reg.getProvider());
        }
        return BedrockUiBackend.get();
    }
}
