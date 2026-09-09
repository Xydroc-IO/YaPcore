package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import com.yapcore.staff.YapStaffClient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Per-player actions: TP, inspect, heal, ranks, mod, trolls. */
public final class PlayerActionsScreen extends StaffPanelScreen {

    private final String playerName;

    public PlayerActionsScreen(Screen parent, String playerName) {
        super(Component.literal("Player: " + playerName), parent);
        this.playerName = playerName;
        YapStaffClient.session().setTarget(playerName);
    }

    @Override
    protected void addContents() {
        addSubtitle("Managing " + playerName);
        String p = playerName;

        addSection("Teleport");
        addButtonGrid(
                action("Go to", "Teleport to them", () -> StaffCmds.runFmt("yapadmin tp %s", p)),
                action("Bring here", "Teleport them to you", () -> StaffCmds.runFmt("yapadmin tphere %s", p)),
                action("Send to spawn", "Send to world spawn", () -> StaffCmds.runFmt("yapadmin tpspawn %s", p))
        );

        addSection("Inspect");
        addButtonGrid(
                action("Freeze", "Toggle freeze", () -> StaffCmds.runFmt("freeze %s", p)),
                action("Inventory", "Open their inventory", () -> {
                    closeToGame();
                    StaffCmds.runFmt("invsee %s", p);
                }),
                action("Ender chest", "Open their ender chest", () -> {
                    closeToGame();
                    StaffCmds.echest(p);
                }),
                action("Bag", "View their bag", () -> {
                    closeToGame();
                    StaffCmds.runFmt("bag see %s", p);
                }),
                action("Check", "Staff inspect", () -> StaffCmds.runFmt("check %s", p)),
                action("History", "Moderation history", () -> StaffCmds.runFmt("modhistory %s", p)),
                action("Modcheck", "Moderation summary", () -> StaffCmds.runFmt("modcheck %s", p))
        );

        addSection("Care");
        addButtonGrid(
                action("Heal", "Restore health", () -> StaffCmds.runFmt("yapadmin heal %s", p)),
                action("Feed", "Fill hunger", () -> StaffCmds.runFmt("yapadmin feed %s", p)),
                action("God", "Toggle god mode for them", () -> StaffCmds.runFmt("god %s", p)),
                action("Walk speed…", "Pick 1–10 for them", () ->
                        open(new SpeedPickerScreen(this, p, false))),
                action("Fly speed…", "Pick 1–10 for them", () ->
                        open(new SpeedPickerScreen(this, p, true))),
                action("Clear inv", "Clear their inventory", () -> StaffCmds.runFmt("yapadmin clear %s", p)),
                action("Creative", "Set them to creative", () -> StaffCmds.runFmt("gmc %s", p))
        );

        addSection("Ranks & economy");
        addButtonGrid(
                action("Promote", "Promote on default track", () -> StaffCmds.runFmt("promote %s", p)),
                action("Demote", "Demote on default track", () -> StaffCmds.runFmt("demote %s", p)),
                action("Ranks…", "Primary rank and parents", () ->
                        open(new PlayerRanksScreen(this))),
                action("Permissions…", "Allow or deny nodes", () ->
                        open(new PermEditorScreen(this, PermEditorScreen.Scope.USER, null))),
                action("Money…", "Grant money", () -> open(new EconomyScreen(this))),
                action("Give…", "Give items", () -> open(new GiveScreen(this))),
                action("Spawn mobs…", "Spawn entities on them", () -> open(new MobsScreen(this)))
        );

        addSection("Moderation");
        addButtonGrid(
                action("Kick", "Remove from server", () -> StaffCmds.mod("kick", p, "Staff action")),
                action("Warn", "Record a warning", () -> StaffCmds.mod("warn", p, "Staff action")),
                action("Mute 1h", "Mute for one hour", () -> StaffCmds.mod("mute", p, "Staff action")),
                action("Tempban 1d", "Ban for one day", () -> StaffCmds.mod("tempban", p, "Staff action")),
                action("Trolls…", "Smite, launch, burn…", () -> open(new TrollsScreen(this)))
        );
    }
}
