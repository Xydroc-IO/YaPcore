package com.yapcore.mmobedrock.ui;

import com.yapcore.abilities.AbilityCategory;
import com.yapcore.abilities.AbilityDefinition;
import com.yapcore.abilities.AbilityServices;
import com.yapcore.abilities.CastResult;
import com.yapcore.bedrock.ui.BedrockFormResult;
import com.yapcore.bedrock.ui.BedrockUiService;
import com.yapcore.mmo.CombatService;
import com.yapcore.mmo.CombatServices;
import com.yapcore.mmo.CombatStats;
import com.yapcore.mmo.CraftingRecipe;
import com.yapcore.mmo.CraftingService;
import com.yapcore.mmo.HiscoreEntry;
import com.yapcore.mmo.MmoServices;
import com.yapcore.mmo.SkillDefinition;
import com.yapcore.mmo.CombatLevelCalculator;
import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.SkillProgress;
import com.yapcore.mmo.SkillService;
import com.yapcore.mmo.SkillServices;
import com.yapcore.mmo.XpTable;
import com.yapcore.mmobedrock.MmoBedrockConfig;
import com.yapcore.npcs.QuestProgress;
import com.yapcore.npcs.QuestService;
import com.yapcore.npcs.QuestServices;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class MmoBedrockUi {

    private final JavaPlugin plugin;
    private final MmoBedrockConfig config;
    private final BedrockUiService bedrock;

    public MmoBedrockUi(JavaPlugin plugin, MmoBedrockConfig config, BedrockUiService bedrock) {
        this.plugin = plugin;
        this.config = config;
        this.bedrock = bedrock;
    }

    public void openHub(Player player) {
        if (!bedrock.hasNativeSession(player)) {
            bedrock.sendActionBar(player, "MMO forms need native Bedrock UDP (not Floodgate-only)");
            player.sendMessage("§eYaP MMO forms require a native YaPcore Bedrock session. "
                    + "Floodgate-only players get action bar / sidebar fallbacks — join via dual-stack UDP for forms.");
            return;
        }
        bedrock.sendSimpleForm(
                player,
                "YaP MMO",
                "Choose a panel:",
                result -> handleHub(player, result),
                "Skills",
                "Quests",
                "Abilities",
                "Recipes",
                "Hiscores",
                "Close");
    }

    private void handleHub(Player player, BedrockFormResult result) {
        if (result.cancelled()) {
            return;
        }
        YapSched.entity(plugin, player, () -> {
            switch (result.buttonIndex()) {
                case 0 -> openSkills(player);
                case 1 -> openQuests(player);
                case 2 -> openAbilities(player, null, 1);
                case 3 -> openSkillPicker(player, "recipes");
                case 4 -> openSkillPicker(player, "hiscores");
                default -> { }
            }
        });
    }

    private void openSkillPicker(Player player, String target) {
        SkillService skills = SkillServices.find().orElse(null);
        if (skills == null) {
            bedrock.sendSimpleForm(player, "Skills", "Skills system not loaded.", null, "Back");
            return;
        }
        List<SkillDefinition> defs = skills.definitions().stream()
                .filter(SkillDefinition::enabled)
                .sorted(Comparator.comparing(d -> d.display().toLowerCase(Locale.ROOT)))
                .toList();
        List<String> buttons = new ArrayList<>();
        for (SkillDefinition def : defs) {
            buttons.add(def.display());
        }
        buttons.add("Back");
        String title = "recipes".equals(target) ? "Recipe skill" : "Hiscore skill";
        bedrock.sendSimpleForm(
                player,
                title,
                "Pick a skill:",
                result -> {
                    if (result.cancelled()) {
                        return;
                    }
                    YapSched.entity(plugin, player, () -> {
                        int idx = result.buttonIndex();
                        if (idx < 0 || idx >= buttons.size() || "Back".equals(buttons.get(idx))) {
                            openHub(player);
                            return;
                        }
                        SkillId skill = defs.get(idx).id();
                        if ("recipes".equals(target)) {
                            openRecipes(player, skill, 1);
                        } else {
                            openHiscores(player, skill, 1);
                        }
                    });
                },
                buttons.toArray(new String[0]));
    }

    public void openQuests(Player player) {
        QuestService quests = QuestServices.find().orElse(null);
        if (quests == null) {
            bedrock.sendSimpleForm(player, "Quests", "Quest system not loaded (yap-npcs).", r -> openHub(player), "Back");
            return;
        }
        StringBuilder body = new StringBuilder();
        List<String> lines = new ArrayList<>();
        for (String questId : quests.questIds()) {
            if (quests.isQuestComplete(player, questId)) {
                lines.add(formatQuestId(questId) + " — ready to turn in");
                continue;
            }
            List<QuestProgress> progress = quests.progressFor(player).stream()
                    .filter(p -> p.questId().equals(questId))
                    .filter(p -> p.required() > 0)
                    .toList();
            if (progress.isEmpty()) {
                continue;
            }
            boolean anyOpen = progress.stream().anyMatch(p -> !p.completed());
            if (!anyOpen) {
                continue;
            }
            lines.add(formatQuestId(questId) + ":");
            for (QuestProgress p : progress) {
                if (p.completed()) {
                    continue;
                }
                lines.add("  " + p.objectiveId() + " " + p.progress() + "/" + p.required());
            }
            if (lines.size() >= 12) {
                break;
            }
        }
        if (lines.isEmpty()) {
            body.append("No active objectives.\nTalk to quest NPCs to start or turn in.");
        } else {
            for (String line : lines) {
                body.append(line).append('\n');
            }
        }
        bedrock.sendSimpleForm(player, "Quests", body.toString(), r -> {
            if (!r.cancelled() && r.buttonIndex() == 0) {
                YapSched.entity(plugin, player, () -> openHub(player));
            }
        }, "Back");
    }

    private static String formatQuestId(String questId) {
        if (questId == null || questId.isBlank()) {
            return "Quest";
        }
        return questId.replace('_', ' ');
    }

    public void openAbilities(Player player, AbilityCategory category, int page) {
        var abilities = AbilityServices.find().orElse(null);
        if (abilities == null) {
            bedrock.sendSimpleForm(player, "Abilities", "Ability engine not loaded.", null, "OK");
            return;
        }
        List<AbilityDefinition> sorted = abilities.definitions().stream()
                .filter(a -> category == null || a.category() == category)
                .sorted(Comparator.comparing(AbilityDefinition::displayName))
                .toList();
        Pagination.Page<AbilityDefinition> pagination = Pagination.slice(sorted, page, 6);
        if (pagination.items().isEmpty()) {
            bedrock.sendSimpleForm(player, "Abilities", "No abilities found.", r -> openHub(player), "Back");
            return;
        }
        List<String> buttons = new ArrayList<>();
        for (AbilityDefinition def : pagination.items()) {
            buttons.add(def.displayName() + " [" + def.category().name().toLowerCase(Locale.ROOT) + "]");
        }
        if (pagination.hasPrev()) {
            buttons.add("Prev");
        }
        if (pagination.hasNext()) {
            buttons.add("Next");
        }
        buttons.add("Back");
        String body = "Page " + pagination.page() + "/" + pagination.totalPages()
                + " — tap to cast";
        bedrock.sendSimpleForm(
                player,
                "Spellbook",
                body,
                result -> handleAbilityNav(player, category, pagination, result, buttons),
                buttons.toArray(new String[0]));
    }

    private void handleAbilityNav(
            Player player,
            AbilityCategory category,
            Pagination.Page<AbilityDefinition> pagination,
            BedrockFormResult result,
            List<String> buttons) {
        if (result.cancelled()) {
            return;
        }
        YapSched.entity(plugin, player, () -> {
            int idx = result.buttonIndex();
            if (idx < 0 || idx >= buttons.size()) {
                return;
            }
            String picked = buttons.get(idx);
            if ("Prev".equals(picked)) {
                openAbilities(player, category, pagination.page() - 1);
            } else if ("Next".equals(picked)) {
                openAbilities(player, category, pagination.page() + 1);
            } else if ("Back".equals(picked)) {
                openHub(player);
            } else if (idx < pagination.items().size()) {
                AbilityDefinition def = pagination.items().get(idx);
                CastResult cast = AbilityServices.find()
                        .map(s -> s.cast(player, def.id()))
                        .orElse(CastResult.FAILED);
                if (!cast.ok()) {
                    player.sendMessage("§cCast failed: §7" + cast.name().toLowerCase(Locale.ROOT).replace('_', ' '));
                }
            }
        });
    }

    public void openSkills(Player player) {
        SkillService skills = SkillServices.find().orElse(null);
        if (skills == null) {
            bedrock.sendSimpleForm(player, "Skills", "Skills system not loaded.", null, "OK");
            return;
        }
        List<SkillDefinition> defs = skills.definitions().stream()
                .filter(SkillDefinition::enabled)
                .sorted(Comparator.comparing(d -> d.display().toLowerCase(Locale.ROOT)))
                .toList();
        List<String> buttons = new ArrayList<>();
        for (SkillDefinition def : defs) {
            buttons.add(def.display());
        }
        buttons.add("Back");
        bedrock.sendSimpleForm(
                player,
                "Skills",
                "Combat level: " + combatLevel(skills, player) + "\nTap a skill for details.",
                result -> {
                    if (result.cancelled()) {
                        return;
                    }
                    YapSched.entity(plugin, player, () -> {
                        int idx = result.buttonIndex();
                        if (idx < 0 || idx >= buttons.size() || "Back".equals(buttons.get(idx))) {
                            openHub(player);
                            return;
                        }
                        openSkillDetail(player, defs.get(idx).id());
                    });
                },
                buttons.toArray(new String[0]));
    }

    private void openSkillDetail(Player player, SkillId skillId) {
        SkillService skills = SkillServices.find().orElse(null);
        if (skills == null) {
            openHub(player);
            return;
        }
        skills.get(player.getUniqueId(), skillId).thenAccept(progress -> YapSched.entity(plugin, player, () -> {
            if (!player.isOnline()) {
                return;
            }
            SkillDefinition def = skills.definitions().stream()
                    .filter(d -> d.id().equals(skillId))
                    .findFirst()
                    .orElse(null);
            String name = def != null ? def.display() : capitalize(skillId.id());
            XpTable table = skills.xpTable();
            StringBuilder body = new StringBuilder();
            body.append(name).append("\n\n");
            body.append("Level: ").append(progress.level()).append(" / ").append(table.maxLevel()).append('\n');
            if (progress.level() < table.maxLevel()) {
                double into = table.xpIntoLevel(progress.xp(), progress.level());
                double need = table.xpBetweenLevels(progress.level());
                body.append("XP: ").append((int) into).append(" / ").append((int) need).append('\n');
                body.append("Total XP: ").append((int) progress.xp());
            }
            bedrock.sendSimpleForm(player, name, body.toString(), r -> {
                if (!r.cancelled() && r.buttonIndex() == 0) {
                    YapSched.entity(plugin, player, () -> openSkills(player));
                }
            }, "Back");
        }));
    }

    public void openRecipes(Player player, SkillId skill, int page) {
        CraftingService crafting = findCrafting().orElse(null);
        if (crafting == null) {
            bedrock.sendSimpleForm(player, "Recipes", "Crafting not loaded.", null, "Back");
            return;
        }
        List<CraftingRecipe> all = new ArrayList<>(crafting.recipesForSkill(skill));
        all.sort(Comparator.comparingInt(CraftingRecipe::level));
        Pagination.Page<CraftingRecipe> slice = Pagination.slice(all, page, config.recipePageSize());
        StringBuilder body = new StringBuilder();
        body.append("Skill: ").append(capitalize(skill.id())).append('\n');
        body.append("Page ").append(slice.page()).append(" / ").append(slice.totalPages()).append('\n');
        int playerLevel = skillLevel(player, skill);
        body.append("Your level: ").append(playerLevel).append("\n\n");
        if (slice.items().isEmpty()) {
            body.append("No recipes.");
        } else {
            for (CraftingRecipe recipe : slice.items()) {
                String mark = playerLevel >= recipe.level() ? "[OK]" : "[Lv" + recipe.level() + "]";
                body.append(mark).append(' ').append(recipe.displayName()).append('\n');
            }
        }
        List<String> buttons = new ArrayList<>();
        if (slice.hasPrev()) {
            buttons.add("Prev");
        }
        if (slice.hasNext()) {
            buttons.add("Next");
        }
        buttons.add("Back");
        bedrock.sendSimpleForm(
                player,
                "Recipes",
                body.toString(),
                result -> handleRecipeNav(player, skill, slice, result, buttons),
                buttons.toArray(String[]::new));
    }

    private void handleRecipeNav(
            Player player,
            SkillId skill,
            Pagination.Page<CraftingRecipe> slice,
            BedrockFormResult result,
            List<String> buttons) {
        if (result.cancelled()) {
            return;
        }
        int idx = result.buttonIndex();
        if (idx < 0 || idx >= buttons.size()) {
            return;
        }
        String picked = buttons.get(idx);
        YapSched.entity(plugin, player, () -> {
            if ("Prev".equals(picked)) {
                openRecipes(player, skill, slice.page() - 1);
            } else if ("Next".equals(picked)) {
                openRecipes(player, skill, slice.page() + 1);
            } else {
                openSkillPicker(player, "recipes");
            }
        });
    }

    public void openHiscores(Player player, SkillId skill, int page) {
        MmoServices.snapshot().ifPresentOrElse(snap -> YapSched.async(plugin, () -> {
            int pageSize = config.hiscorePageSize();
            List<HiscoreEntry> rows = snap.hiscorePage(skill, pageSize, page);
            boolean hasNext = rows.size() >= pageSize;
            YapSched.entity(plugin, player, () -> renderHiscores(player, skill, page, hasNext, rows));
        }), () -> bedrock.sendSimpleForm(player, "Hiscores", "MMO content not loaded.", r -> openHub(player), "Back"));
    }

    private void renderHiscores(Player player, SkillId skill, int page, boolean hasNext, List<HiscoreEntry> rows) {
        StringBuilder body = new StringBuilder();
        body.append("Skill: ").append(capitalize(skill.id())).append('\n');
        body.append("Page ").append(page).append('\n');
        if (rows.isEmpty()) {
            body.append("\nNo entries yet.");
        } else {
            body.append('\n');
            for (HiscoreEntry row : rows) {
                body.append('#').append(row.rank()).append(' ')
                        .append(row.playerName())
                        .append(" — Lv ").append(row.level())
                        .append(" (").append((int) row.xp()).append(" xp)\n");
            }
        }
        List<String> buttons = new ArrayList<>();
        if (page > 1) {
            buttons.add("Prev");
        }
        if (hasNext) {
            buttons.add("Next");
        }
        buttons.add("Back");
        bedrock.sendSimpleForm(
                player,
                "Hiscores",
                body.toString(),
                result -> handleHiscoreNav(player, skill, page, hasNext, result, buttons),
                buttons.toArray(String[]::new));
    }

    private void handleHiscoreNav(
            Player player,
            SkillId skill,
            int page,
            boolean hasNext,
            BedrockFormResult result,
            List<String> buttons) {
        if (result.cancelled()) {
            return;
        }
        int idx = result.buttonIndex();
        if (idx < 0 || idx >= buttons.size()) {
            return;
        }
        String picked = buttons.get(idx);
        YapSched.entity(plugin, player, () -> {
            if ("Prev".equals(picked)) {
                openHiscores(player, skill, page - 1);
            } else if ("Next".equals(picked)) {
                openHiscores(player, skill, page + 1);
            } else {
                openSkillPicker(player, "hiscores");
            }
        });
    }

    public void refreshCombatSidebar(Player player) {
        if (!bedrock.isBedrock(player)) {
            return;
        }
        CombatService combat = CombatServices.find().orElse(null);
        SkillService skills = SkillServices.find().orElse(null);
        if (combat == null || skills == null) {
            return;
        }
        YapSched.entity(plugin, player, () -> {
            CombatStats stats = combat.stats(player);
            int combatLevel = combatLevel(skills, player);
            List<String> lines = List.of(
                    "§6Combat §e" + combatLevel,
                    "§cHP §f" + stats.currentHp() + "/" + stats.maxHp(),
                    "§4ATK §f" + stats.attack() + " §cSTR §f" + stats.strength(),
                    "§9DEF §f" + stats.defence() + " §aHP §f" + stats.hitpoints());
            bedrock.updateSidebar(player, config.sidebarObjective(), "YaP MMO", lines);
        });
    }

    private int combatLevel(SkillService skills, Player player) {
        int attack = level(skills, player, "attack");
        int strength = level(skills, player, "strength");
        int defence = level(skills, player, "defence");
        int hitpoints = level(skills, player, "hitpoints");
        int prayer = level(skills, player, "prayer");
        int ranged = level(skills, player, "ranged");
        int magic = level(skills, player, "magic");
        return CombatLevelCalculator.calculate(
                attack, strength, defence, hitpoints, prayer, ranged, magic);
    }

    private static int level(SkillService skills, Player player, String id) {
        try {
            return skills.get(player.getUniqueId(), SkillId.of(id))
                    .orTimeout(2, TimeUnit.SECONDS)
                    .join()
                    .level();
        } catch (Exception e) {
            return 1;
        }
    }

    private int skillLevel(Player player, SkillId skill) {
        return SkillServices.find()
                .map(s -> {
                    try {
                        return s.get(player.getUniqueId(), skill)
                                .orTimeout(2, TimeUnit.SECONDS)
                                .join()
                                .level();
                    } catch (Exception e) {
                        return 1;
                    }
                })
                .orElse(1);
    }

    private static java.util.Optional<CraftingService> findCrafting() {
        var reg = Bukkit.getServicesManager().getRegistration(CraftingService.class);
        return reg == null ? java.util.Optional.empty() : java.util.Optional.of(reg.getProvider());
    }

    private static String capitalize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }
}
