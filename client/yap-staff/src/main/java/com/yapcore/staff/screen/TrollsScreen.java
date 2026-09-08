package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import com.yapcore.staff.YapStaffClient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TrollsScreen extends StaffPanelScreen {

    public TrollsScreen(Screen parent) {
        super(Component.literal("Trolls"), parent);
    }

    @Override
    protected void addContents() {
        String target = YapStaffClient.session().hasTarget()
                ? "Player: " + YapStaffClient.session().targetName()
                : "No player selected";
        addSubtitle(target);
        addTargetBar();

        addButtonGrid(
                troll("Smite", "smite", "Lightning strike"),
                troll("Launch", "launch", "Launch upward"),
                troll("Burn", "burn", "Set on fire briefly"),
                troll("Rocket", "rocket", "High launch with lightning"),
                troll("Squash", "squash", "Drop from height"),
                troll("Blind", "blind", "Blindness for 12 seconds"),
                troll("Confuse", "confuse", "Nausea for 15 seconds"),
                troll("Slap", "slap", "Knock away"),
                troll("Drop hand", "drop", "Force-drop held item"),
                action("Freeze", "Toggle freeze", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        StaffCmds.runFmt("freeze %s", p);
                    }
                })
        );
    }

    private net.minecraft.client.gui.components.Button troll(String label, String type, String tip) {
        return action(label, tip, () -> {
            String p = StaffCmds.requireTarget();
            if (p != null) {
                StaffCmds.troll(type, p);
            }
        });
    }
}
