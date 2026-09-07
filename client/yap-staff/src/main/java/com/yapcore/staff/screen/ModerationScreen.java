package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import com.yapcore.staff.YapStaffClient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ModerationScreen extends StaffPanelScreen {

    public ModerationScreen(Screen parent) {
        super(Component.literal("Moderation"), parent);
    }

    @Override
    protected void addContents() {
        String target = YapStaffClient.session().hasTarget()
                ? YapStaffClient.session().targetName()
                : null;
        addSubtitle(target == null ? "Pick a player first." : "Target: " + target);
        addTargetBar();

        addButtonGrid(
                action("Kick", "yapmod.kick", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        StaffCmds.runFmt("yapadmin kick %s Staff action", p);
                    }
                }),
                action("Warn", "yapmod.warn", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        StaffCmds.runFmt("yapadmin warn %s Staff action", p);
                    }
                }),
                action("Mute 1h", "yapmod.mute", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        StaffCmds.runFmt("yapadmin mute %s Staff action", p);
                    }
                }),
                action("Tempban 1d", "yapmod.ban", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        StaffCmds.runFmt("yapadmin tempban %s Staff action", p);
                    }
                }),
                action("Unmute", "/unmute", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        StaffCmds.runFmt("unmute %s", p);
                    }
                }),
                action("Unban", "/unban", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        StaffCmds.runFmt("unban %s", p);
                    }
                }),
                action("Banlist", "/banlist", () -> StaffCmds.run("banlist")),
                action("Check", "/check", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        StaffCmds.runFmt("check %s", p);
                    }
                }),
                action("Mod history", "/modhistory", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        StaffCmds.runFmt("modhistory %s", p);
                    }
                }),
                action("Alts", "/alts", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        StaffCmds.runFmt("alts %s", p);
                    }
                }),
                action("Protect lookup", "/yapprotect lookup", () -> {
                    if (minecraft != null) {
                        closeToGame();
                    }
                    StaffCmds.run("yapprotect lookup");
                })
        );
    }
}
