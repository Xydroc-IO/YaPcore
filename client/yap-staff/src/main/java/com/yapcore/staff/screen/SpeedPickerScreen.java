package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Pick walk or fly speed 1–10 for self or another player. */
public final class SpeedPickerScreen extends StaffPanelScreen {

    private final String playerOrNull;
    private final boolean fly;

    public SpeedPickerScreen(Screen parent, String playerOrNull, boolean fly) {
        super(Component.literal((fly ? "Fly" : "Walk") + " speed"
                + (playerOrNull == null || playerOrNull.isBlank() ? "" : ": " + playerOrNull)), parent);
        this.playerOrNull = playerOrNull;
        this.fly = fly;
    }

    @Override
    protected void addContents() {
        boolean self = playerOrNull == null || playerOrNull.isBlank();
        addSubtitle((fly ? "Fly" : "Walk") + " speed 1–10"
                + (self ? " (you)" : " → " + playerOrNull));

        addSection("Level");
        addButtonGrid(
                level(1), level(2), level(3), level(4), level(5),
                level(6), level(7), level(8), level(9), level(10)
        );

        addSection("Quick");
        addButtonGrid(
                action("Reset default",
                        fly ? "Fly 1/10 (vanilla)" : "Walk 2/10 (vanilla)",
                        () -> apply(fly ? 1 : 2))
        );
    }

    private net.minecraft.client.gui.components.Button level(int n) {
        return action(String.valueOf(n), (fly ? "Fly" : "Walk") + " " + n + "/10", () -> apply(n));
    }

    private void apply(int level) {
        closeToGame();
        StaffCmds.speed(level, fly, playerOrNull);
    }
}
