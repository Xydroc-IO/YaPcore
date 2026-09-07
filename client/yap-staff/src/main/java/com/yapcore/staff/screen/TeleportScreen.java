package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import com.yapcore.staff.YapStaffClient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TeleportScreen extends StaffPanelScreen {

    public TeleportScreen(Screen parent) {
        super(Component.literal("Teleport"), parent);
    }

    @Override
    protected void addContents() {
        String target = YapStaffClient.session().hasTarget()
                ? YapStaffClient.session().targetName()
                : null;
        addSubtitle(target == null ? "Pick a player for target TPs." : "Target: " + target);
        addTargetBar();

        addButtonGrid(
                action("TP to player", "Requires target", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        StaffCmds.runFmt("yapadmin tp %s", p);
                    }
                }),
                action("Bring player here", "Requires target", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        StaffCmds.runFmt("yapadmin tphere %s", p);
                    }
                }),
                action("Send to spawn", "Requires target", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        StaffCmds.runFmt("yapadmin tpspawn %s", p);
                    }
                }),
                action("My spawn", "/spawn", () -> StaffCmds.run("spawn")),
                action("Back", "/back", () -> StaffCmds.run("back")),
                action("World GUI", "/yapworld gui", () -> {
                    if (minecraft != null) {
                        closeToGame();
                    }
                    StaffCmds.run("yapworld gui");
                }),
                action("Near players", "/near", () -> StaffCmds.run("near"))
        );
    }
}
