package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import com.yapcore.staff.YapStaffClient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Per-player kitchen sink: TP, inspect, heal, ranks, mod, trolls. */
public final class PlayerActionsScreen extends StaffPanelScreen {

    private final String playerName;

    public PlayerActionsScreen(Screen parent, String playerName) {
        super(Component.literal("Player: " + playerName), parent);
        this.playerName = playerName;
        YapStaffClient.session().setTarget(playerName);
    }

    @Override
    protected void addContents() {
        addSubtitle("Target locked: " + playerName);
        String p = playerName;
        addButtonGrid(
                action("TP to", "Teleport to them", () -> StaffCmds.runFmt("yapadmin tp %s", p)),
                action("TP here", "Bring them to you", () -> StaffCmds.runFmt("yapadmin tphere %s", p)),
                action("TP spawn", "Send to world spawn", () -> StaffCmds.runFmt("yapadmin tpspawn %s", p)),
                action("Freeze", "Toggle freeze", () -> StaffCmds.runFmt("freeze %s", p)),
                action("Invsee", "Open inventory", () -> {
                    if (minecraft != null) {
                        closeToGame();
                    }
                    StaffCmds.runFmt("invsee %s", p);
                }),
                action("Ender chest", "Open echest", () -> {
                    if (minecraft != null) {
                        closeToGame();
                    }
                    StaffCmds.runFmt("echest %s", p);
                }),
                action("Heal", null, () -> StaffCmds.runFmt("yapadmin heal %s", p)),
                action("Feed", null, () -> StaffCmds.runFmt("yapadmin feed %s", p)),
                action("Clear inv", "Requires clear perm", () -> StaffCmds.runFmt("yapadmin clear %s", p)),
                action("Promote", "/promote", () -> StaffCmds.runFmt("promote %s", p)),
                action("Demote", "/demote", () -> StaffCmds.runFmt("demote %s", p)),
                action("Ranks…", "Set primary · parents · perms", () ->
                        open(new PlayerRanksScreen(this))),
                action("Permissions…", "Allow / deny / unset nodes", () ->
                        open(new PermEditorScreen(this, PermEditorScreen.Scope.USER, null))),
                action("Give money…", "Economy grants for this player", () ->
                        open(new EconomyScreen(this))),
                action("Give items…", "Open give for this player", () ->
                        open(new GiveScreen(this))),
                action("Kick", null, () -> StaffCmds.runFmt("yapadmin kick %s Staff action", p)),
                action("Warn", null, () -> StaffCmds.runFmt("yapadmin warn %s Staff action", p)),
                action("Mute 1h", null, () -> StaffCmds.runFmt("yapadmin mute %s Staff action", p)),
                action("Tempban 1d", null, () -> StaffCmds.runFmt("yapadmin tempban %s Staff action", p)),
                action("Check", "/check", () -> StaffCmds.runFmt("check %s", p)),
                action("Mod history", "/modhistory", () -> StaffCmds.runFmt("modhistory %s", p)),
                action("Modcheck", "/modcheck", () -> StaffCmds.runFmt("modcheck %s", p)),
                action("Trolls…", "Smite / launch / burn…", () ->
                        open(new TrollsScreen(this))),
                action("Bag see", "/bag see", () -> {
                    if (minecraft != null) {
                        closeToGame();
                    }
                    StaffCmds.runFmt("bag see %s", p);
                }),
                action("Creative them", "/gmc <player>", () -> StaffCmds.runFmt("gmc %s", p))
        );
    }
}
