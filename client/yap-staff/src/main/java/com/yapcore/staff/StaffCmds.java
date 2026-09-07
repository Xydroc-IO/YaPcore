package com.yapcore.staff;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Sends staff commands to YaPAdmin / Essentials / Moderation. Server enforces perms. */
public final class StaffCmds {

    private static final long COOLDOWN_MS = 250L;
    private static long lastSendMs;

    private StaffCmds() {
    }

    public static void run(String command) {
        if (command == null || command.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSendMs < COOLDOWN_MS) {
            return;
        }
        lastSendMs = now;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }
        String trimmed = command.startsWith("/") ? command.substring(1) : command;
        Screen screen = minecraft.gui.screen();
        if (screen != null) {
            minecraft.player.connection.sendUnattendedCommand(trimmed, screen);
        } else {
            minecraft.player.connection.sendCommand(trimmed);
        }
    }

    public static void runFmt(String format, Object... args) {
        run(String.format(Locale.ROOT, format, args));
    }

    public static List<String> onlineNames(String filter) {
        Minecraft minecraft = Minecraft.getInstance();
        List<String> names = new ArrayList<>();
        if (minecraft == null || minecraft.getConnection() == null) {
            return names;
        }
        String needle = filter == null ? "" : filter.toLowerCase(Locale.ROOT).trim();
        for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
            String name = info.getProfile().name();
            if (name == null || name.isBlank()) {
                continue;
            }
            if (!needle.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            names.add(name);
        }
        names.sort(Comparator.comparing(n -> n.toLowerCase(Locale.ROOT)));
        return names;
    }

    public static String requireTarget() {
        StaffSession session = YapStaffClient.session();
        if (!session.hasTarget()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal("§cPick a player first."));
            }
            return null;
        }
        return session.targetName();
    }
}
