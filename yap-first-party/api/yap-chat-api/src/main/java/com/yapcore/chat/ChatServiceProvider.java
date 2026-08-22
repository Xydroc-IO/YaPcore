package com.yapcore.chat;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

public final class ChatServiceProvider {

    private ChatServiceProvider() {
    }

    public static Optional<ChatService> find() {
        RegisteredServiceProvider<ChatService> rsp =
                Bukkit.getServicesManager().getRegistration(ChatService.class);
        if (rsp == null) {
            return Optional.empty();
        }
        ChatService service = rsp.getProvider();
        return service == null ? Optional.empty() : Optional.of(service);
    }
}
