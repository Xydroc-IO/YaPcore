package com.yapcore.staff.screen;

import com.yapcore.staff.AbilityCatalog;
import com.yapcore.staff.ItemTemplateCatalog;
import com.yapcore.staff.StaffCmds;
import com.yapcore.staff.YapStaffClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Full custom-item builder: name, id, ability, damage, range, cooldown, gear. */
public final class ItemsCreateScreen extends StaffPanelScreen {

    private static final String[] DAMAGE = {"2", "4", "8", "12", "20", "40", "100", "200", "kill"};
    private static final String[] RANGE = {"4", "6", "8", "12", "16", "24", "32", "48", "64", "80", "100"};
    private static final String[] RADIUS = {"2", "3", "4", "5", "6", "8", "12"};
    private static final String[] HEAL = {"2", "4", "6", "8", "12", "20"};
    private static final String[] COOLDOWNS = {"0s", "1s", "3s", "5s", "8s", "12s", "20s", "30s"};
    private static final String[] GEAR = {"0", "2", "5", "10", "20", "50", "100"};
    private static final String[] POTIONS = {
            "SPEED", "STRENGTH", "REGENERATION", "RESISTANCE", "JUMP_BOOST",
            "INVISIBILITY", "FIRE_RESISTANCE", "HASTE", "NIGHT_VISION",
            "SLOWNESS", "WEAKNESS", "POISON", "WITHER", "BLINDNESS", "NAUSEA", "LEVITATION"
    };
    private static final String[] PROJECTILES = {"snowball", "arrow", "egg", "ender_pearl", "fireball"};

    private EditBox nameBox;
    private EditBox idBox;
    private final boolean editMode;

    public ItemsCreateScreen(Screen parent) {
        this(parent, false);
    }

    public ItemsCreateScreen(Screen parent, boolean editMode) {
        super(Component.literal(editMode ? "Edit custom item" : "Create custom item"), parent);
        this.editMode = editMode;
    }

