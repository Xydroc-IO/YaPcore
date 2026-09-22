package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Admin grant skill XP or set skill level for a player. */
public final class SkillGrantScreen extends StaffPanelScreen {

    private final String playerName;
    private final boolean giveXp;
    private String skill = "marathon";

    public SkillGrantScreen(Screen parent, String playerName, boolean giveXp) {
        super(Component.literal((giveXp ? "Give XP: " : "Set level: ") + playerName), parent);
        this.playerName = playerName;
        this.giveXp = giveXp;
    }

    @Override
    protected void addContents() {
        addSubtitle((giveXp ? "XP" : "Level") + " → " + playerName + " · skill: " + skill);

        addSection("Skill");
        addButtonGrid(
                skillBtn("marathon"), skillBtn("swimming"), skillBtn("mining"),
                skillBtn("woodcutting"), skillBtn("strength"), skillBtn("builder"),
                skillBtn("herbalism"), skillBtn("excavation"), skillBtn("alchemy"),
                skillBtn("health")
        );

        if (giveXp) {
            addSection("Amount");
            addButtonGrid(
                    xp(100), xp(500), xp(1000), xp(5000), xp(25000), xp(100000)
            );
        } else {
            addSection("Level");
            addButtonGrid(
                    lvl(1), lvl(10), lvl(25), lvl(50), lvl(75), lvl(100), lvl(120)
            );
        }
    }

    private net.minecraft.client.gui.components.Button skillBtn(String id) {
        String mark = id.equals(skill) ? " ★" : "";
        return action(id + mark, "Select " + id, () -> {
            skill = id;
            rebuildWidgets();
        });
    }

    private net.minecraft.client.gui.components.Button xp(int amount) {
        return action(amount + " XP", "Add " + amount + " to " + skill, () -> {
            closeToGame();
            StaffCmds.runFmt("skill addxp %s %s %d", playerName, skill, amount);
        });
    }

    private net.minecraft.client.gui.components.Button lvl(int level) {
        return action("Lv " + level, "Set " + skill + " to " + level, () -> {
            closeToGame();
            StaffCmds.runFmt("skill set %s %s %d", playerName, skill, level);
        });
    }
}
