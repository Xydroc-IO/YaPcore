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
                ? "Player: " + YapStaffClient.session().targetName()
                : "No player selected";
        addSubtitle(target);
        addTargetBar();

        addSection("Player");
        addButtonGrid(
                action("Go to player", "Teleport to selected player", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        StaffCmds.runFmt("yapadmin tp %s", p);
                    }
                }),
                action("Bring here", "Teleport player to you", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        StaffCmds.runFmt("yapadmin tphere %s", p);
                    }
                }),
                action("Send to spawn", "Send player to world spawn", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        StaffCmds.runFmt("yapadmin tpspawn %s", p);
                    }
                })
        );

        addSection("Self");
        addButtonGrid(
                action("Spawn", "Go to spawn", () -> StaffCmds.run("spawn")),
                action("Back", "Return to previous location", () -> StaffCmds.run("back")),
                action("Nearby", "List nearby players", () -> StaffCmds.run("near")),
                action("World panel", "Open YaPWorld chest GUI", () -> {
                    closeToGame();
                    StaffCmds.run("yapworld gui");
                }),
                action("World edit…", "Native world edit tools", () ->
                        open(new WorldEditHubScreen(this)))
        );
    }
}
