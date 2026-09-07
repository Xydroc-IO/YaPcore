package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ServerOpsScreen extends StaffPanelScreen {

    private static final String[] BROADCASTS = {
            "Server restart in 5 minutes — please wrap up.",
            "Welcome to YaPcore — rules in /menu.",
            "Staff are online — ask in /staffchat."
    };

    public ServerOpsScreen(Screen parent) {
        super(Component.literal("Server"), parent);
    }

    @Override
    protected void addContents() {
        addSubtitle("Broadcasts · weather · plugin reloads · staff chat");
        addButtonGrid(
                action("Broadcast #1", BROADCASTS[0], () ->
                        StaffCmds.runFmt("yapadmin broadcast %s", BROADCASTS[0])),
                action("Broadcast #2", BROADCASTS[1], () ->
                        StaffCmds.runFmt("yapadmin broadcast %s", BROADCASTS[1])),
                action("Broadcast #3", BROADCASTS[2], () ->
                        StaffCmds.runFmt("yapadmin broadcast %s", BROADCASTS[2])),
                action("Clear weather", "/weather clear", () -> StaffCmds.run("weather clear")),
                action("Rain", "/weather rain", () -> StaffCmds.run("weather rain")),
                action("Thunder", "/weather thunder", () -> StaffCmds.run("weather thunder")),
                action("Disasters GUI", "/yapdisaster", () -> {
                    if (minecraft != null) {
                        closeToGame();
                    }
                    StaffCmds.run("yapdisaster");
                }),
                action("Staff chat", "/staffchat", () -> StaffCmds.run("staffchat")),
                action("Admin chat", "/adminchat", () -> StaffCmds.run("adminchat")),
                action("Clear chat", "/clearchat", () -> StaffCmds.run("clearchat")),
                action("Reload YaPAdmin", null, () -> StaffCmds.run("yapadmin reload")),
                action("Reload Essentials", null, () -> StaffCmds.run("yapess reload")),
                action("Reload Moderation", null, () -> StaffCmds.run("yapmod reload")),
                action("Plugins list", "/yapplugins list", () -> StaffCmds.run("yapplugins list")),
                action("Guard status", "/yapguard status", () -> StaffCmds.run("yapguard status")),
                action("Knobs reload", "/yapknobs reload", () -> StaffCmds.run("yapknobs reload"))
        );
    }
}
