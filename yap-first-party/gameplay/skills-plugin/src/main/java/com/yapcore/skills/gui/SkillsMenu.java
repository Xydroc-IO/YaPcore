package com.yapcore.skills.gui;

import com.yapcore.mmo.SkillDefinition;
import com.yapcore.mmo.SkillProgress;
import com.yapcore.mmo.XpTable;
import com.yapcore.sched.YapSched;
import com.yapcore.skills.service.SkillServiceImpl;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class SkillsMenu {

    private static final int[] SKILL_SLOTS = {
            10, 11, 12, 14, 15, 16,
            19, 20, 21, 23, 24, 25
    };
    private static final int COMBAT_SLOT = 13;

    private final JavaPlugin plugin;
    private final SkillServiceImpl skills;

    public SkillsMenu(JavaPlugin plugin, SkillServiceImpl skills) {
        this.plugin = plugin;
        this.skills = skills;
    }

    public void open(Player viewer, UUID targetId, String targetName) {
        skills.getAll(targetId).thenAccept(all -> YapSched.entity(plugin, viewer, () -> {
            if (!viewer.isOnline()) {
                return;
            }
            openSync(viewer, targetId, targetName, all);
        }));
    }

    private void openSync(Player viewer, UUID targetId, String targetName, Collection<SkillProgress> progressList) {
        SkillsMenuHolder holder = new SkillsMenuHolder(targetId);
        var inv = Bukkit.createInventory(holder, 54,
                Component.text("Skills — " + targetName, NamedTextColor.GOLD));
        holder.bind(inv);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, SkillsMenuHolder.filler());
        }

        XpTable table = skills.xpTable();
        List<SkillDefinition> enabled = skills.definitions().stream()
                .filter(SkillDefinition::enabled)
                .sorted(Comparator.comparing(def -> def.display().toLowerCase()))
                .toList();

        int combatLevel = skills.combatLevel(targetId);
        inv.setItem(COMBAT_SLOT, SkillsMenuHolder.combatLevelIcon(combatLevel, table.maxLevel()));

        int slotIndex = 0;
        for (SkillDefinition def : enabled) {
            if (slotIndex >= SKILL_SLOTS.length) {
                break;
            }
            SkillProgress progress = progressList.stream()
                    .filter(p -> p.skillId().equals(def.id()))
                    .findFirst()
                    .orElse(new SkillProgress(targetId, def.id(), 0, 1));
            double xpInto = table.xpIntoLevel(progress.xp(), progress.level());
            double xpToNext = table.xpBetweenLevels(progress.level());
            inv.setItem(SKILL_SLOTS[slotIndex], SkillsMenuHolder.skillIcon(
                    def.icon(),
                    def.iconCmd(),
                    def.display(),
                    progress.level(),
                    progress.xp(),
                    xpInto,
                    xpToNext,
                    table.maxLevel()));
            slotIndex++;
        }

        viewer.openInventory(inv);
    }
}
