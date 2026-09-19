package com.yapcore.chat;

import com.yapcore.lib.packet.PacketContainer;

import java.util.Locale;
import java.util.Set;

/** Read serverbound chat / command text from a packet handle (no NMS required). */
public final class ChatPacketText {

    static final Set<String> MUTE_ALLOWED_COMMANDS = Set.of(
            "/login", "/l", "/register", "/reg", "/logout", "/changepassword", "/changepass", "/cp");

    private ChatPacketText() {
    }

    public static String read(PacketContainer packet) {
        if (packet == null) {
            return null;
        }
        String text = packet.readOrNull(String.class, 0);
        if (text != null && !text.isBlank()) {
            return text;
        }
        Object handle = packet.handle();
        if (handle instanceof String s && !s.isBlank()) {
            return s;
        }
        return null;
    }

    public static boolean commandAllowedWhenMuted(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return false;
        }
        String msg = commandLine.trim();
        if (!msg.startsWith("/")) {
            msg = "/" + msg;
        }
        msg = msg.toLowerCase(Locale.ROOT);
        for (String allowed : MUTE_ALLOWED_COMMANDS) {
            if (msg.equals(allowed) || msg.startsWith(allowed + " ")) {
                return true;
            }
        }
        return false;
    }
}
