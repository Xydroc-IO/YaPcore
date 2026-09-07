package com.yapcore.staff.screen;

import com.yapcore.staff.YapStaffClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
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
                ? "Target: " + YapStaffClient.session().targetName()
                : "Target: (none — pick a player)";
        addBody(new StringWidget(Component.literal(target), this.font));

        int cols = gridColumns();
        GridLayout grid = new GridLayout().columnSpacing(8).rowSpacing(bodySpacing());
        GridLayout.RowHelper rows = grid.createRowHelper(cols);
        rows.addChild(nav("Players", "Online list · manage one player", () ->
                open(new PlayersScreen(this))));
        rows.addChild(nav("Self tools", "God · fly · vanish · gamemode", () ->
                open(new SelfToolsScreen(this))));
        rows.addChild(nav("Give / spawn", "Browse all items · kits · presets", () ->
                open(new GiveScreen(this))));
        rows.addChild(nav("Teleport", "TP to / here / spawn / back", () ->
                open(new TeleportScreen(this))));
        rows.addChild(nav("Trolls", "Smite · launch · burn · slap…", () ->
                open(new TrollsScreen(this))));
        rows.addChild(nav("Moderation", "Kick · warn · mute · tempban", () ->
                open(new ModerationScreen(this))));
        rows.addChild(nav("Server", "Broadcast · weather · reloads", () ->
                open(new ServerOpsScreen(this))));
        rows.addChild(nav("Economy", "Grant money via /eco", () ->
                open(new EconomyScreen(this))));
        rows.addChild(nav("Ranks & perms", "Assign ranks · allow/deny nodes · tracks", () ->
                open(new RanksHubScreen(this))));
        rows.addChild(nav("Deep links", "World · stacker · menu · server ranks GUI", () ->
                open(new LinksScreen(this))));
        rows.addChild(nav("Server chest GUI", "Open Folia /yapadmin chest menu", () -> {
            closeToGame();
            com.yapcore.staff.StaffCmds.run("yapadmin chest");
        }));
        addBody(grid);
        addSubtitle("Server still enforces permissions. Scroll if needed.");
    }

    private Button nav(String label, String tip, Runnable open) {
        return action(label, tip, open);
    }
}
