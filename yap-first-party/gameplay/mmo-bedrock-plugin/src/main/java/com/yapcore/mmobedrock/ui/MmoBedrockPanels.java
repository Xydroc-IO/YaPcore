package com.yapcore.mmobedrock.ui;

import com.yapcore.bedrock.ui.BedrockFormResult;
import com.yapcore.mmo.CraftingRecipe;
import com.yapcore.mmo.CraftingService;
import com.yapcore.mmo.HiscoreEntry;
import com.yapcore.mmo.MmoServices;
import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.SkillServices;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Recipes / hiscores Bedrock panels. */
final class MmoBedrockPanels {

    private final MmoBedrockUi host;

    MmoBedrockPanels(MmoBedrockUi host) {
        this.host = host;
    }

    void openRecipes(Player player, SkillId skill, int page) {
        CraftingService crafting = findCrafting().orElse(null);
        if (crafting == null) {
            host.bedrock().sendSimpleForm(player, "Recipes", "Crafting not loaded.", null, "Back");
            return;
        }
        List<CraftingRecipe> all = new ArrayList<>(crafting.recipesForSkill(skill));
        all.sort(Comparator.comparingInt(CraftingRecipe::level));
        Pagination.Page<CraftingRecipe> slice = Pagination.slice(all, page, host.config().recipePageSize());
        StringBuilder body = new StringBuilder();
        body.append("Skill: ").append(MmoBedrockUi.capitalize(skill.id())).append('\n');
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
        host.bedrock().sendSimpleForm(
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
        YapSched.entity(host.plugin(), player, () -> {
            if ("Prev".equals(picked)) {
                openRecipes(player, skill, slice.page() - 1);
            } else if ("Next".equals(picked)) {
                openRecipes(player, skill, slice.page() + 1);
            } else {
                host.openSkillPickerInternal(player, "recipes");
            }
        });
    }

    void openHiscores(Player player, SkillId skill, int page) {
        MmoServices.snapshot().ifPresentOrElse(snap -> YapSched.async(host.plugin(), () -> {
            int pageSize = host.config().hiscorePageSize();
            List<HiscoreEntry> rows = snap.hiscorePage(skill, pageSize, page);
            boolean hasNext = rows.size() >= pageSize;
            YapSched.entity(host.plugin(), player, () -> renderHiscores(player, skill, page, hasNext, rows));
        }), () -> host.bedrock().sendSimpleForm(player, "Hiscores", "MMO content not loaded.", r -> host.openHubInternal(player), "Back"));
    }

    private void renderHiscores(Player player, SkillId skill, int page, boolean hasNext, List<HiscoreEntry> rows) {
        StringBuilder body = new StringBuilder();
        body.append("Skill: ").append(MmoBedrockUi.capitalize(skill.id())).append('\n');
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
        host.bedrock().sendSimpleForm(
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
        YapSched.entity(host.plugin(), player, () -> {
            if ("Prev".equals(picked)) {
                openHiscores(player, skill, page - 1);
            } else if ("Next".equals(picked)) {
                openHiscores(player, skill, page + 1);
            } else {
                host.openSkillPickerInternal(player, "hiscores");
            }
        });
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
}
