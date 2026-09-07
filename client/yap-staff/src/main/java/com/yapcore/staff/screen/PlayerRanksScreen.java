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

/** Assign primary group / parents / promote for a player. */
public final class PlayerRanksScreen extends StaffPanelScreen {

    public PlayerRanksScreen(Screen parent) {
        super(Component.literal("Player ranks"), parent);
    }

    @Override
    protected void addContents() {
        var session = YapStaffClient.session();
        String target = session.hasTarget() ? session.targetName() : "(pick a player)";

        LinearLayout top = LinearLayout.vertical().spacing(4);
        top.addChild(new StringWidget(Component.literal(
                "Player: " + target + "  ·  Mode: " + session.rankAssignLabel()), this.font));

        LinearLayout controls = LinearLayout.horizontal().spacing(6);
        controls.addChild(Button.builder(Component.literal("Pick player…"), b ->
                open(new PlayersScreen(this, true))).width(110).build());
        controls.addChild(Button.builder(Component.literal("Mode: " + session.rankAssignLabel()), b -> {
            session.cycleRankAssignMode();
            rebuildWidgets();
        }).width(140).build());
        controls.addChild(Button.builder(Component.literal("Info"), b -> {
            String p = StaffCmds.requireTarget();
            if (p != null) {
                PermCmds.userInfo(p);
            }
        }).width(60).build());
        top.addChild(controls);
        addBody(top);

        addSubtitle("Tap a rank (" + session.rankAssignLabel() + ")");
        GridLayout grid = new GridLayout().columnSpacing(6).rowSpacing(4);
        GridLayout.RowHelper rows = grid.createRowHelper(2);
        for (RankDefs.Rank rank : RankDefs.STARTER) {
            String tip = "w" + rank.weight() + " · " + rank.hint()
                    + (rank.onTrack() ? " · on track yap" : " · not on track");
            rows.addChild(action(rank.label(), tip, () -> applyRank(rank.id())));
        }
        addBody(grid);

        addSubtitle("Custom group name");
        LinearLayout custom = LinearLayout.horizontal().spacing(6);
        EditBox box = new EditBox(this.font, 140, 20, Component.literal("group"));
        box.setValue(session.customGroup());
        box.setHint(Component.literal("custom rank…"));
        box.setResponder(session::setCustomGroup);
        custom.addChild(box);
        custom.addChild(Button.builder(Component.literal("Apply"), b -> {
            if (session.customGroup().isBlank()) {
                return;
            }
            applyRank(session.customGroup());
        }).width(70).build());
        addBody(custom);

        addSubtitle("Track shortcuts & extras");
        addButtonGrid(
                action("Promote", "/promote on track yap", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        PermCmds.promote(p);
                    }
                }),
                action("Demote", "/demote on track yap", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        PermCmds.demote(p);
                    }
                }),
                action("Clear meta", "Clear prefix/suffix override", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        PermCmds.clearMeta(p);
                    }
                }),
                action("User perms…", "Allow/deny nodes for this player", () ->
                        open(new PermEditorScreen(this, PermEditorScreen.Scope.USER, null)))
        );
    }

    private void applyRank(String group) {
        String p = StaffCmds.requireTarget();
        if (p == null) {
            return;
        }
        int mode = YapStaffClient.session().rankAssignMode();
        switch (mode) {
            case 1 -> PermCmds.addParent(p, group);
            case 2 -> PermCmds.removeParent(p, group);
            default -> PermCmds.setPrimary(p, group);
        }
    }
}
