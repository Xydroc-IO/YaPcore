package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Roll back logged block changes on the current world via YaPProtect. */
public final class ServerRollbackScreen extends StaffPanelScreen {

    private String pendingDuration;

    public ServerRollbackScreen(Screen parent) {
        super(Component.literal("Rollback"), parent);
    }

    @Override
    protected void addContents() {
        addSubtitle("Current world · YaPProtect · click twice to confirm");

        addSection("Duration");
        addButtonGrid(
                duration("5 minutes", "5m"),
                duration("15 minutes", "15m"),
                duration("30 minutes", "30m"),
                duration("1 hour", "1h"),
                duration("8 hours", "8h"),
                duration("24 hours", "24h")
        );

        if (pendingDuration != null) {
            addSection("Confirm");
            addBody(actionWide(
                    "Confirm " + pendingDuration,
                    "Undo logged changes for the last " + pendingDuration + " on this world",
                    this::confirm));
            addBody(actionWide("Cancel", "Clear pending rollback", () -> {
                pendingDuration = null;
                rebuildWidgets();
            }));
        }
    }

    private Button duration(String label, String duration) {
        boolean pending = duration.equals(pendingDuration);
        String tip = pending
                ? "Selected — use Confirm below"
                : "Roll back last " + label.toLowerCase();
        return action(pending ? "▸ " + label : label, tip, () -> {
            pendingDuration = duration;
            rebuildWidgets();
        });
    }

    private void confirm() {
        if (pendingDuration == null || pendingDuration.isBlank()) {
            return;
        }
        String duration = pendingDuration;
        pendingDuration = null;
        closeToGame();
        StaffCmds.runFmt("yapprotect rollback time %s", duration);
    }
}
