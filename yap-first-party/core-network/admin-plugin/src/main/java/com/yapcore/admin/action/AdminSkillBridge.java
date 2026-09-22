package com.yapcore.admin.action;

import com.yapcore.mmo.SkillDefinition;
import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.SkillService;
import com.yapcore.mmo.SkillServices;
import com.yapcore.mmo.XpSource;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Soft-hooks YaPSkills for admin give-XP / set-level. */
public final class AdminSkillBridge {

    public record SkillRow(String id, String display, Material icon, boolean enabled) {
    }

    private AdminSkillBridge() {
    }

    public static boolean available() {
        return SkillServices.find().isPresent();
    }

    public static List<SkillRow> skills() {
        Optional<SkillService> svc = SkillServices.find();
        if (svc.isEmpty()) {
            return List.of();
        }
        List<SkillRow> out = new ArrayList<>();
        for (SkillDefinition def : svc.get().definitions()) {
            if (def == null || def.id() == null) {
                continue;
            }
            Material icon = def.icon() == null ? Material.EXPERIENCE_BOTTLE : def.icon();
            String display = def.display() == null || def.display().isBlank()
                    ? def.id().id() : def.display();
            out.add(new SkillRow(def.id().id(), display, icon, def.enabled()));
        }
        out.sort(Comparator.comparing(SkillRow::display, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public static int maxLevel() {
        return SkillServices.find()
                .map(s -> s.xpTable().maxLevel())
                .orElse(120);
    }

    public static void addXp(Player admin, Player target, String skillId, double amount) {
        Optional<SkillService> svc = SkillServices.find();
        if (svc.isEmpty()) {
            admin.sendMessage("§cYaPSkills is not loaded.");
            return;
        }
        SkillId id = SkillId.of(skillId);
        svc.get().addXp(target.getUniqueId(), id, amount, XpSource.COMMAND)
                .thenAccept(progress -> admin.sendMessage(
                        "§a+" + format(amount) + " XP §7→ §f" + target.getName()
                                + " §7/ §f" + skillId + " §7(lv §f" + progress.level() + "§7)"))
                .exceptionally(ex -> {
                    admin.sendMessage("§cFailed to add XP: " + ex.getMessage());
                    return null;
                });
    }

    public static void setLevel(Player admin, Player target, String skillId, int level) {
        Optional<SkillService> svc = SkillServices.find();
        if (svc.isEmpty()) {
            admin.sendMessage("§cYaPSkills is not loaded.");
            return;
        }
        SkillId id = SkillId.of(skillId);
        svc.get().setLevel(target.getUniqueId(), id, level, XpSource.ADMIN)
                .thenAccept(progress -> admin.sendMessage(
                        "§aSet §f" + target.getName() + "§a's §f" + skillId
                                + " §ato level §f" + progress.level()))
                .exceptionally(ex -> {
                    admin.sendMessage("§cFailed to set level: " + ex.getMessage());
                    return null;
                });
    }

    private static String format(double xp) {
        if (xp >= 1000) {
            return String.format("%.0f", xp);
        }
        if (xp == Math.rint(xp)) {
            return String.format("%.0f", xp);
        }
        return String.format("%.1f", xp);
    }
}
