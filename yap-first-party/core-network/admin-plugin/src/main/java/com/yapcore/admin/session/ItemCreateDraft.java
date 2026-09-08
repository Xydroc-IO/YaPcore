package com.yapcore.admin.session;

import com.yapcore.admin.gui.AbilityCatalog;
import com.yapcore.admin.gui.ItemTemplateCatalog;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** Draft state for the custom-item create wizard. */
public final class ItemCreateDraft {

    public static final class AbilitySlot {
        private String type;
        /** together | right_click | sneak_right_click | left_click | drop | swap_hands | attack */
        private String trigger;

        public AbilitySlot(String type, String trigger) {
            this.type = type == null ? "" : type.toLowerCase(Locale.ROOT);
            this.trigger = normalizeTrigger(trigger);
        }

        public String type() {
            return type;
        }

        public String trigger() {
            return trigger;
        }

        public void setTrigger(String trigger) {
            this.trigger = normalizeTrigger(trigger);
        }

        public String triggerLabel() {
            return switch (trigger) {
                case "together" -> "Together";
                case "left_click" -> "Left-click";
                case "sneak_right_click" -> "Sneak+RMB";
                case "sneak_left_click" -> "Sneak+LMB";
                case "attack" -> "Attack";
                case "drop" -> "Drop (Q)";
                case "swap_hands" -> "Swap (F)";
                case "consume" -> "Consume";
                default -> "Right-click";
            };
        }

        private static String normalizeTrigger(String raw) {
            if (raw == null || raw.isBlank()) {
                return "together";
            }
            String key = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
            return switch (key) {
                case "none", "same", "with_primary", "primary", "combo" -> "together";
                case "sneak_right", "sneak_rmb", "shift_right" -> "sneak_right_click";
                case "sneak_left", "sneak_lmb", "shift_left" -> "sneak_left_click";
                case "q" -> "drop";
                case "f", "swap", "offhand" -> "swap_hands";
                case "right_click", "left_click", "sneak_right_click", "sneak_left_click",
                     "attack", "drop", "swap_hands", "consume", "together" -> key;
                default -> "together";
            };
        }
    }

    private static final String[] TRIGGER_CYCLE = {
            "together", "right_click", "sneak_right_click", "left_click",
            "sneak_left_click", "attack", "drop", "swap_hands"
    };

    private String template = "sword";
    private String id = "";
    private String displayName = "";
    private final List<AbilitySlot> abilities = new ArrayList<>();
    private String cooldown = "5s";
    private double damage = 8;
    private double range = 8;
    private int gearAttack = 0;
    private int gearStrength = 0;
    private boolean furniture;
    private boolean glow;
    private boolean unbreakable;
    private String potionEffect = "SPEED";
    private double radius = 4;
    private double healAmount = 6;
    private String projectileKind = "snowball";
    private int breakRadius = 0;
    private int breakCount = 1;
    private int potionDurationSec = 10;
    private int potionAmplifier = 0;
    private boolean showAllAbilities;
    /** When true, buildCreateCommand includes --replace (edit existing custom item). */
    private boolean replaceExisting;
    /** 0=none, 1=display name, 2=id */
    private int pendingChat;
    /** Index into abilities for trigger-edit menu; -1 = none. */
    private int editingTriggerIndex = -1;

    public void resetForTemplate(String template) {
        this.template = template == null || template.isBlank() ? "sword" : template.toLowerCase(Locale.ROOT);
        ItemTemplateCatalog.Entry entry = ItemTemplateCatalog.get(this.template);
        this.furniture = entry != null ? entry.furniture()
                : this.template.equals("prop") || this.template.equals("furniture") || this.template.startsWith("prop_");
        abilities.clear();
        String defAbility = entry != null ? entry.defaultAbility() : switch (this.template) {
            case "tool", "pickaxe", "shovel", "hoe", "shears" -> "break_block";
            case "fishing_rod" -> "pull";
            case "gem", "charm", "amethyst", "emerald", "golden_apple" -> "heal";
            case "prop", "furniture" -> "";
            default -> this.template.startsWith("prop_") ? "" : "lightning_dash";
        };
        if (defAbility == null) {
            defAbility = "";
        }
        if (!defAbility.isBlank() && !"none".equalsIgnoreCase(defAbility)) {
            abilities.add(new AbilitySlot(defAbility, "right_click"));
        }
        this.damage = hasAbility("lightning_dash") || hasAbility("smite_target") || hasAbility("ground_slam") ? 8 : 4;
        this.range = 8;
        this.cooldown = "5s";
        this.gearAttack = 0;
        this.gearStrength = 0;
        this.glow = false;
        this.unbreakable = false;
        this.pendingChat = 0;
        this.editingTriggerIndex = -1;
        this.replaceExisting = false;
        if (id.isBlank()) {
            String stamp = Integer.toString((int) (System.currentTimeMillis() % 100000));
            String prefix = this.template.startsWith("prop_") ? "prop"
                    : this.template.replace('-', '_');
            this.id = prefix + "_" + stamp;
        }
        if (displayName.isBlank()) {
            this.displayName = "&f" + (entry != null ? entry.label() : id);
        }
    }

