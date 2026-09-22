package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Give night vision: 15m / 1h / unlimited / off — self or another player. */
public final class NightVisionScreen extends StaffPanelScreen {

    private final String playerOrNull;

    public NightVisionScreen(Screen parent, String playerOrNull) {
        super(Component.literal("Night vision"
                + (playerOrNull == null || playerOrNull.isBlank() ? "" : ": " + playerOrNull)), parent);
        this.playerOrNull = playerOrNull;
    }

    @Override
    protected void addContents() {
        boolean self = playerOrNull == null || playerOrNull.isBlank();
        addSubtitle(self ? "Applies to you" : "Applies to " + playerOrNull);

        addSection("Duration");
        addButtonGrid(
                action("15 minutes", "Night vision for 15 minutes", () -> apply("15m")),
                action("1 hour", "Night vision for 1 hour", () -> apply("1h")),
                action("Unlimited", "Until turned off", () -> apply("on")),
                action("Turn off", "Remove night vision", () -> apply("off"))
        );
    }

    private void apply(String mode) {
        closeToGame();
        if (playerOrNull == null || playerOrNull.isBlank()) {
            StaffCmds.runFmt("yapadmin nv %s", mode);
        } else {
            StaffCmds.runFmt("yapadmin nv %s %s", mode, playerOrNull);
        }
    }
}
