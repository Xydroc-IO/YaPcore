package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import com.yapcore.staff.YapStaffClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Pattern field + set / walls / hollow / replace on the current selection. */
public final class WorldEditFillScreen extends StaffPanelScreen {

    private static final String[] PRESETS = {
            "stone", "dirt", "grass_block", "oak_planks", "glass", "sand",
            "cobblestone", "deepslate", "air", "water", "lava", "barrier"
    };

    public WorldEditFillScreen(Screen parent) {
        super(Component.literal("Fill / set"), parent);
    }

    @Override
    protected void addContents() {
        var session = YapStaffClient.session();
        addBody(new StringWidget(Component.literal(
                "Pattern: " + session.wePattern()), this.font));

        EditBox pattern = new EditBox(this.font, Math.min(280, panelWidth() - 20), 20,
                Component.literal("Pattern"));
        pattern.setValue(session.wePattern());
        pattern.setHint(Component.literal("stone · 50%stone,50%dirt · #air"));
        pattern.setResponder(session::setWePattern);
        addBody(pattern);

        addSubtitle("Apply");
        String pat = session.wePattern();
        addButtonGrid(
                closeFmt("Set", "Fill selection", "yapworld set %s", pat),
                closeFmt("Walls", "Four sides", "yapworld walls %s", pat),
                closeFmt("Hollow", "Shell only", "yapworld hollow"),
                closeFmt("Outline", "Edges", "yapworld outline %s", pat),
                closeFmt("Overlay", "Top surface", "yapworld overlay %s", pat),
                closeFmt("Smooth", "Terrain smooth", "yapworld smooth"),
                closeFmt("Replace → air", "Clear to air", "yapworld replace %s air", pat),
                closeFmt("Replace air →", "Fill air only", "yapworld replace air %s", pat)
        );

        addSubtitle("Presets (sets pattern)");
        GridLayout grid = new GridLayout().columnSpacing(4).rowSpacing(3);
        GridLayout.RowHelper rows = grid.createRowHelper(gridColumns());
        for (String id : PRESETS) {
            rows.addChild(Button.builder(Component.literal(id.replace('_', ' ')), b -> {
                session.setWePattern(id);
                rebuildWidgets();
            }).width(colWidth()).build());
        }
        addBody(grid);

        LinearLayout shapes = LinearLayout.horizontal().spacing(6);
        shapes.addChild(Button.builder(Component.literal("Sphere r5"), b -> {
            closeToGame();
            StaffCmds.runFmt("yapworld sphere %s 5", session.wePattern());
        }).width(colWidth()).build());
        shapes.addChild(Button.builder(Component.literal("Cyl r5 h8"), b -> {
            closeToGame();
            StaffCmds.runFmt("yapworld cyl %s 5 8", session.wePattern());
        }).width(colWidth()).build());
        addBody(shapes);
    }

    private Button closeFmt(String label, String tip, String fmt, Object... args) {
        return action(label, tip, () -> {
            closeToGame();
            if (args.length == 0) {
                StaffCmds.run(fmt);
            } else {
                StaffCmds.runFmt(fmt, args);
            }
        });
    }
}
