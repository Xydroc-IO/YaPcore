package com.yapcore.link.api;

/** Console/player command handler for YaP Link plugins. */
public interface SimpleCommand {

    void execute(CommandSource source, String[] args);

    default boolean hasPermission(CommandSource source) {
        return true;
    }

    interface CommandSource {
        String name();

        boolean isPlayer();

        default LinkPlayer asPlayer() {
            throw new IllegalStateException("Not a player");
        }

        void sendMessage(String legacyText);
    }
}
