package com.yapcore.staff.screen;

import com.yapcore.staff.YapStaffClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Top-level staff hub — full admin suite. */
public final class StaffHubScreen extends StaffPanelScreen {

    public StaffHubScreen(Screen parent) {
        super(Component.literal("YaP Staff"), parent);
    }

    @Override
    protected void addContents() {
        String target = YapStaffClient.session().hasTarget()
                ? "Player: " + YapStaffClient.session().targetName()
                : "No player selected";
        addSubtitle("Network admin tools · scroll if the window is short");
        addSubtitle(target);

        addSection("Players");
        addButtonGrid(
                nav("Players", "Online list — manage one player", () ->
                        open(new PlayersScreen(this)))
        );

        addSection("Tools");
        addButtonGrid(
                nav("Self tools", "God, fly, vanish, gamemode, speed", () ->
                        open(new SelfToolsScreen(this))),
                nav("Give", "Items, kits, and material browser", () ->
                        open(new GiveScreen(this))),
                nav("Custom items…", "Browse, give, and create YaPItems", () ->
                        open(new ItemsAdminScreen(this))),
                nav("Spawn mobs", "Spawn entities at you or a player", () ->
                        open(new MobsScreen(this))),
                nav("Teleport", "TP to, bring here, spawn, back", () ->
                        open(new TeleportScreen(this)))
        );

        addSection("World");
        addButtonGrid(
                nav("World edit", "Selection, schematics, brush, worlds", () ->
                        open(new WorldEditHubScreen(this)))
        );

        addSection("Moderation");
        addButtonGrid(
                nav("Moderation", "Kick, warn, mute, tempban", () ->
                        open(new ModerationScreen(this))),
                nav("Trolls", "Smite, launch, burn, slap…", () ->
                        open(new TrollsScreen(this)))
        );

        addSection("Server");
        addButtonGrid(
                nav("Server", "Broadcast, weather, reloads", () ->
                        open(new ServerOpsScreen(this))),
                nav("Economy", "Grant money to a player", () ->
                        open(new EconomyScreen(this))),
                nav("Ranks & perms", "Assign ranks and permission nodes", () ->
                        open(new RanksHubScreen(this))),
                nav("More…", "Stacker, regions, map, and other GUIs", () ->
                        open(new LinksScreen(this))),
                nav("Chest menu", "Open the in-game chest hub", () -> {
                    closeToGame();
                    com.yapcore.staff.StaffCmds.run("yapadmin chest");
                })
        );
    }

    private Button nav(String label, String tip, Runnable open) {
        return action(label, tip, open);
    }
}
