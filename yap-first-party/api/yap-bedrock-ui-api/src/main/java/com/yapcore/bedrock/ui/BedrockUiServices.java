package com.yapcore.bedrock.ui;

import org.bukkit.Bukkit;

import java.util.Optional;

public final class BedrockUiServices {

    private BedrockUiServices() {
    }

    public static Optional<BedrockUiService> find() {
        try {
            if (Bukkit.getServer() != null) {
                var reg = Bukkit.getServicesManager().getRegistration(BedrockUiService.class);
                if (reg != null) {
                    return Optional.of(reg.getProvider());
                }
            }
        } catch (Throwable ignored) {
            // Early boot / unit tests without a live Bukkit server
        }
        return BedrockUiBackend.get();
    }
}
