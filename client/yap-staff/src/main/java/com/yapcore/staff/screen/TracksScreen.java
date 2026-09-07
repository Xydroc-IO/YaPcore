package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import com.yapcore.staff.YapStaffClient;
import com.yapcore.staff.ranks.PermCmds;
import com.yapcore.staff.ranks.RankDefs;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Promote/demote ladder and track ops. */
public final class TracksScreen extends StaffPanelScreen {

    public TracksScreen(Screen parent) {
        super(Component.literal("Tracks & ladder"), parent);
    }

    @Override
    protected void addContents() {
        var session = YapStaffClient.session();
        String target = session.hasTarget() ? session.targetName() : "(pick a player)";
        addBody(new StringWidget(Component.literal(
                "Player: " + target + "  ·  default track: " + RankDefs.DEFAULT_TRACK), this.font));

        LinearLayout controls = LinearLayout.horizontal().spacing(6);
        controls.addChild(Button.builder(Component.literal("Pick player…"), b ->
                open(new PlayersScreen(this, true))).width(110).build());
        controls.addChild(Button.builder(Component.literal("Promote ▶"), b -> {
            String p = StaffCmds.requireTarget();
            if (p != null) {
                PermCmds.promote(p);
            }
        }).width(100).build());
        controls.addChild(Button.builder(Component.literal("◀ Demote"), b -> {
            String p = StaffCmds.requireTarget();
            if (p != null) {
                PermCmds.demote(p);
            }
        }).width(100).build());
        addBody(controls);

        addSubtitle("Track yap (lowest → highest)");
        GridLayout ladder = new GridLayout().columnSpacing(4).rowSpacing(3);
        GridLayout.RowHelper rows = ladder.createRowHelper(1);
        var onTrack = RankDefs.onTrack();
        for (int i = 0; i < onTrack.size(); i++) {
            RankDefs.Rank rank = onTrack.get(i);
            String arrow = i == 0 ? "● " : "↑ ";
            rows.addChild(actionWide(
                    arrow + rank.label() + "  (" + rank.id() + ", w" + rank.weight() + ")",
                    "Set primary to " + rank.id(),
                    () -> {
                        String p = StaffCmds.requireTarget();
                        if (p != null) {
                            PermCmds.setPrimary(p, rank.id());
                        }
                    }));
        }
        addBody(ladder);

        addSubtitle("Track commands");
        addButtonGrid(
                action("List tracks", "yapperm track list", PermCmds::trackList),
                action("Info: yap", "yapperm track info yap", () -> PermCmds.trackInfo(RankDefs.DEFAULT_TRACK)),
                action("Player info", "Current primary/groups", () -> {
                    String p = StaffCmds.requireTarget();
                    if (p != null) {
                        PermCmds.userInfo(p);
                    }
                }),
                action("Player ranks…", "Full assign UI", () -> open(new PlayerRanksScreen(this)))
        );
    }
}
