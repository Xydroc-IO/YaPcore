package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import com.yapcore.staff.YapStaffClient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Grant money — searchable player select, then tap an amount. */
public final class EconomyScreen extends StaffPanelScreen {

    private static final int[] AMOUNTS = {100, 500, 1000, 5000, 10000, 50000};

    public EconomyScreen(Screen parent) {
        super(Component.literal("Economy"), parent);
    }

    @Override
    protected void addContents() {
        addSubtitle("Who gets the money?");
        addTargetBar();
        addSubtitle("Amount · yapadmin.economy");
        for (int amount : AMOUNTS) {
            int a = amount;
            addBody(actionWide("Give $" + a, "yapadmin money", () -> {
                var session = YapStaffClient.session();
                if (session.hasTarget()) {
                    StaffCmds.runFmt("yapadmin money %d %s", a, session.targetName());
                } else {
                    StaffCmds.runFmt("yapadmin money %d", a);
                }
            }));
        }
    }
}
