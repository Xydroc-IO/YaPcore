package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import com.yapcore.staff.YapStaffClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Brush radius + common //brush types via YaPWorld. */
public final class WorldEditBrushScreen extends StaffPanelScreen {

    public WorldEditBrushScreen(Screen parent) {
        super(Component.literal("Brush"), parent);
    }

    @Override
    protected void addContents() {
        var session = YapStaffClient.session();
        int r = session.weBrushRadius();
        String pat = session.wePattern();

        addBody(new StringWidget(Component.literal(
                "Radius " + r + "  ·  Pattern " + pat), this.font));

        LinearLayout controls = LinearLayout.horizontal().spacing(6);
        controls.addChild(Button.builder(Component.literal("Radius ×" + r), b -> {
            session.cycleWeBrushRadius();
            rebuildWidgets();
        }).width(110).build());
        controls.addChild(Button.builder(Component.literal("Edit pattern…"), b ->
                open(new WorldEditFillScreen(this))).width(120).build());
        addBody(controls);

        addSubtitle("Set brush (right-click with wand in brush mode)");
        addButtonGrid(
                closeFmt("Sphere", "yapworld brush sphere %d %s", r, pat),
                closeFmt("Cyl", "yapworld brush cyl %d %s", r, pat),
                closeFmt("Smooth", "yapworld brush smooth %d", r),
                closeFmt("Gravity", "yapworld brush gravity %d", r),
                closeFmt("Erode", "yapworld brush erode %d", r),
                closeFmt("Raise", "yapworld brush raise %d %s", r, pat),
                closeFmt("Lower", "yapworld brush lower %d", r),
                closeFmt("Melt", "yapworld brush melt %d", r),
                closeFmt("Fill", "yapworld brush fill %d %s", r, pat),
                closeFmt("Forest", "yapworld brush forest %d", r),
                closeFmt("Clipboard", "yapworld brush clipboard %d", r),
                closeFmt("Butcher", "yapworld brush butcher %d", r)
        );

        addSubtitle("Legacy radius brush");
        addButtonGrid(
                closeFmt("Simple brush", "yapworld brush %d %s", r, pat),
                action("Get wand", "Tool + brush mode via GUI", () -> {
                    closeToGame();
                    StaffCmds.run("yapworld tool");
                })
        );
    }

    private Button closeFmt(String label, String fmt, Object... args) {
        return action(label, fmt, () -> {
            closeToGame();
            StaffCmds.runFmt(fmt, args);
        });
    }
}
