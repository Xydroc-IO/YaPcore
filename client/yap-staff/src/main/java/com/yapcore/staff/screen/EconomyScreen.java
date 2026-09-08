package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import com.yapcore.staff.YapStaffClient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Grant money — select a player, then choose an amount. */
public final class EconomyScreen extends StaffPanelScreen {

    private static final int[] AMOUNTS = {100, 500, 1000, 5000, 10000, 50000};

    public EconomyScreen(Screen parent) {
        super(Component.literal("Economy"), parent);
    }

    @Override
    protected void addContents() {
        addSubtitle("Who receives the grant?");
        addTargetBar();
        addSection("Amount");
        for (int amount : AMOUNTS) {
            int a = amount;
            addBody(actionWide("Give $" + a, "Deposit to selected player (or yourself)", () -> {
                var session = YapStaffClient.session();
                StaffCmds.money(a, session.hasTarget() ? session.targetName() : null);
            }));
        }
    }
}
