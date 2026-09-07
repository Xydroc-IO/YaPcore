package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import com.yapcore.staff.YapStaffClient;
import com.yapcore.staff.ranks.PermCmds;
import com.yapcore.staff.ranks.RankDefs;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** YaPPerms ranks & permissions — full staff editor. */
public final class RanksHubScreen extends StaffPanelScreen {

    public RanksHubScreen(Screen parent) {
        super(Component.literal("Ranks & permissions"), parent);
    }

    @Override
    protected void addContents() {
        String target = YapStaffClient.session().hasTarget()
                ? "Target: " + YapStaffClient.session().targetName()
                : "Target: (none — pick when needed)";
        addBody(new StringWidget(Component.literal(target), this.font));
        addSubtitle("YaPPerms · needs yapperm.admin (promote/demote need their nodes)");

        GridLayout grid = new GridLayout().columnSpacing(8).rowSpacing(6);
        GridLayout.RowHelper rows = grid.createRowHelper(2);
        rows.addChild(actionWide("Player ranks", "Set primary · parents · promote/demote", () ->
                open(new PlayerRanksScreen(this))));
        rows.addChild(actionWide("Player permissions", "Allow / deny / unset user nodes", () ->
                open(new PermEditorScreen(this, PermEditorScreen.Scope.USER, null))));
        rows.addChild(actionWide("Browse ranks", "Starter ladder · create · manage groups", () ->
                open(new RanksBrowseScreen(this))));
        rows.addChild(actionWide("Tracks & ladder", "Promote track · list tracks", () ->
                open(new TracksScreen(this))));
        rows.addChild(actionWide("List groups (chat)", "yapperm group list", PermCmds::groupList));
        rows.addChild(actionWide("Server ranks GUI", "Chest GUI /yapperm gui", () -> {
            closeToGame();
            StaffCmds.run("yapperm gui");
        }));
        addBody(grid);

        addSubtitle("Ops");
        addButtonGrid(
                action("Reload", "yapperm reload", PermCmds::reload),
                action("Apply pack", "Starter ranks + grants", PermCmds::applyPack),
                action("Dump snapshot", "editor-snapshot.yml", PermCmds::dump),
                action("Track info", "yap track", () -> PermCmds.trackInfo(RankDefs.DEFAULT_TRACK))
        );
    }
}
