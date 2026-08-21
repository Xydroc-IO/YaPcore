package org.bukkit.command;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

public interface CommandSender extends Audience {
    void sendMessage(String message);

    void sendMessage(String... messages);

    String getName();

    boolean isOp();

    @Override
    default void sendMessage(Component message) {
        sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(message));
    }
}
