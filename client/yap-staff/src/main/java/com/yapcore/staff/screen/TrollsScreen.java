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
                ? YapStaffClient.session().targetName()
                : null;
        addSubtitle(target == null
                ? "Pick a player first."
                : "Trolling: " + target + "  ·  needs yapadmin.troll");
        addTargetBar();

        addButtonGrid(
                troll("Smite", "smite", "Lightning strike"),
                troll("Launch", "launch", "Yeet upward"),
                troll("Burn", "burn", "Fire 8 seconds"),
                troll("Rocket", "rocket", "High launch + bolt FX"),
                troll("Squash", "squash", "Drop from height"),
                troll("Blind", "blind", "Blindness 12s"),
                troll("Confuse", "confuse", "Nausea 15s"),
                troll("Slap", "slap", "Knock away"),
                troll("Drop hand", "drop", "Force-drop held item"),
                action("Freeze", "Essentials freeze toggle", () -> {
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
                StaffCmds.runFmt("yapadmin troll %s %s", type, p);
            }
        });
    }
}
