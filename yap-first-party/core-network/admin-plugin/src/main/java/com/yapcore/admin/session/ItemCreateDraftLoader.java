package com.yapcore.admin.session;

import com.yapcore.admin.gui.ItemTemplateCatalog;

import java.util.Locale;

/** Load / apply helpers for {@link ItemCreateDraft} from YaPItems custom YAML. */
final class ItemCreateDraftLoader {

    private ItemCreateDraftLoader() {
    }

    static void resetForTemplate(ItemCreateDraft d, String template) {
        d.setTemplate(template == null || template.isBlank() ? "sword" : template.toLowerCase(Locale.ROOT));
        ItemTemplateCatalog.Entry entry = ItemTemplateCatalog.get(d.template());
        d.setFurniture(entry != null ? entry.furniture()
                : d.template().equals("prop") || d.template().equals("furniture") || d.template().startsWith("prop_"));
        d.abilitiesMutable().clear();
        String defAbility = entry != null ? entry.defaultAbility() : switch (d.template()) {
            case "tool", "pickaxe", "shovel", "hoe", "shears" -> "break_block";
            case "fishing_rod" -> "pull";
            case "gem", "charm", "amethyst", "emerald", "golden_apple" -> "heal";
            case "prop", "furniture" -> "";
            default -> d.template().startsWith("prop_") ? "" : "lightning_dash";
        };
        if (defAbility == null) {
            defAbility = "";
        }
        if (!defAbility.isBlank() && !"none".equalsIgnoreCase(defAbility)) {
            d.abilitiesMutable().add(new ItemCreateAbilitySlot(defAbility, "right_click"));
        }
        d.setDamage(d.hasAbility("lightning_dash") || d.hasAbility("smite_target") || d.hasAbility("ground_slam") ? 8 : 4);
        d.setRange(8);
        d.setCooldown("5s");
        d.setGearAttack(0);
        d.setGearStrength(0);
        d.setGlow(false);
        d.setUnbreakable(false);
        d.enchantsMutable().clear();
        d.clearPotionEffects();
        d.setPendingChat(0);
        d.setEditingTriggerIndex(-1);
        d.setReplaceExisting(false);
        if (d.id().isBlank()) {
            String stamp = Integer.toString((int) (System.currentTimeMillis() % 100000));
            String prefix = d.template().startsWith("prop_") ? "prop"
                    : d.template().replace('-', '_');
            d.setId(prefix + "_" + stamp);
        }
        d.applyResetDisplayName(entry != null ? entry.label() : d.id());
    }

    /**
     * Prefills {@code d} from {@code YaPItems/items/custom/<id>.yml} (edit mode).
     * @return false if the custom file is missing
     */
    static boolean loadFromCustomFile(ItemCreateDraft d, String itemId) {
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
        d.setId(clean);
        d.setDisplayName(yaml.getString(clean + ".name", "&f" + clean));
        String baseMat = yaml.getString(clean + ".base", "NETHERITE_SWORD");
        d.setTemplate(guessTemplate(baseMat));
        d.setFurniture(yaml.getBoolean(clean + ".furniture.enabled", false)
                || "prop".equals(d.template()) || "furniture".equals(d.template()));
        d.setGearAttack(yaml.getInt(clean + ".gear.attack", 0));
        d.setGearStrength(yaml.getInt(clean + ".gear.strength", 0));
        d.setGlow(yaml.getBoolean(clean + ".glow", false));
        d.setUnbreakable(yaml.getBoolean(clean + ".unbreakable", false));
        d.setRainbow(yaml.getBoolean(clean + ".rainbow", false)
                || yaml.getBoolean(clean + ".rainbow-name", false)
                || yaml.getBoolean(clean + ".name-rainbow", false));
        d.enchantsMutable().clear();
        org.bukkit.configuration.ConfigurationSection enchSec = yaml.getConfigurationSection(clean + ".enchants");
        if (enchSec != null) {
            for (String key : enchSec.getKeys(false)) {
                int lvl = enchSec.getInt(key, 0);
                if (lvl > 0) {
                    d.enchantsMutable().put(key.toLowerCase(Locale.ROOT), lvl);
                }
            }
        }
        d.abilitiesMutable().clear();
        java.util.List<java.util.Map<?, ?>> list = yaml.getMapList(clean + ".abilities");
        if (list != null) {
            for (java.util.Map<?, ?> raw : list) {
                Object typeObj = raw.get("type");
                Object trigObj = raw.get("trigger");
                String type = typeObj == null ? "" : String.valueOf(typeObj).toLowerCase(Locale.ROOT);
                String trigger = trigObj == null ? "right_click" : String.valueOf(trigObj).toLowerCase(Locale.ROOT);
                if (!type.isBlank()) {
                    d.abilitiesMutable().add(new ItemCreateAbilitySlot(type, trigger));
                }
                Object cd = raw.get("cooldown");
                if (cd != null && !String.valueOf(cd).isBlank()) {
                    d.setCooldown(String.valueOf(cd));
                }
                Object paramsObj = raw.get("params");
                if (paramsObj instanceof java.util.Map<?, ?> map) {
                    applyParamMap(d, map, type);
                }
            }
        }
        if (d.abilitiesMutable().isEmpty() && yaml.isConfigurationSection(clean + ".ability")) {
            String type = yaml.getString(clean + ".ability.type", "");
            String trigger = yaml.getString(clean + ".ability.trigger", "right_click");
            if (!type.isBlank()) {
                d.abilitiesMutable().add(new ItemCreateAbilitySlot(type, trigger));
            }
            d.setCooldown(yaml.getString(clean + ".ability.cooldown", d.cooldown()));
            org.bukkit.configuration.ConfigurationSection sec = yaml.getConfigurationSection(clean + ".ability.params");
            if (sec != null) {
                applyParamMap(d, sec.getValues(false), type);
            }
        }
        d.setReplaceExisting(true);
        d.setPendingChat(0);
        d.setEditingTriggerIndex(-1);
        return true;
    }

