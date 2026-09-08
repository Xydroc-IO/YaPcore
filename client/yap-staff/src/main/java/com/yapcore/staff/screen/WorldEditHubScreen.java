package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** YaPWorld tools — selection, fill, schems, brush, worlds. */
public final class WorldEditHubScreen extends StaffPanelScreen {

    public WorldEditHubScreen(Screen parent) {
        super(Component.literal("World edit"), parent);
    }

    @Override
    protected void addContents() {
        addSubtitle("YaPWorld — Folia-safe world editing");

        addSection("Menus");
        addButtonGrid(
                closeRun("Chest panel", "Full YaP World Edit inventory", "yapworld gui"),
                closeRun("Schematics", "Browse and paste saved schematics", "yapworld schem browse"),
                closeRun("Browser studio", "Optional web editor", "yapworld editor")
        );

        addSection("Selection");
        addButtonGrid(
                closeRun("Wand", "Golden axe — left pos1, right pos2", "yapworld tool"),
                closeRun("Pos1 here", "Set first corner at your feet", "yapworld pos1"),
                closeRun("Pos2 here", "Set second corner at your feet", "yapworld pos2"),
                closeRun("Clear", "Clear selection", "yapworld clear"),
                closeRun("Size", "Selection volume", "yapworld size"),
                closeRun("Expand +1", "Grow selection by one block", "yapworld expand 1"),
                closeRun("Cuboid", "Box selection mode", "yapworld sel cuboid"),
                closeRun("Sphere", "Sphere selection mode", "yapworld sel sphere"),
                closeRun("Cylinder", "Cylinder selection mode", "yapworld sel cyl"),
                closeRun("Polygon", "Polygon selection mode", "yapworld sel poly")
        );

        addSection("Clipboard");
        addButtonGrid(
                closeRun("Copy", "Copy relative to your feet", "yapworld copy"),
                closeRun("Cut", "Copy and clear", "yapworld cut"),
                closeRun("Paste", "Paste at your feet", "yapworld paste"),
                closeRun("Paste (no air)", "Paste ignoring air blocks", "yapworld paste -a"),
                closeRun("Undo", "Undo last edit", "yapworld undo"),
                closeRun("Redo", "Redo last edit", "yapworld redo"),
                closeRun("Fast mode", "Skip undo for large edits", "yapworld fast"),
                closeRun("Fix lighting", "Relight selection", "yapworld fixlighting")
        );

        addSection("Tools");
        addButtonGrid(
                action("Fill / set…", "Fill selection with a pattern", () ->
                        open(new WorldEditFillScreen(this))),
                action("Schematics…", "Save, paste, or browse by name", () ->
                        open(new WorldEditSchemsScreen(this))),
                action("Brush…", "Brush radius and types", () ->
                        open(new WorldEditBrushScreen(this))),
                action("Worlds…", "Load, unload, teleport, create", () ->
                        open(new WorldEditWorldsScreen(this)))
        );

        addSection("Navigate");
        addButtonGrid(
                closeRun("Thru", "Pass through a wall", "yapworld thru"),
                closeRun("Jump to", "Teleport to look target", "yapworld jumpto"),
                closeRun("Up", "Ascend one block", "yapworld up 1"),
                closeRun("Ascend", "Climb to surface above", "yapworld ascend"),
                closeRun("Descend", "Drop to space below", "yapworld descend"),
                closeRun("Status", "YaPWorld status", "yapworld status")
        );
    }

    private net.minecraft.client.gui.components.Button closeRun(String label, String tip, String cmd) {
        return action(label, tip, () -> {
            closeToGame();
            StaffCmds.run(cmd);
        });
    }
}
