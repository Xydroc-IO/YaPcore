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
        addSection("Broadcast");
        addButtonGrid(
                action("Restart notice", BROADCASTS[0], () ->
                        StaffCmds.runFmt("yapadmin broadcast %s", BROADCASTS[0])),
                action("Welcome", BROADCASTS[1], () ->
                        StaffCmds.runFmt("yapadmin broadcast %s", BROADCASTS[1])),
                action("Staff online", BROADCASTS[2], () ->
                        StaffCmds.runFmt("yapadmin broadcast %s", BROADCASTS[2]))
        );

        addSection("Weather");
        addButtonGrid(
                action("Clear", "Clear weather", () -> StaffCmds.run("weather clear")),
                action("Rain", "Start rain", () -> StaffCmds.run("weather rain")),
                action("Thunder", "Start thunderstorm", () -> StaffCmds.run("weather thunder")),
                action("Disasters", "Open disasters panel", () -> {
                    closeToGame();
                    StaffCmds.run("yapdisaster");
                })
        );

        addSection("Chat");
        addButtonGrid(
                action("Staff chat", "Toggle staff chat", () -> StaffCmds.run("staffchat")),
                action("Admin chat", "Toggle admin chat", () -> StaffCmds.run("adminchat")),
                action("Clear chat", "Clear the chat for everyone", () -> StaffCmds.run("clearchat"))
        );

        addSection("Reload");
        addButtonGrid(
                action("Reload Admin", "Reload YaPAdmin config", () -> StaffCmds.run("yapadmin reload")),
                action("Reload Essentials", "Reload YaPEssentials", () -> StaffCmds.run("yapess reload")),
                action("Reload Moderation", "Reload YaPModeration", () -> StaffCmds.run("yapmod reload")),
                action("Plugins", "List plugins", () -> StaffCmds.run("yapplugins list")),
                action("Guard", "Anti-cheat status", () -> StaffCmds.run("yapguard status")),
                action("Knobs", "Reload gameplay knobs", () -> StaffCmds.run("yapknobs reload"))
        );
    }
}
