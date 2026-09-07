package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class LinksScreen extends StaffPanelScreen {

    public LinksScreen(Screen parent) {
        super(Component.literal("Deep links"), parent);
    }

    @Override
    protected void addContents() {
        addSubtitle("Chest GUIs close this screen. Ranks (native) stays in staff UI.");
        addButtonGrid(
                action("Ranks (native)…", "Full staff ranks UI", () ->
                        open(new RanksHubScreen(this))),
                link("Ranks chest GUI", "yapperm gui"),
                link("World edit", "yapworld gui"),
                link("Stacker", "yapstacker gui"),
                link("Player menu", "menu"),
                link("Skills", "skills"),
                link("Dungeons", "dungeon"),
                link("Disasters", "yapdisaster"),
                link("Admin chest", "yapadmin chest"),
                link("Pregen", "yappregen"),
                link("Regions", "yapregions"),
                link("Map", "yapmap"),
                link("NPCs", "yapnpc")
        );
    }

    private net.minecraft.client.gui.components.Button link(String label, String command) {
        return action(label, "/" + command, () -> {
            if (minecraft != null) {
                closeToGame();
            }
            StaffCmds.run(command);
        });
    }
}
