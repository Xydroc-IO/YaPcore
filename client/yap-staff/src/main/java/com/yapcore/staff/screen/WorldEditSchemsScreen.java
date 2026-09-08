package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import com.yapcore.staff.YapStaffClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Schematic save / paste / load by name + chest browser. */
public final class WorldEditSchemsScreen extends StaffPanelScreen {

    public WorldEditSchemsScreen(Screen parent) {
        super(Component.literal("Schematics"), parent);
    }

    @Override
    protected void addContents() {
        var session = YapStaffClient.session();
        addBody(new StringWidget(Component.literal(
                "Saved as .yschem / .schem under plugins/YaPWorld/schematics"), this.font));

        EditBox name = new EditBox(this.font, Math.min(280, panelWidth() - 20), 20,
                Component.literal("Schematic name"));
        name.setValue(session.weSchemName());
        name.setHint(Component.literal("name (no extension)"));
        name.setResponder(session::setWeSchemName);
        addBody(name);

        addSubtitle("By name");
        addButtonGrid(
                action("Save sel", "pos1/pos2 → .yschem", () -> named("yapworld schem save %s")),
                action("Paste", "At your feet", () -> named("yapworld schem paste %s")),
                action("Load clip", "Into clipboard", () -> named("yapworld schem load %s")),
                action("Delete", "Remove file", () -> named("yapworld schem delete %s")),
                closeRun("List (chat)", "yapworld schem list", "yapworld schem list"),
                closeRun("Formats", "Supported types", "yapworld schem formats")
        );

        addSubtitle("Browse");
        addButtonGrid(
                closeRun("Chest browser", "Click a schem to paste", "yapworld schem browse"),
                closeRun("Full edit GUI", "Save / paste from panel", "yapworld gui")
        );

        LinearLayout tip = LinearLayout.vertical().spacing(2);
        tip.addChild(new StringWidget(Component.literal(
                "Tip: set pos1/pos2 (or wand), then Save. Paste drops at your feet."), this.font));
        addBody(tip);
    }

    private void named(String fmt) {
        String n = YapStaffClient.session().weSchemName();
        if (n == null || n.isBlank()) {
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal("§cEnter a schematic name first."));
            }
            return;
        }
        // Keep spaces out of filenames
        String safe = n.replace(' ', '_');
        YapStaffClient.session().setWeSchemName(safe);
        closeToGame();
        StaffCmds.runFmt(fmt, safe);
    }

    private Button closeRun(String label, String tip, String cmd) {
        return action(label, tip, () -> {
            closeToGame();
            StaffCmds.run(cmd);
        });
    }
}
