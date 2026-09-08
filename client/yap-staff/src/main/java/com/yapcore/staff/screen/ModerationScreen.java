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
                ? "Player: " + YapStaffClient.session().targetName()
                : "No player selected";
        addSubtitle(target);
        addTargetBar();

        addSection("Actions");
        addButtonGrid(
                action("Kick", "Remove from the server", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        StaffCmds.mod("kick", p, "Staff action");
                    }
                }),
                action("Warn", "Record a warning", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        StaffCmds.mod("warn", p, "Staff action");
                    }
                }),
                action("Mute 1h", "Mute for one hour", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        StaffCmds.mod("mute", p, "Staff action");
                    }
                }),
                action("Tempban 1d", "Ban for one day", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        StaffCmds.mod("tempban", p, "Staff action");
                    }
                }),
                action("Unmute", "Clear active mute", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        StaffCmds.runFmt("unmute %s", p);
                    }
                }),
                action("Unban", "Clear active ban", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        StaffCmds.runFmt("unban %s", p);
                    }
                })
        );

        addSection("Lookup");
        addButtonGrid(
                action("Ban list", "Show banned players", () -> StaffCmds.run("banlist")),
                action("Check", "Staff inspect", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        StaffCmds.runFmt("check %s", p);
                    }
                }),
                action("History", "Moderation history", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        StaffCmds.runFmt("modhistory %s", p);
                    }
                }),
                action("Alts", "Possible alternate accounts", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        StaffCmds.runFmt("alts %s", p);
                    }
                }),
                action("Block lookup", "YaPProtect user history", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        closeToGame();
                        StaffCmds.protectLookupUser(p);
                    }
                })
        );
    }
}
