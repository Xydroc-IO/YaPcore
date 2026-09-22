package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import com.yapcore.staff.YapStaffClient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Skills admin hub — give XP / set levels for the selected player.
 * Pick a target with the bar below, then choose XP or level.
 */
public final class SkillsHubScreen extends StaffPanelScreen {

    public SkillsHubScreen(Screen parent) {
        super(Component.literal("Skills"), parent);
    }

    @Override
    protected void addContents() {
        addSubtitle("Give XP or set skill levels · YaPSkills must be loaded on this server");
        addTargetBar();

        addSection("Grant");
        addButtonGrid(
                action("Give skill XP…", "Pick skill → amount for the selected player", () -> {
                    if (!requireTarget()) {
                        return;
                    }
                    open(new SkillGrantScreen(this, YapStaffClient.session().targetName(), true));
                }),
                action("Set skill level…", "Pick skill → level for the selected player", () -> {
                    if (!requireTarget()) {
                        return;
                    }
                    open(new SkillGrantScreen(this, YapStaffClient.session().targetName(), false));
                })
        );

        addSection("View");
        addButtonGrid(
                action("Open /skills", "Your skills menu on this server", () -> {
                    closeToGame();
                    StaffCmds.run("skills");
                }),
                action("View target /skills", "Open skills for the selected player", () -> {
                    if (!requireTarget()) {
                        return;
                    }
                    closeToGame();
                    StaffCmds.runFmt("skills %s", YapStaffClient.session().targetName());
                })
        );
    }

    private boolean requireTarget() {
        if (YapStaffClient.session().hasTarget()) {
            return true;
        }
        // No toast API — bounce to player picker; when they return, target is set.
        open(new PlayersScreen(this, true));
        return false;
    }
}
