package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import com.yapcore.staff.YapStaffClient;
import com.yapcore.staff.ranks.PermCmds;
import com.yapcore.staff.ranks.RankDefs;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Manage one YaPPerms group: assign to player, parents, perms, delete. */
public final class RankManageScreen extends StaffPanelScreen {

    private final String groupId;

    public RankManageScreen(Screen parent, String groupId) {
        super(Component.literal("Rank: " + groupId), parent);
        this.groupId = groupId.toLowerCase();
    }

    @Override
    protected void addContents() {
        RankDefs.Rank known = RankDefs.find(groupId).orElse(null);
        String hint = known != null
                ? "weight " + known.weight() + " · " + known.hint()
                : "custom / live group";
        addBody(new StringWidget(Component.literal(hint), this.font));

        String target = YapStaffClient.session().hasTarget()
                ? YapStaffClient.session().targetName()
                : "(no target)";
        addSubtitle("Assign to player: " + target);

        addButtonGrid(
                action("Set as primary", "yapperm user … parent set", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        PermCmds.setPrimary(p, groupId);
                    }
                }),
                action("Add as parent", "Extra group on player", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        PermCmds.addParent(p, groupId);
                    }
                }),
                action("Remove parent", "Remove this group from player", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        PermCmds.removeParent(p, groupId);
                    }
                }),
                action("Pick player…", null, () -> open(new PlayersScreen(this, true))),
                action("Group info", "Dump nodes to chat", () -> PermCmds.groupInfo(groupId)),
                action("Edit permissions…", "Allow/deny group nodes", () ->
                        open(new PermEditorScreen(this, PermEditorScreen.Scope.GROUP, groupId)))
        );

        addSubtitle("Inheritance — add/remove parent groups");
        GridLayout parents = new GridLayout().columnSpacing(4).rowSpacing(3);
        GridLayout.RowHelper prows = parents.createRowHelper(2);
        for (RankDefs.Rank rank : RankDefs.STARTER) {
            if (rank.id().equals(groupId)) {
                continue;
            }
            String id = rank.id();
            prows.addChild(Button.builder(Component.literal("+ " + rank.label()), b ->
                    PermCmds.groupParentAdd(groupId, id)).width(110).build());
            prows.addChild(Button.builder(Component.literal("− " + rank.label()), b ->
                    PermCmds.groupParentRemove(groupId, id)).width(110).build());
        }
        addBody(parents);

        addSubtitle("Danger");
        addButtonGrid(
                action("Delete group", "yapperm group delete", () -> PermCmds.groupDelete(groupId)),
                action("Reload perms", "yapperm reload", PermCmds::reload)
        );
    }
}