    static void applyParamMap(ItemCreateDraft d, java.util.Map<?, ?> map, String abilityType) {
        Object dmg = map.get("damage");
        if (dmg instanceof Number n) {
            d.setDamage(n.doubleValue());
        }
        Object rng = map.get("range");
        if (rng instanceof Number n) {
            d.setRange(n.doubleValue());
        }
        Object rad = map.get("radius");
        if (rad instanceof Number n) {
            if ("break_block".equals(abilityType)) {
                d.setBreakRadius(n.intValue());
            } else {
                d.setRadius(n.doubleValue());
            }
        }
        Object amt = map.get("amount");
        if (amt instanceof Number n) {
            if ("break_block".equals(abilityType)) {
                d.setBreakCount(Math.max(1, n.intValue()));
            } else {
                d.setHealAmount(n.doubleValue());
            }
        }
        Object dur = map.get("duration");
        if (dur instanceof Number n) {
            if (n.intValue() < 0) {
                d.setPotionDurationSec(-1);
            } else {
                d.setPotionDurationSec(Math.max(1, n.intValue() / 20));
            }
        }
        Object amp = map.get("amplifier");
        if (amp instanceof Number n) {
            d.setPotionAmplifier(Math.max(0, Math.min(99, n.intValue())));
        }
        Object sound = map.get("sound");
        if (sound != null && !String.valueOf(sound).isBlank()) {
            d.fx().sound = String.valueOf(sound).trim().toUpperCase(Locale.ROOT);
        }
        Object particle = map.get("particle");
        if (particle != null && !String.valueOf(particle).isBlank()) {
            d.fx().particle = String.valueOf(particle).trim().toUpperCase(Locale.ROOT);
        }
        Object count = map.get("count");
        if (count instanceof Number n) {
            d.fx().count = Math.max(0, n.intValue());
        }
        Object fxFlag = map.get("fx");
        if (fxFlag instanceof Boolean b) {
            d.fx().enabled = b;
        } else if (fxFlag != null) {
            d.fx().enabled = !"false".equalsIgnoreCase(String.valueOf(fxFlag))
                    && !"0".equals(String.valueOf(fxFlag))
                    && !"off".equalsIgnoreCase(String.valueOf(fxFlag));
        }
        Object eff = map.get("effect");
        Object effects = map.get("effects");
        if (effects instanceof java.util.List<?> list) {
            StringBuilder csv = new StringBuilder();
            for (Object o : list) {
                if (o == null) {
                    continue;
                }
                if (csv.length() > 0) {
                    csv.append(',');
                }
                csv.append(o);
            }
            if (csv.length() > 0) {
                ItemCreateDraftCycles.setPotionEffectsCsv(d, csv.toString());
            }
        } else if (eff != null) {
            ItemCreateDraftCycles.setPotionEffectsCsv(d, String.valueOf(eff));
        }
        for (int i = 2; i <= 6; i++) {
            Object extra = map.get("effect" + i);
            if (extra != null) {
                String key = String.valueOf(extra).trim().toUpperCase(Locale.ROOT).replace(' ', '_');
                if (!key.isEmpty()) {
                    d.potionEffectsMutable().add(key);
                }
            }
        }
        Object proj = map.get("projectile");
        if (proj == null) {
            proj = map.get("kind");
        }
        if (proj != null) {
            d.setProjectileKind(String.valueOf(proj).toLowerCase(Locale.ROOT));
        }
    }

    static String guessTemplate(String materialName) {
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
}
