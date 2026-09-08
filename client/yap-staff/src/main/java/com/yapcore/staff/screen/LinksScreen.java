package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class LinksScreen extends StaffPanelScreen {

    public LinksScreen(Screen parent) {
        super(Component.literal("More"), parent);
    }

    @Override
    protected void addContents() {
        addSubtitle("Opens in-game menus (closes this screen)");

        addSection("Ranks");
        addButtonGrid(
                action("Ranks (this menu)", "Full ranks editor", () ->
                        open(new RanksHubScreen(this))),
                link("Ranks chest", "Open YaPPerms chest GUI", "yapperm gui")
        );

        addSection("World & build");
        addButtonGrid(
                link("World edit chest", "Open YaPWorld panel", "yapworld gui"),
                action("World edit", "Native YaPWorld tools", () ->
                        open(new WorldEditHubScreen(this))),
                link("Pregen status", "Chunk pre-generator status", "yappregen status"),
                link("Regions", "Region tools", "yapregions")
        );

        addSection("Gameplay");
        addButtonGrid(
                link("Stacker", "Mob stacker GUI", "yapstacker gui"),
                link("Player menu", "Open /menu", "menu"),
                link("Skills", "Skills menu", "skills"),
                link("Dungeons", "Dungeon tools", "dungeon"),
                link("Disasters", "Weather / disasters", "yapdisaster"),
                link("NPCs", "NPC tools", "yapnpc")
        );

        addSection("Ops");
        addButtonGrid(
                link("Admin chest", "YaP Admin chest hub", "yapadmin chest"),
                link("Map", "Web map info", "yapmap")
        );
    }

    private net.minecraft.client.gui.components.Button link(String label, String tip, String command) {
        return action(label, tip, () -> {
            closeToGame();
            StaffCmds.run(command);
        });
    }
}
