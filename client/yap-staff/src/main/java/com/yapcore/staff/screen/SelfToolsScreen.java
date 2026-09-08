package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** God / fly / vanish / gamemode / heal / speed. */
public final class SelfToolsScreen extends StaffPanelScreen {

    public SelfToolsScreen(Screen parent) {
        super(Component.literal("Self tools"), parent);
    }

    @Override
    protected void addContents() {
        addSubtitle("Actions apply to you");

        addSection("Status");
        addButtonGrid(
                action("God mode", "Toggle invulnerability", () -> StaffCmds.run("god")),
                action("Fly", "Toggle flight", () -> StaffCmds.run("fly")),
                action("Vanish", "Hide from players", () -> StaffCmds.run("vanish")),
                action("Night vision", "Toggle for 5 minutes", () -> StaffCmds.run("yapadmin nv"))
        );

        addSection("Health");
        addButtonGrid(
                action("Heal", "Restore health and hunger", () -> StaffCmds.run("yapadmin heal")),
                action("Feed", "Fill hunger", () -> StaffCmds.run("yapadmin feed")),
                action("Repair hand", "Repair held item", () -> StaffCmds.run("repair"))
        );

        addSection("Gamemode");
        addButtonGrid(
                action("Survival", "Set gamemode survival", () -> StaffCmds.run("gms")),
                action("Creative", "Set gamemode creative", () -> StaffCmds.run("gmc")),
                action("Adventure", "Set gamemode adventure", () -> StaffCmds.run("gma")),
                action("Spectator", "Set gamemode spectator", () -> StaffCmds.run("gmsp"))
        );

        addSection("Speed & inventory");
        addButtonGrid(
                action("Walk speed 5", "Set walk speed to 5/10", () -> StaffCmds.speed(5, false)),
                action("Fly speed 5", "Set fly speed to 5/10", () -> StaffCmds.speed(5, true)),
                action("Clear inventory", "Clear your inventory", () -> StaffCmds.run("clear")),
                action("Workbench", "Open a crafting table", () -> StaffCmds.run("workbench")),
                action("Ender chest", "Open your ender chest", () -> StaffCmds.echest(null))
        );
    }
}
