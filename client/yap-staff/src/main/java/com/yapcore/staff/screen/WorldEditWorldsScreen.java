package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import com.yapcore.staff.YapStaffClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** World load / unload / TP / create helpers via /yapworld. */
public final class WorldEditWorldsScreen extends StaffPanelScreen {

    private static final String[] COMMON = {
            "world", "world_nether", "world_the_end"
    };

    public WorldEditWorldsScreen(Screen parent) {
        super(Component.literal("Worlds"), parent);
    }

    @Override
    protected void addContents() {
        var session = YapStaffClient.session();
        addBody(new StringWidget(Component.literal(
                "World: " + session.weWorldName()), this.font));

        EditBox name = new EditBox(this.font, Math.min(280, panelWidth() - 20), 20,
                Component.literal("World name"));
        name.setValue(session.weWorldName());
        name.setHint(Component.literal("world name"));
        name.setResponder(session::setWeWorldName);
        addBody(name);

        String w = session.weWorldName();
        addSubtitle("Actions");
        addButtonGrid(
                closeFmt("TP there", "yapworld tp %s", w),
                closeFmt("Load", "yapworld load %s", w),
                closeFmt("Unload", "yapworld unload %s", w),
                closeRun("Status", "yapworld status"),
                closeRun("Pregen start", "yapworld pregen start"),
                closeRun("Pregen status", "yapworld pregen status"),
                closeRun("Pregen cancel", "yapworld pregen cancel"),
                closeRun("Reload cfg", "yapworld reload")
        );

        addSubtitle("Quick names");
        GridLayout grid = new GridLayout().columnSpacing(4).rowSpacing(3);
        GridLayout.RowHelper rows = grid.createRowHelper(gridColumns());
        for (String id : COMMON) {
            rows.addChild(Button.builder(Component.literal(id), b -> {
                session.setWeWorldName(id);
                rebuildWidgets();
            }).width(colWidth()).build());
        }
        addBody(grid);

        addSubtitle("Create (from typed name)");
        addButtonGrid(
                closeFmt("Create flat", "yapworld create %s --type flat --env overworld", w),
                closeFmt("Create normal", "yapworld create %s --type normal --env overworld", w),
                closeFmt("Create nether", "yapworld create %s --type normal --env nether", w),
                closeFmt("Create end", "yapworld create %s --type normal --env end", w)
        );
    }

    private Button closeFmt(String label, String fmt, Object... args) {
        return action(label, fmt, () -> {
            closeToGame();
            StaffCmds.runFmt(fmt, args);
        });
    }

    private Button closeRun(String label, String cmd) {
        return action(label, "/" + cmd, () -> {
            closeToGame();
            StaffCmds.run(cmd);
        });
    }
}
