package com.yapcore.staff.screen;

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

/** Browse starter ranks, open manage, create custom groups. */
public final class RanksBrowseScreen extends StaffPanelScreen {

    public RanksBrowseScreen(Screen parent) {
        super(Component.literal("Browse ranks"), parent);
    }

    @Override
    protected void addContents() {
        var session = YapStaffClient.session();
        addSubtitle("Starter pack · tap a rank to manage · custom create below");

        GridLayout grid = new GridLayout().columnSpacing(6).rowSpacing(4);
        GridLayout.RowHelper rows = grid.createRowHelper(2);
        for (RankDefs.Rank rank : RankDefs.STARTER) {
            String tip = "weight " + rank.weight() + " · " + rank.hint();
            rows.addChild(action(rank.label() + " (" + rank.id() + ")", tip, () ->
                    open(new RankManageScreen(this, rank.id()))));
        }
        addBody(grid);

        addSubtitle("Create group");
        LinearLayout create = LinearLayout.vertical().spacing(4);
        LinearLayout row = LinearLayout.horizontal().spacing(6);
        EditBox name = new EditBox(this.font, 120, 20, Component.literal("name"));
        name.setValue(session.customGroup());
        name.setHint(Component.literal("rank id…"));
        name.setResponder(session::setCustomGroup);
        row.addChild(name);
        row.addChild(Button.builder(Component.literal("Weight " + session.createWeight()), b -> {
            session.cycleCreateWeight();
            rebuildWidgets();
        }).width(100).build());
        row.addChild(Button.builder(Component.literal("Create"), b -> {
            if (session.customGroup().isBlank()) {
                return;
            }
            PermCmds.groupCreate(session.customGroup(), session.createWeight());
            open(new RankManageScreen(this, session.customGroup()));
        }).width(70).build());
        create.addChild(row);
        create.addChild(new StringWidget(Component.literal(
                "Also: List groups dumps live names to chat (custom ranks appear there)."), this.font));
        addBody(create);

        LinearLayout extras = LinearLayout.horizontal().spacing(6);
        extras.addChild(Button.builder(Component.literal("List groups (chat)"), b -> PermCmds.groupList())
                .width(150).build());
        EditBox openCustom = new EditBox(this.font, 100, 20, Component.literal("open"));
        openCustom.setHint(Component.literal("open id…"));
        openCustom.setValue(session.customGroup());
        openCustom.setResponder(session::setCustomGroup);
        extras.addChild(openCustom);
        extras.addChild(Button.builder(Component.literal("Open"), b -> {
            if (!session.customGroup().isBlank()) {
                open(new RankManageScreen(this, session.customGroup()));
            }
        }).width(60).build());
        addBody(extras);
    }
}