    public String template() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template == null ? "sword" : template.toLowerCase(Locale.ROOT);
    }

    public String id() {
        return id == null ? "" : id;
    }

    public void setId(String id) {
        String raw = id == null ? "" : id.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        this.id = raw.replaceAll("[^a-z0-9_]", "");
    }

    public String displayName() {
        return displayName == null || displayName.isBlank() ? "&f" + id() : displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName == null ? "" : displayName.trim();
    }

    public List<AbilitySlot> abilitySlots() {
        return List.copyOf(abilities);
    }

    public List<String> abilities() {
        List<String> out = new ArrayList<>();
        for (AbilitySlot slot : abilities) {
            out.add(slot.type());
        }
        return List.copyOf(out);
    }

    public String abilitiesLabel() {
        if (abilities.isEmpty()) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < abilities.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            AbilitySlot s = abilities.get(i);
            sb.append(labelFor(s.type())).append('[').append(s.triggerLabel()).append(']');
        }
        return sb.toString();
    }

    private static String labelFor(String type) {
        return AbilityCatalog.label(type);
    }

    public String ability() {
        return abilities.isEmpty() ? "" : abilities.getFirst().type();
    }

    public void setAbility(String ability) {
        abilities.clear();
        if (ability != null && !ability.isBlank() && !"none".equalsIgnoreCase(ability)) {
            abilities.add(new AbilitySlot(ability, "right_click"));
        }
    }

    public void toggleAbility(String ability) {
        if (ability == null || ability.isBlank() || "none".equalsIgnoreCase(ability)) {
            abilities.clear();
            return;
        }
        String a = ability.trim().toLowerCase(Locale.ROOT);
        for (Iterator<AbilitySlot> it = abilities.iterator(); it.hasNext(); ) {
            if (it.next().type().equals(a)) {
                it.remove();
                return;
            }
        }
        // First ability defaults to right-click; extras default to Together (same key).
        String trigger = abilities.isEmpty() ? "right_click" : "together";
        abilities.add(new AbilitySlot(a, trigger));
    }

    public boolean hasAbility(String ability) {
        if (ability == null) {
            return false;
        }
        String a = ability.trim().toLowerCase(Locale.ROOT);
        for (AbilitySlot slot : abilities) {
            if (slot.type().equals(a)) {
                return true;
            }
        }
        return false;
    }

    public int editingTriggerIndex() {
        return editingTriggerIndex;
    }

    public void setEditingTriggerIndex(int editingTriggerIndex) {
        this.editingTriggerIndex = editingTriggerIndex;
    }

    public void cycleTrigger(int index) {
        if (index < 0 || index >= abilities.size()) {
            return;
        }
        AbilitySlot slot = abilities.get(index);
        String cur = slot.trigger();
        int at = 0;
        for (int i = 0; i < TRIGGER_CYCLE.length; i++) {
            if (TRIGGER_CYCLE[i].equals(cur)) {
                at = i;
                break;
            }
        }
        slot.setTrigger(TRIGGER_CYCLE[(at + 1) % TRIGGER_CYCLE.length]);
    }

    public void setTrigger(int index, String trigger) {
        if (index < 0 || index >= abilities.size()) {
            return;
        }
        abilities.get(index).setTrigger(trigger);
    }

    public String cooldown() {
        return cooldown == null || cooldown.isBlank() ? "5s" : cooldown;
    }

    public void setCooldown(String cooldown) {
        this.cooldown = cooldown == null || cooldown.isBlank() ? "5s" : cooldown.trim().toLowerCase(Locale.ROOT);
    }

    public double damage() {
        return damage;
    }

    public void setDamage(double damage) {
        this.damage = damage < 0 ? -1 : Math.max(0, damage);
    }

    public void cycleDamage() {
        double[] presets = {0, 2, 4, 8, 12, 20, 40, 100, 200, -1};
        damage = next(presets, damage);
    }

    public String damageLabel() {
        return damage < 0 ? "INSTAKILL" : trimNum(damage);
    }

    public double range() {
        return range;
    }

    public void setRange(double range) {
        this.range = Math.max(1, range);
    }

    public void cycleRange() {
        double[] presets = {4, 6, 8, 12, 16, 24, 32, 48, 64, 80, 100};
        range = next(presets, range);
    }

    public void cycleCooldown() {
        String[] presets = {"0s", "1s", "2s", "3s", "5s", "8s", "10s", "12s", "15s", "20s", "30s", "60s"};
        int idx = 0;
        for (int i = 0; i < presets.length; i++) {
            if (presets[i].equalsIgnoreCase(cooldown())) {
                idx = i;
                break;
            }
        }
        setCooldown(presets[(idx + 1) % presets.length]);
    }

    public int gearAttack() {
        return gearAttack;
    }

    public void setGearAttack(int gearAttack) {
        this.gearAttack = Math.max(0, gearAttack);
    }

    public void cycleGearAttack() {
        int[] presets = {0, 2, 5, 10, 20, 50, 100};
        gearAttack = nextInt(presets, gearAttack);
    }

    public int gearStrength() {
        return gearStrength;
    }

    public void setGearStrength(int gearStrength) {
        this.gearStrength = Math.max(0, gearStrength);
    }

    public void cycleGearStrength() {
        int[] presets = {0, 2, 5, 10, 20, 50};
        gearStrength = nextInt(presets, gearStrength);
    }

    public String potionEffect() {
        return potionEffect == null || potionEffect.isBlank() ? "SPEED" : potionEffect;
    }

    public void setPotionEffect(String potionEffect) {
        this.potionEffect = potionEffect == null || potionEffect.isBlank()
                ? "SPEED"
                : potionEffect.trim().toUpperCase(Locale.ROOT);
    }

    public void cyclePotionEffect() {
        String[] presets = {
                "SPEED", "STRENGTH", "REGENERATION", "RESISTANCE", "JUMP_BOOST",
                "INVISIBILITY", "FIRE_RESISTANCE", "HASTE", "NIGHT_VISION",
                "SLOWNESS", "WEAKNESS", "POISON", "WITHER", "BLINDNESS", "NAUSEA", "LEVITATION"
        };
        int idx = 0;
        for (int i = 0; i < presets.length; i++) {
            if (presets[i].equalsIgnoreCase(potionEffect())) {
                idx = i;
                break;
            }
        }
        potionEffect = presets[(idx + 1) % presets.length];
    }

    public double radius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = Math.max(1, radius);
    }

    public void cycleRadius() {
        double[] presets = {2, 3, 4, 5, 6, 8, 12};
        radius = next(presets, radius);
    }

    public double healAmount() {
        return healAmount;
    }

    public void setHealAmount(double healAmount) {
        this.healAmount = Math.max(1, healAmount);
    }

    public void cycleHealAmount() {
        double[] presets = {2, 4, 6, 8, 12, 20};
        healAmount = next(presets, healAmount);
    }

    public String projectileKind() {
        return projectileKind == null || projectileKind.isBlank() ? "snowball" : projectileKind;
    }

    public void setProjectileKind(String projectileKind) {
        this.projectileKind = projectileKind == null || projectileKind.isBlank()
                ? "snowball"
                : projectileKind.trim().toLowerCase(Locale.ROOT);
    }

    public void cycleProjectileKind() {
        String[] presets = {"snowball", "arrow", "egg", "ender_pearl", "fireball"};
        int idx = 0;
        for (int i = 0; i < presets.length; i++) {
            if (presets[i].equalsIgnoreCase(projectileKind())) {
                idx = i;
                break;
            }
        }
        projectileKind = presets[(idx + 1) % presets.length];
    }

    public int breakRadius() {
        return breakRadius;
    }

    public void cycleBreakRadius() {
        int[] presets = {0, 1, 2, 3};
        breakRadius = nextInt(presets, breakRadius);
    }

    public int breakCount() {
        return breakCount;
    }

    public void cycleBreakCount() {
        int[] presets = {1, 3, 9, 18, 27};
        breakCount = nextInt(presets, breakCount);
    }

    public int potionDurationSec() {
        return Math.max(1, potionDurationSec);
    }

    public void cyclePotionDurationSec() {
        int[] presets = {5, 10, 20, 30, 60};
        potionDurationSec = nextInt(presets, potionDurationSec);
    }

    public int potionAmplifier() {
        return Math.max(0, potionAmplifier);
    }

    public void cyclePotionAmplifier() {
        int[] presets = {0, 1, 2};
        potionAmplifier = nextInt(presets, potionAmplifier);
    }

    public boolean showAllAbilities() {
        return showAllAbilities;
    }

    public void toggleShowAllAbilities() {
        showAllAbilities = !showAllAbilities;
    }

    public String itemGroup() {
        ItemTemplateCatalog.Entry e = ItemTemplateCatalog.get(template());
        return e == null ? "weapon" : e.group();
    }

    public boolean furniture() {
        return furniture;
    }

    public void setFurniture(boolean furniture) {
        this.furniture = furniture;
    }

    public boolean glow() {
        return glow;
    }

    public void setGlow(boolean glow) {
        this.glow = glow;
    }

    public void toggleGlow() {
        this.glow = !this.glow;
    }

    public boolean unbreakable() {
        return unbreakable;
    }

    public void setUnbreakable(boolean unbreakable) {
        this.unbreakable = unbreakable;
    }

    public void toggleUnbreakable() {
        this.unbreakable = !this.unbreakable;
    }

    public boolean replaceExisting() {
        return replaceExisting;
    }

    public void setReplaceExisting(boolean replaceExisting) {
        this.replaceExisting = replaceExisting;
    }

    /**
     * Prefills this draft from {@code YaPItems/items/custom/<id>.yml} (edit mode).
     * @return false if the custom file is missing
     */
    public boolean loadFromCustomFile(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        String clean = itemId.trim().toLowerCase(Locale.ROOT);
        org.bukkit.plugin.Plugin items = org.bukkit.Bukkit.getPluginManager().getPlugin("YaPItems");
        if (items == null) {
            return false;
        }
        java.io.File file = new java.io.File(items.getDataFolder(), "items/custom/" + clean + ".yml");
        if (!file.isFile()) {
            return false;
        }
        org.bukkit.configuration.file.YamlConfiguration yaml =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        if (!yaml.isConfigurationSection(clean)) {
            return false;
        }
        setId(clean);
        setDisplayName(yaml.getString(clean + ".name", "&f" + clean));
        String baseMat = yaml.getString(clean + ".base", "NETHERITE_SWORD");
        setTemplate(guessTemplate(baseMat));
        furniture = yaml.getBoolean(clean + ".furniture.enabled", false)
                || "prop".equals(template) || "furniture".equals(template);
        gearAttack = yaml.getInt(clean + ".gear.attack", 0);
        gearStrength = yaml.getInt(clean + ".gear.strength", 0);
        glow = yaml.getBoolean(clean + ".glow", false);
        unbreakable = yaml.getBoolean(clean + ".unbreakable", false);
        abilities.clear();
        java.util.List<java.util.Map<?, ?>> list = yaml.getMapList(clean + ".abilities");
        if (list != null) {
            for (java.util.Map<?, ?> raw : list) {
                Object typeObj = raw.get("type");
                Object trigObj = raw.get("trigger");
                String type = typeObj == null ? "" : String.valueOf(typeObj).toLowerCase(Locale.ROOT);
                String trigger = trigObj == null ? "right_click" : String.valueOf(trigObj).toLowerCase(Locale.ROOT);
                if (!type.isBlank()) {
                    abilities.add(new AbilitySlot(type, trigger));
                }
                Object cd = raw.get("cooldown");
                if (cd != null && !String.valueOf(cd).isBlank()) {
                    cooldown = String.valueOf(cd);
                }
                Object paramsObj = raw.get("params");
                if (paramsObj instanceof java.util.Map<?, ?> map) {
                    applyParamMap(map, type);
                }
            }
        }
        if (abilities.isEmpty() && yaml.isConfigurationSection(clean + ".ability")) {
            String type = yaml.getString(clean + ".ability.type", "");
            String trigger = yaml.getString(clean + ".ability.trigger", "right_click");
            if (!type.isBlank()) {
                abilities.add(new AbilitySlot(type, trigger));
            }
            cooldown = yaml.getString(clean + ".ability.cooldown", cooldown());
            org.bukkit.configuration.ConfigurationSection sec = yaml.getConfigurationSection(clean + ".ability.params");
            if (sec != null) {
                applyParamMap(sec.getValues(false), type);
            }
        }
        replaceExisting = true;
        pendingChat = 0;
        editingTriggerIndex = -1;
        return true;
    }

    private void applyParamMap(java.util.Map<?, ?> map, String abilityType) {
        Object dmg = map.get("damage");
        if (dmg instanceof Number n) {
            damage = n.doubleValue();
        }
        Object rng = map.get("range");
        if (rng instanceof Number n) {
            range = n.doubleValue();
        }
        Object rad = map.get("radius");
        if (rad instanceof Number n) {
            if ("break_block".equals(abilityType)) {
                breakRadius = n.intValue();
            } else {
                radius = n.doubleValue();
            }
        }
        Object amt = map.get("amount");
        if (amt instanceof Number n) {
            if ("break_block".equals(abilityType)) {
                breakCount = Math.max(1, n.intValue());
            } else {
                healAmount = n.doubleValue();
            }
        }
        Object dur = map.get("duration");
        if (dur instanceof Number n) {
            potionDurationSec = Math.max(1, n.intValue() / 20);
        }
        Object amp = map.get("amplifier");
        if (amp instanceof Number n) {
            potionAmplifier = Math.max(0, n.intValue());
        }
        Object eff = map.get("effect");
        if (eff != null) {
            potionEffect = String.valueOf(eff).toUpperCase(Locale.ROOT);
        }
        Object proj = map.get("projectile");
        if (proj == null) {
            proj = map.get("kind");
        }
        if (proj != null) {
            projectileKind = String.valueOf(proj).toLowerCase(Locale.ROOT);
        }
    }

    private static String guessTemplate(String materialName) {
        String m = materialName == null ? "" : materialName.toUpperCase(Locale.ROOT);
        if (m.contains("SWORD")) {
            return "sword";
        }
        if (m.contains("AXE") && !m.contains("PICKAXE")) {
            return "axe";
        }
        if (m.contains("SPEAR")) {
            return "spear";
        }
        if (m.equals("MACE")) {
            return "mace";
        }
        if (m.equals("BOW")) {
            return "bow";
        }
        if (m.equals("CROSSBOW")) {
            return "crossbow";
        }
        if (m.equals("TRIDENT")) {
            return "trident";
        }
        if (m.equals("SHIELD")) {
            return "shield";
        }
        if (m.contains("BLAZE_ROD")) {
            return "wand";
        }
        if (m.contains("PICKAXE")) {
            return "pickaxe";
        }
        if (m.contains("SHOVEL")) {
            return "shovel";
        }
        if (m.contains("HOE")) {
            return "hoe";
        }
        if (m.equals("SHEARS")) {
            return "shears";
        }
        if (m.equals("FISHING_ROD")) {
            return "fishing_rod";
        }
        if (m.equals("AMETHYST_SHARD")) {
            return "amethyst";
        }
        if (m.equals("EMERALD")) {
            return "emerald";
        }
        if (m.equals("DIAMOND")) {
            return "diamond";
        }
        if (m.equals("NETHER_STAR")) {
            return "nether_star";
        }
        if (m.equals("ECHO_SHARD")) {
            return "echo_shard";
        }
        if (m.equals("TOTEM_OF_UNDYING")) {
            return "totem";
        }
        if (m.equals("GOLDEN_APPLE") || m.equals("ENCHANTED_GOLDEN_APPLE")) {
            return "golden_apple";
        }
        if (m.equals("HEART_OF_THE_SEA")) {
            return "heart_of_the_sea";
        }
        if (m.equals("PRISMARINE_SHARD")) {
            return "prismarine";
        }
        if (m.equals("OAK_PLANKS")) {
            return "prop_oak";
        }
        if (m.equals("STONE")) {
            return "prop_stone";
        }
        if (m.equals("CHEST")) {
            return "prop_chest";
        }
        if (m.equals("ENDER_CHEST")) {
            return "prop_ender_chest";
        }
        if (m.equals("LANTERN")) {
            return "prop_lantern";
        }
        if (m.equals("SOUL_LANTERN")) {
            return "prop_soul_lantern";
        }
        if (m.equals("BEACON")) {
            return "prop_beacon";
        }
        if (m.equals("FLOWER_POT")) {
            return "prop_pot";
        }
        if (m.equals("BELL")) {
            return "prop_bell";
        }
        if (m.equals("ARMOR_STAND")) {
            return "prop_armor_stand";
        }
        if (m.contains("TRIPWIRE")) {
            return "key";
        }
        if (m.equals("PAPER")) {
            return "prop_paper";
        }
        return "sword";
    }

    public int pendingChat() {
        return pendingChat;
    }

    public void setPendingChat(int pendingChat) {
        this.pendingChat = pendingChat;
    }

    public String buildCreateCommand() {
        StringBuilder sb = new StringBuilder();
        sb.append("yapitems create ").append(id())
                .append(" --template ").append(template())
                .append(" --name ").append(displayName())
                .append(" --cooldown ").append(cooldown());
        for (AbilitySlot slot : abilities) {
            sb.append(" --ability ").append(slot.type())
                    .append(" --trigger ").append(slot.trigger());
        }
        if (usesDamage()) {
            sb.append(" --damage ").append(trimNum(damage()));
        }
        if (usesRange()) {
            sb.append(" --range ").append(trimNum(range()));
        }
        if (usesRadius() && !usesBreakVolume()) {
            sb.append(" --radius ").append(trimNum(radius()));
        }
        if (usesBreakVolume()) {
            sb.append(" --radius ").append(breakRadius());
            sb.append(" --amount ").append(breakCount());
        }
        if (usesPotion()) {
            sb.append(" --effect ").append(potionEffect());
        }
        if (usesPotionPower()) {
            sb.append(" --duration ").append(potionDurationSec() * 20);
            sb.append(" --amplifier ").append(potionAmplifier());
        }
        if (usesHeal() && !usesBreakVolume()) {
            sb.append(" --amount ").append(trimNum(healAmount()));
        }
        if (usesProjectile()) {
            sb.append(" --projectile ").append(projectileKind());
        }
        if (gearAttack() > 0) {
            sb.append(" --gear-attack ").append(gearAttack());
        }
        if (gearStrength() > 0) {
            sb.append(" --gear-strength ").append(gearStrength());
        }
        if (glow()) {
            sb.append(" --glow");
        } else if (replaceExisting()) {
            sb.append(" --no-glow");
        }
        if (unbreakable()) {
            sb.append(" --unbreakable");
        } else if (replaceExisting()) {
            sb.append(" --no-unbreakable");
        }
        if (furniture()) {
            sb.append(" --furniture");
        }
        if (replaceExisting()) {
            sb.append(" --replace");
        }
        return sb.toString();
    }

    public boolean usesDamage() {
        return AbilityCatalog.anyNeeds(abilities(), AbilityCatalog.Info::needsDamage);
    }

    public boolean usesRange() {
        return AbilityCatalog.anyNeeds(abilities(), AbilityCatalog.Info::needsRange);
    }

    public boolean usesRadius() {
        return AbilityCatalog.anyNeeds(abilities(), AbilityCatalog.Info::needsRadius);
    }

    public boolean usesPotion() {
        return AbilityCatalog.anyNeeds(abilities(), AbilityCatalog.Info::needsPotion);
    }

    public boolean usesPotionPower() {
        return AbilityCatalog.anyNeeds(abilities(), AbilityCatalog.Info::needsPotionPower);
    }

    public boolean usesHeal() {
        return AbilityCatalog.anyNeeds(abilities(), AbilityCatalog.Info::needsHeal);
    }

    public boolean usesProjectile() {
        return AbilityCatalog.anyNeeds(abilities(), AbilityCatalog.Info::needsProjectile);
    }

    public boolean usesBreakVolume() {
        return AbilityCatalog.anyNeeds(abilities(), AbilityCatalog.Info::needsBreakVolume);
    }

    private static double next(double[] presets, double current) {
        int idx = 0;
        for (int i = 0; i < presets.length; i++) {
            if (Math.abs(presets[i] - current) < 0.001) {
                idx = i;
                break;
            }
        }
        return presets[(idx + 1) % presets.length];
    }

    private static int nextInt(int[] presets, int current) {
        int idx = 0;
        for (int i = 0; i < presets.length; i++) {
            if (presets[i] == current) {
                idx = i;
                break;
            }
        }
        return presets[(idx + 1) % presets.length];
    }

    private static String trimNum(double v) {
        if (Math.rint(v) == v) {
            return Integer.toString((int) v);
        }
        return Double.toString(v);
    }
}