    @Override
    protected void addContents() {
        var session = YapStaffClient.session();

        addSubtitle(editMode
                ? "Editing " + session.createIdDraft() + " — change options, then Save (overwrites YAML)."
                : "Name the item, pick abilities + options, then Create.");

        LinearLayout nameRow = LinearLayout.horizontal().spacing(6);
        nameBox = new EditBox(this.font, 220, 20, Component.literal("Display name"));
        nameBox.setValue(session.createDisplayName().isBlank() ? (editMode ? session.createIdDraft() : "God Killer") : session.createDisplayName());
        nameBox.setHint(Component.literal("Display name…"));
        nameBox.setMaxLength(64);
        nameBox.setResponder(text -> {
            session.setCreateDisplayName(text);
            if (!editMode && idBox != null && (session.createIdDraft().isBlank() || autoId(session.createDisplayName()).equals(session.createIdDraft()))) {
                String auto = autoId(text);
                session.setCreateIdDraft(auto);
                idBox.setValue(auto);
            }
        });
        nameRow.addChild(nameBox);
        addBody(nameRow);

        LinearLayout idRow = LinearLayout.horizontal().spacing(6);
        idBox = new EditBox(this.font, 220, 20, Component.literal("Id"));
        if (session.createIdDraft().isBlank() && !editMode) {
            session.setCreateIdDraft(autoId(nameBox.getValue()));
        }
        idBox.setValue(session.createIdDraft());
        idBox.setHint(Component.literal("id (a-z0-9_)…"));
        idBox.setMaxLength(40);
        idBox.setEditable(!editMode);
        if (!editMode) {
            idBox.setResponder(session::setCreateIdDraft);
        }
        idRow.addChild(idBox);
        addBody(idRow);

        addSection("Base: " + ItemTemplateCatalog.label(session.createTemplate()));
        for (String group : ItemTemplateCatalog.groupOrder()) {
            addSection(ItemTemplateCatalog.groupTitle(group));
            GridLayout templates = new GridLayout().columnSpacing(4).rowSpacing(3);
            GridLayout.RowHelper tRows = templates.createRowHelper(3);
            for (ItemTemplateCatalog.Entry e : ItemTemplateCatalog.byGroup(group)) {
                ItemTemplateCatalog.Entry entry = e;
                tRows.addChild(Button.builder(Component.literal(
                        entry.id().equals(session.createTemplate()) ? "▶ " + entry.label() : entry.label()
                ), b -> {
                    session.setCreateTemplate(entry.id());
                    applyTemplateDefaults(session, entry);
                    rebuildWidgets();
                }).width(Math.max(100, colWidth() - 4)).build());
            }
            addBody(templates);
        }

        String itemGroup = session.createItemGroup();
        addSection("Abilities for " + ItemTemplateCatalog.groupTitle(itemGroup)
                + "  ·  " + session.createAbilitiesPrettyLabel());
        addBody(Button.builder(Component.literal(
                session.createShowAllAbilities()
                        ? "Filter: showing ALL abilities (click to suit this base)"
                        : "Filter: suited to this base (click to show all)"
        ), b -> {
            session.toggleCreateShowAllAbilities();
            rebuildWidgets();
        }).width(Math.min(360, panelWidth() - 20)).build());

        addBody(Button.builder(Component.literal(
                session.createAbilities().isEmpty() ? "▶ No ability" : "Clear abilities"
        ), b -> {
            session.toggleCreateAbility("none");
            rebuildWidgets();
        }).width(Math.max(140, colWidth())).build());

        for (String cat : AbilityCatalog.categoryOrder()) {
            java.util.List<AbilityCatalog.Info> catAbilities = session.createShowAllAbilities()
                    ? AbilityCatalog.byCategory(cat, null)
                    : AbilityCatalog.byCategory(cat, itemGroup);
            if (catAbilities.isEmpty()) {
                continue;
            }
            addSection(AbilityCatalog.categoryTitle(cat));
            GridLayout abilities = new GridLayout().columnSpacing(4).rowSpacing(3);
            GridLayout.RowHelper aRows = abilities.createRowHelper(2);
            for (AbilityCatalog.Info info : catAbilities) {
                boolean sel = session.hasCreateAbility(info.id());
                aRows.addChild(Button.builder(Component.literal((sel ? "▶ " : "") + info.label()), b -> {
                    session.toggleCreateAbility(info.id());
                    rebuildWidgets();
                }).width(Math.max(140, colWidth())).build());
            }
            addBody(abilities);
        }

        if (!session.createAbilities().isEmpty()) {
            for (String a : session.createAbilities()) {
                String desc = AbilityCatalog.description(a);
                if (!desc.isBlank()) {
                    addBody(new StringWidget(Component.literal("· " + AbilityCatalog.label(a) + " — " + desc), this.font));
                }
            }
            addSection("Keybinds");
            GridLayout binds = new GridLayout().columnSpacing(4).rowSpacing(3);
            GridLayout.RowHelper bRows = binds.createRowHelper(2);
            for (String a : session.createAbilities()) {
                String ability = a;
                String label = AbilityCatalog.label(ability) + " → " + session.createAbilityTrigger(ability);
                bRows.addChild(Button.builder(Component.literal(label), b -> {
                    session.cycleCreateAbilityTrigger(ability);
                    rebuildWidgets();
                }).width(Math.max(140, colWidth())).build());
            }
            addBody(binds);
            addBody(new StringWidget(Component.literal(
                    "Together = same key as primary · RMB · Shift+RMB · LMB · Q · F · Attack"), this.font));
        }

        var selected = session.createAbilities();
        boolean needDmg = AbilityCatalog.anyNeeds(selected, AbilityCatalog.Info::needsDamage);
        boolean needRange = AbilityCatalog.anyNeeds(selected, AbilityCatalog.Info::needsRange);
        boolean needRadius = AbilityCatalog.anyNeeds(selected, AbilityCatalog.Info::needsRadius);
        boolean needPotion = AbilityCatalog.anyNeeds(selected, AbilityCatalog.Info::needsPotion);
        boolean needPotionPower = AbilityCatalog.anyNeeds(selected, AbilityCatalog.Info::needsPotionPower);
        boolean needProj = AbilityCatalog.anyNeeds(selected, AbilityCatalog.Info::needsProjectile);
        boolean needHeal = AbilityCatalog.anyNeeds(selected, AbilityCatalog.Info::needsHeal);
        boolean needBreak = AbilityCatalog.anyNeeds(selected, AbilityCatalog.Info::needsBreakVolume);
        boolean hasSelfPotion = selected.stream().anyMatch(a -> "effect".equals(a));
        boolean hasEnemyPotion = selected.stream().anyMatch(a -> "area_effect".equals(a));

        if (needDmg) {
            addSection("Combat damage  ·  " + damageLabel(session.createDamage()));
            addChipRow(DAMAGE, normalizeDamageChip(session.createDamage()), v -> {
                session.setCreateDamage("kill".equals(v) ? "kill" : v);
                rebuildWidgets();
            });
        }
        if (needRange && !needBreak) {
            addSection("Ability reach / range  ·  " + session.createRange() + " blocks");
            addChipRow(RANGE, session.createRange(), session::setCreateRange);
        }
        if (needBreak) {
            addSection("Break blocks — look reach  ·  " + session.createRange() + " blocks");
            addChipRow(RANGE, session.createRange(), session::setCreateRange);
            addSection("Break blocks — blast size (0 = single block)  ·  " + session.createBreakRadius());
            addChipRow(new String[]{"0", "1", "2", "3"}, session.createBreakRadius(), session::setCreateBreakRadius);
            addSection("Break blocks — max blocks broken  ·  " + session.createBreakCount());
            addChipRow(new String[]{"1", "3", "9", "18", "27"}, session.createBreakCount(), session::setCreateBreakCount);
        }
        if (needRadius) {
            addSection("Enemy / stomp AoE radius  ·  " + session.createRadius() + " blocks");
            addChipRow(RADIUS, session.createRadius(), session::setCreateRadius);
        }
        if (hasSelfPotion || hasEnemyPotion) {
            addSection((hasSelfPotion && !hasEnemyPotion)
                    ? "Self potion — which effect"
                    : (hasEnemyPotion && !hasSelfPotion)
                    ? "Enemy AoE potion — which effect"
                    : "Potion effect (self and/or enemy AoE)");
            addChipRow(POTIONS, session.createPotionEffect(), session::setCreatePotionEffect);
        } else if (needPotion) {
            addSection("Potion effect");
            addChipRow(POTIONS, session.createPotionEffect(), session::setCreatePotionEffect);
        }
        if (needPotionPower) {
            addSection("Potion duration  ·  " + session.createPotionDurationSec() + "s");
            addChipRow(new String[]{"5", "10", "20", "30", "60"}, session.createPotionDurationSec(), session::setCreatePotionDurationSec);
            addSection("Potion strength  ·  level " + (safeInt(session.createPotionAmplifier(), 0) + 1));
            addChipRow(new String[]{"0", "1", "2"}, session.createPotionAmplifier(), session::setCreatePotionAmplifier);
        }
        if (needProj) {
            addSection("Projectile type  ·  " + session.createProjectileKind());
            addChipRow(PROJECTILES, session.createProjectileKind(), session::setCreateProjectileKind);
        }
        if (needHeal) {
            addSection("Heal amount (HP)  ·  " + session.createHealAmount());
            addChipRow(HEAL, session.createHealAmount(), session::setCreateHealAmount);
        }

        addSection("Ability cooldown  ·  " + session.createCooldown());
        addChipRow(COOLDOWNS, session.createCooldown(), session::setCreateCooldown);

        addSection("Melee gear bonus  ·  +" + session.createGearAttack());
        addChipRow(GEAR, session.createGearAttack(), session::setCreateGearAttack);

        addSection("Item look");
        LinearLayout lookRow = LinearLayout.horizontal().spacing(6);
        lookRow.addChild(Button.builder(Component.literal(
                session.createGlow() ? "▶ Glow ON" : "Glow OFF"
        ), b -> {
            session.toggleCreateGlow();
            rebuildWidgets();
        }).width(Math.max(110, colWidth())).build());
        lookRow.addChild(Button.builder(Component.literal(
                session.createUnbreakable() ? "▶ Unbreakable ON" : "Unbreakable OFF"
        ), b -> {
            session.toggleCreateUnbreakable();
            rebuildWidgets();
        }).width(Math.max(140, colWidth())).build());
        addBody(lookRow);
        addBody(new StringWidget(Component.literal(
                "Glow = enchantment shine · Unbreakable = never loses durability"), this.font));

        addBody(new StringWidget(Component.literal(
                colorless(session.createDisplayName())
                        + " · " + session.createIdDraft()
                        + " · " + ItemTemplateCatalog.label(session.createTemplate())
                        + " · " + session.createAbilitiesPrettyLabel()), this.font));

        String amountFlag = needBreak ? session.createBreakCount() : (needHeal ? session.createHealAmount() : null);
        String radiusFlag = needBreak
                ? session.createBreakRadius()
                : (needRadius ? session.createRadius() : null);

        addBody(Button.builder(Component.literal(editMode ? "SAVE CHANGES" : "CREATE ITEM"), b -> {
            String id = session.createIdDraft();
            String name = session.createDisplayName();
            if (id.isBlank()) {
                id = autoId(name);
            }
            if (id.isBlank()) {
                return;
            }
            if (name.isBlank()) {
                name = "&f" + id;
            } else if (!name.contains("&")) {
                name = "&c&l" + name;
            }
            session.rememberCustomItem(id);
            if (parent != null) {
                open(parent);
            } else {
                closeToGame();
            }
            StaffCmds.customItemCreateFull(
                    id,
                    session.createTemplate(),
                    name,
                    session.createAbilityTriggers(),
                    needDmg ? session.createDamage() : null,
                    needRange || needBreak ? session.createRange() : null,
                    session.createCooldown(),
                    session.createGearAttack(),
                    needPotion ? session.createPotionEffect() : null,
                    radiusFlag,
                    amountFlag,
                    needProj ? session.createProjectileKind() : null,
                    needPotionPower ? session.createPotionDurationTicks() : null,
                    needPotionPower ? session.createPotionAmplifier() : null,
                    session.createGlow(),
                    session.createUnbreakable(),
                    editMode);
        }).width(Math.min(280, panelWidth() - 20)).build());
    }

