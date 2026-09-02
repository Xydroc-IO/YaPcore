package com.yapcore.abilities.book;

import com.yapcore.abilities.AbilityCategory;
import com.yapcore.abilities.AbilityDefinition;
import com.yapcore.abilities.AbilityService;
import com.yapcore.abilities.bar.AbilityBarService;
import com.yapcore.bedrock.ui.BedrockFormResult;
import com.yapcore.bedrock.ui.BedrockUiService;
import com.yapcore.bedrock.ui.BedrockUiServices;
import com.yapcore.mmo.SkillProgress;
import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class AbilityBookBedrockUi {

    private final JavaPlugin plugin;
    private final AbilityBookConfig config;
    private final AbilityService abilities;
    private final AbilityBarService bar;

    public AbilityBookBedrockUi(
            JavaPlugin plugin,
            AbilityBookConfig config,
            AbilityService abilities,
            AbilityBarService bar
    ) {
        this.plugin = plugin;
        this.config = config;
        this.abilities = abilities;
        this.bar = bar;
    }

    public void open(Player player) {
        BedrockUiService bedrock = BedrockUiServices.find().orElse(null);
        if (bedrock == null) {
            return;
        }
        bedrock.sendSimpleForm(
                player,
                config.title(),
                "Choose a category to browse unlocked abilities:",
                result -> handleCategoryPick(player, bedrock, result),
                "All",
                "Magic",
                "Ranged",
                "Melee",
                "Prayer",
                "Utility",
                "View bar",
                "Close");
    }

    private void handleCategoryPick(Player player, BedrockUiService bedrock, BedrockFormResult result) {
        if (result.cancelled()) {
            return;
        }
        YapSched.entity(plugin, player, () -> {
            switch (result.buttonIndex()) {
                case 0 -> openAbilityList(player, bedrock, null, 1);
                case 1 -> openAbilityList(player, bedrock, AbilityCategory.MAGIC, 1);
                case 2 -> openAbilityList(player, bedrock, AbilityCategory.RANGED, 1);
                case 3 -> openAbilityList(player, bedrock, AbilityCategory.MELEE, 1);
                case 4 -> openAbilityList(player, bedrock, AbilityCategory.PRAYER, 1);
                case 5 -> openAbilityList(player, bedrock, AbilityCategory.UTILITY, 1);
                case 6 -> openBarOverview(player, bedrock);
                default -> { }
            }
        });
    }

    private void openAbilityList(Player player, BedrockUiService bedrock, AbilityCategory category, int page) {
        AbilitySkillData.load(plugin, player, all ->
                openAbilityListSync(player, bedrock, category, page, all));
    }

    private void openAbilityListSync(
            Player player,
            BedrockUiService bedrock,
            AbilityCategory category,
            int page,
            Collection<SkillProgress> skillData
    ) {
        List<AbilityDefinition> listed = AbilityUnlocks.sorted(
                abilities.definitions(), category, true, player, skillData);
        AbilityBookPagination.Page<AbilityDefinition> slice =
                AbilityBookPagination.slice(listed, page, 8);
        if (slice.items().isEmpty()) {
            bedrock.sendSimpleForm(player, config.title(),
                    listed.isEmpty() && abilities.definitions().isEmpty()
                            ? "No abilities loaded. Check plugins/YaPAbilities/abilities."
                            : "No abilities in this category.",
                    r -> open(player), "Back");
            return;
        }
        List<String> buttons = new ArrayList<>();
        for (AbilityDefinition def : slice.items()) {
            boolean unlocked = AbilityUnlocks.isUnlocked(player, def, skillData);
            buttons.add((unlocked ? "" : "[Locked] ") + def.displayName());
        }
        if (slice.hasPrev()) {
            buttons.add("Prev");
        }
        if (slice.hasNext()) {
            buttons.add("Next");
        }
        buttons.add("Back");
        String body = "Page " + slice.page() + "/" + slice.totalPages()
                + "\nTap an ability to bind it to your combat bar.";
        bedrock.sendSimpleForm(
                player,
                config.title(),
                body,
                result -> handleAbilityList(player, bedrock, category, slice, buttons, result),
                buttons.toArray(new String[0]));
    }

    private void handleAbilityList(
            Player player,
            BedrockUiService bedrock,
            AbilityCategory category,
            AbilityBookPagination.Page<AbilityDefinition> slice,
            List<String> buttons,
            BedrockFormResult result
    ) {
        if (result.cancelled()) {
            return;
        }
        YapSched.entity(plugin, player, () -> {
            int idx = result.buttonIndex();
            if (idx < 0 || idx >= buttons.size()) {
                return;
            }
            String label = buttons.get(idx);
            if ("Prev".equals(label)) {
                openAbilityList(player, bedrock, category, slice.page() - 1);
            } else if ("Next".equals(label)) {
                openAbilityList(player, bedrock, category, slice.page() + 1);
            } else if ("Back".equals(label)) {
                open(player);
            } else if (idx < slice.items().size()) {
                openInspect(player, bedrock, slice.items().get(idx), category, slice.page());
            }
        });
    }

    private void openInspect(
            Player player,
            BedrockUiService bedrock,
            AbilityDefinition ability,
            AbilityCategory category,
            int page
    ) {
        AbilitySkillData.load(plugin, player, all -> {
            boolean unlocked = AbilityUnlocks.isUnlocked(player, ability, all);
            String body = AbilityDescribe.inspectBody(ability, all);
            if (unlocked) {
                bedrock.sendSimpleForm(
                        player,
                        ability.displayName(),
                        body,
                        result -> {
                            if (result.cancelled()) {
                                return;
                            }
                            YapSched.entity(plugin, player, () -> {
                                if (result.buttonIndex() == 0) {
                                    openSlotPicker(player, bedrock, ability);
                                } else {
                                    openAbilityList(player, bedrock, category, page);
                                }
                            });
                        },
                        "Add to hotbar",
                        "Back");
            } else {
                bedrock.sendSimpleForm(
                        player,
                        ability.displayName(),
                        body + "\n\nLocked — raise the required skill to use this.",
                        result -> YapSched.entity(plugin, player, () ->
                                openAbilityList(player, bedrock, category, page)),
                        "Back");
            }
        });
    }

    private void openSlotPicker(Player player, BedrockUiService bedrock, AbilityDefinition ability) {
        int slots = bar.config().slotCount();
        int firstKey = bar.config().firstKey();
        List<String> buttons = new ArrayList<>();
        for (int i = 0; i < slots; i++) {
            String bound = bar.store().get(player.getUniqueId(), i);
            String suffix = bound == null || bound.isBlank()
                    ? " (empty)"
                    : " → " + abilities.get(bound).map(AbilityDefinition::displayName).orElse(bound);
            buttons.add("Key " + (firstKey + i) + suffix);
        }
        buttons.add("Back");
        bedrock.sendSimpleForm(
                player,
                ability.displayName(),
                "Bind to combat hotbar slot:",
                result -> {
                    if (result.cancelled()) {
                        return;
                    }
                    YapSched.entity(plugin, player, () -> {
                        int idx = result.buttonIndex();
                        if (idx < 0 || idx >= buttons.size()) {
                            return;
                        }
                        if ("Back".equals(buttons.get(idx))) {
                            open(player);
                            return;
                        }
                        if (idx < slots) {
                            if (!player.hasPermission("yapabilities.bar")) {
                                player.sendMessage("§cNo permission to edit ability bar.");
                                return;
                            }
                            bar.bind(player, idx + 1, ability.id(), true);
                            player.sendMessage("§aBound §f" + ability.displayName()
                                    + " §7→ key §e" + (firstKey + idx));
                            openBarOverview(player, bedrock);
                        }
                    });
                },
                buttons.toArray(new String[0]));
    }

    private void openBarOverview(Player player, BedrockUiService bedrock) {
        int slots = bar.config().slotCount();
        int firstKey = bar.config().firstKey();
        StringBuilder body = new StringBuilder("Current combat bar bindings:\n\n");
        for (int i = 0; i < slots; i++) {
            String id = bar.store().get(player.getUniqueId(), i);
            String name = id == null || id.isBlank()
                    ? "— empty"
                    : abilities.get(id).map(AbilityDefinition::displayName).orElse(id);
            body.append('[').append(firstKey + i).append("] ").append(name).append('\n');
        }
        body.append("\nUse /ability mode to switch to combat bar.");
        bedrock.sendSimpleForm(
                player,
                "Combat bar",
                body.toString(),
                r -> {
                    if (!r.cancelled() && r.buttonIndex() == 0) {
                        YapSched.entity(plugin, player, () -> open(player));
                    }
                },
                "Back");
    }
}
