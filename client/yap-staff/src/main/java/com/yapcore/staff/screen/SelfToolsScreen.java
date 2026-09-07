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
        addSubtitle("Toggles apply to you. Needs matching essentials perms.");
        addButtonGrid(
                action("God mode", "/god — yapessentials.god", () -> StaffCmds.run("god")),
                action("Fly", "/fly", () -> StaffCmds.run("fly")),
                action("Vanish", "/vanish", () -> StaffCmds.run("vanish")),
                action("Heal", "Full HP + hunger", () -> StaffCmds.run("yapadmin heal")),
                action("Feed", "Full hunger", () -> StaffCmds.run("yapadmin feed")),
                action("Night vision", "Toggle 5m NV", () -> StaffCmds.run("yapadmin nv")),
                action("Survival", "/gms", () -> StaffCmds.run("gms")),
                action("Creative", "/gmc", () -> StaffCmds.run("gmc")),
                action("Adventure", "/gma", () -> StaffCmds.run("gma")),
                action("Spectator", "/gmsp", () -> StaffCmds.run("gmsp")),
                action("Repair hand", "/repair", () -> StaffCmds.run("repair")),
                action("Speed walk 5", "/speed walk 5", () -> StaffCmds.run("speed walk 5")),
                action("Speed fly 5", "/speed fly 5", () -> StaffCmds.run("speed fly 5")),
                action("Clear inventory", "Your inventory", () -> StaffCmds.run("clear")),
                action("Workbench", "/workbench", () -> StaffCmds.run("workbench")),
                action("Ender chest", "/echest", () -> StaffCmds.run("echest"))
        );
    }
}