    private void addChipRow(String[] values, String selected, java.util.function.Consumer<String> setter) {
        GridLayout grid = new GridLayout().columnSpacing(4).rowSpacing(3);
        GridLayout.RowHelper rows = grid.createRowHelper(Math.min(4, values.length));
        for (String v : values) {
            String value = v;
            rows.addChild(Button.builder(Component.literal(
                    value.equals(selected) ? "▶ " + value : value
            ), b -> {
                setter.accept(value);
                rebuildWidgets();
            }).width(Math.max(56, (colWidth() - 12) / 2)).build());
        }
        addBody(grid);
    }

    private static void applyTemplateDefaults(com.yapcore.staff.StaffSession session, ItemTemplateCatalog.Entry entry) {
        if (entry == null) {
            return;
        }
        String ability = entry.defaultAbility();
        if (ability == null || ability.isBlank() || "none".equalsIgnoreCase(ability)) {
            session.setCreateAbility("none");
        } else {
            session.setCreateAbility(ability);
        }
    }

    private static int safeInt(String raw, int def) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static String damageLabel(String raw) {
        if (raw == null) {
            return "8";
        }
        String v = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if ("kill".equals(v) || "instakill".equals(v) || "-1".equals(v)) {
            return "INSTAKILL";
        }
        return raw;
    }

    private static String normalizeDamageChip(String raw) {
        if (raw == null) {
            return "8";
        }
        String v = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if ("kill".equals(v) || "instakill".equals(v) || "-1".equals(v)) {
            return "kill";
        }
        return raw;
    }

    private static String autoId(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "";
        }
        String plain = displayName.replaceAll("(?i)&[0-9a-fk-or]", "").trim().toLowerCase(java.util.Locale.ROOT);
        plain = plain.replace(' ', '_').replaceAll("[^a-z0-9_]", "");
        return plain;
    }

    private static String colorless(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("(?i)&[0-9a-fk-or]", "");
    }
}
