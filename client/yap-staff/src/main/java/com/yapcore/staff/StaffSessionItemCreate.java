package com.yapcore.staff;

/** Item-create wizard UI state (template, abilities, enchants, potion knobs). */
public final class StaffSessionItemCreate {

    private String createDisplayName = "";
    private String createIdDraft = "";
    private String createTemplate = "sword";
    private final java.util.LinkedHashMap<String, String> createAbilities = new java.util.LinkedHashMap<>();
    private String createDamage = "8";
    private String createRange = "8";
    private String createCooldown = "5s";
    private String createGearAttack = "0";
    private boolean createGlow = false;
    private boolean createUnbreakable = false;
    private boolean createRainbow = false;
    private final java.util.LinkedHashMap<String, Integer> createEnchants = new java.util.LinkedHashMap<>();
    private final java.util.LinkedHashSet<String> createPotionEffects = new java.util.LinkedHashSet<>();
    private String createRadius = "4";
    private String createHealAmount = "6";
    private String createProjectileKind = "snowball";
    private String createBreakRadius = "0";
    private String createBreakCount = "1";
    private String createPotionDurationSec = "10";
    private String createPotionAmplifier = "0";
    private boolean createFxEnabled = true;
    private String createFxSound = "DEFAULT";
    private String createFxParticle = "DEFAULT";
    private String createFxCount = "DEFAULT";
    private boolean createShowAllAbilities;

    {
        createAbilities.put("lightning_dash", "right_click");
        createPotionEffects.add("SPEED");
    }

    public String createDisplayName() {
        return createDisplayName == null ? "" : createDisplayName;
    }

    public void setCreateDisplayName(String createDisplayName) {
        this.createDisplayName = createDisplayName == null ? "" : createDisplayName;
    }

    public String createIdDraft() {
        return createIdDraft == null ? "" : createIdDraft.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public void setCreateIdDraft(String createIdDraft) {
        this.createIdDraft = createIdDraft == null ? "" : createIdDraft.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public String createTemplate() {
        return createTemplate == null || createTemplate.isBlank() ? "sword" : createTemplate;
    }

    public void setCreateTemplate(String createTemplate) {
        this.createTemplate = createTemplate == null || createTemplate.isBlank() ? "sword" : createTemplate.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public String createAbility() {
        return createAbilities.isEmpty() ? "none" : createAbilities.keySet().iterator().next();
    }

    public String createAbilitiesLabel() {
        if (createAbilities.isEmpty()) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        for (var e : createAbilities.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(e.getKey()).append('[').append(triggerShort(e.getValue())).append(']');
        }
        return sb.toString();
    }

    public String createAbilitiesPrettyLabel() {
        if (createAbilities.isEmpty()) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        for (var e : createAbilities.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(AbilityCatalog.label(e.getKey())).append('[').append(triggerShort(e.getValue())).append(']');
        }
        return sb.toString();
    }

    public java.util.List<String> createAbilities() {
        return java.util.List.copyOf(createAbilities.keySet());
    }

    public java.util.Map<String, String> createAbilityTriggers() {
        return java.util.Map.copyOf(createAbilities);
    }

    public void setCreateAbility(String createAbility) {
        createAbilities.clear();
        if (createAbility != null && !createAbility.isBlank() && !"none".equalsIgnoreCase(createAbility)) {
            createAbilities.put(createAbility.trim().toLowerCase(java.util.Locale.ROOT), "right_click");
        }
    }

    public void toggleCreateAbility(String ability) {
        if (ability == null || ability.isBlank() || "none".equalsIgnoreCase(ability)) {
            createAbilities.clear();
            return;
        }
        String a = ability.trim().toLowerCase(java.util.Locale.ROOT);
        if (createAbilities.containsKey(a)) {
            createAbilities.remove(a);
            return;
        }
        createAbilities.put(a, createAbilities.isEmpty() ? "right_click" : "together");
    }

    public boolean hasCreateAbility(String ability) {
        return ability != null && createAbilities.containsKey(ability.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public void cycleCreateAbilityTrigger(String ability) {
        if (ability == null || !createAbilities.containsKey(ability)) {
            return;
        }
        String[] cycle = {
                "together", "right_click", "sneak_right_click", "left_click",
                "sneak_left_click", "attack", "drop", "swap_hands"
        };
        String cur = createAbilities.get(ability);
        int idx = 0;
        for (int i = 0; i < cycle.length; i++) {
            if (cycle[i].equals(cur)) {
                idx = i;
                break;
            }
        }
        createAbilities.put(ability, cycle[(idx + 1) % cycle.length]);
    }

    public String createAbilityTrigger(String ability) {
        return createAbilities.getOrDefault(ability, "together");
    }

    private static String triggerShort(String trigger) {
        return switch (trigger == null ? "" : trigger) {
            case "together" -> "Together";
            case "left_click" -> "LMB";
            case "sneak_right_click" -> "Shift+RMB";
            case "sneak_left_click" -> "Shift+LMB";
            case "attack" -> "Hit";
            case "drop" -> "Q";
            case "swap_hands" -> "F";
            default -> "RMB";
        };
    }

    public String createDamage() {
        return createDamage == null || createDamage.isBlank() ? "8" : createDamage;
    }

    public void setCreateDamage(String createDamage) {
        this.createDamage = createDamage == null || createDamage.isBlank() ? "8" : createDamage.trim();
    }

    public String createRange() {
        return createRange == null || createRange.isBlank() ? "8" : createRange;
    }

    public void setCreateRange(String createRange) {
        this.createRange = createRange == null || createRange.isBlank() ? "8" : createRange.trim();
    }

    public String createCooldown() {
        return createCooldown == null || createCooldown.isBlank() ? "5s" : createCooldown;
    }

    public void setCreateCooldown(String createCooldown) {
        this.createCooldown = createCooldown == null || createCooldown.isBlank() ? "5s" : createCooldown.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public String createGearAttack() {
        return createGearAttack == null || createGearAttack.isBlank() ? "0" : createGearAttack;
    }

    public void setCreateGearAttack(String createGearAttack) {
        this.createGearAttack = createGearAttack == null || createGearAttack.isBlank() ? "0" : createGearAttack.trim();
    }

    public boolean createGlow() {
        return createGlow;
    }

    public void setCreateGlow(boolean createGlow) {
        this.createGlow = createGlow;
    }

    public void toggleCreateGlow() {
        this.createGlow = !this.createGlow;
    }

    public boolean createUnbreakable() {
        return createUnbreakable;
    }

    public void setCreateUnbreakable(boolean createUnbreakable) {
        this.createUnbreakable = createUnbreakable;
    }

    public void toggleCreateUnbreakable() {
        this.createUnbreakable = !this.createUnbreakable;
    }

    public boolean createRainbow() {
        return createRainbow;
    }

    public void setCreateRainbow(boolean createRainbow) {
        this.createRainbow = createRainbow;
    }

    public void toggleCreateRainbow() {
        this.createRainbow = !this.createRainbow;
    }

    public java.util.Map<String, Integer> createEnchants() {
        return java.util.Collections.unmodifiableMap(createEnchants);
    }

    public int createEnchantLevel(String id) {
        if (id == null) {
            return 0;
        }
        return createEnchants.getOrDefault(id.toLowerCase(java.util.Locale.ROOT), 0);
    }

    /** Cycle off → 1 → … → max → off. */
    public void cycleCreateEnchant(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        String key = id.toLowerCase(java.util.Locale.ROOT);
        EnchantCatalog.Info info = EnchantCatalog.get(key);
        int max = info == null ? 5 : info.maxLevel();
        int cur = createEnchants.getOrDefault(key, 0);
        int next = cur >= max ? 0 : cur + 1;
        if (next <= 0) {
            createEnchants.remove(key);
        } else {
            createEnchants.put(key, next);
        }
    }

    public void clearCreateEnchants() {
        createEnchants.clear();
    }

    public String createEnchantsCompact() {
        if (createEnchants.isEmpty()) {
            return "";
        }
        java.util.StringJoiner j = new java.util.StringJoiner(",");
        for (var e : createEnchants.entrySet()) {
            j.add(e.getKey() + ":" + e.getValue());
        }
        return j.toString();
    }

    public String createEnchantsPrettyLabel() {
        if (createEnchants.isEmpty()) {
            return "none";
        }
        java.util.StringJoiner j = new java.util.StringJoiner(", ");
        for (var e : createEnchants.entrySet()) {
            j.add(EnchantCatalog.labelWithLevel(e.getKey(), e.getValue()));
        }
        return j.toString();
    }

    public String createPotionEffect() {
        return createPotionEffects.isEmpty() ? "SPEED" : createPotionEffects.iterator().next();
    }

    public String createPotionEffectsCompact() {
        return createPotionEffects.isEmpty() ? "SPEED" : String.join(",", createPotionEffects);
    }

    public String createPotionEffectsLabel() {
        return createPotionEffects.isEmpty() ? "SPEED" : String.join(" + ", createPotionEffects);
    }

    public boolean hasCreatePotionEffect(String id) {
        return id != null && createPotionEffects.contains(id.trim().toUpperCase(java.util.Locale.ROOT));
    }

    public void setCreatePotionEffect(String createPotionEffect) {
        createPotionEffects.clear();
        createPotionEffects.add(createPotionEffect == null || createPotionEffect.isBlank()
                ? "SPEED"
                : createPotionEffect.trim().toUpperCase(java.util.Locale.ROOT));
    }

    /** Toggle a potion in the multi-select set (keeps at least one). */
    public void toggleCreatePotionEffect(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        String key = id.trim().toUpperCase(java.util.Locale.ROOT);
        if (createPotionEffects.contains(key)) {
            if (createPotionEffects.size() <= 1) {
                return;
            }
            createPotionEffects.remove(key);
        } else if (createPotionEffects.size() < 6) {
            createPotionEffects.add(key);
        }
    }

    public void cycleCreatePotionEffect() {
        String[] opts = {
                "SPEED", "STRENGTH", "REGENERATION", "RESISTANCE", "JUMP_BOOST",
                "INVISIBILITY", "FIRE_RESISTANCE", "HASTE", "NIGHT_VISION",
                "SLOWNESS", "WEAKNESS", "POISON", "WITHER", "BLINDNESS", "NAUSEA", "LEVITATION"
        };
        for (String p : opts) {
            if (!createPotionEffects.contains(p)) {
                if (createPotionEffects.size() < 6) {
                    createPotionEffects.add(p);
                }
                return;
            }
        }
        String first = createPotionEffect();
        int idx = 0;
        for (int i = 0; i < opts.length; i++) {
            if (opts[i].equals(first)) {
                idx = i;
                break;
            }
        }
        createPotionEffects.clear();
        createPotionEffects.add(opts[(idx + 1) % opts.length]);
    }

    public String createRadius() {
        return createRadius == null || createRadius.isBlank() ? "4" : createRadius;
    }

    public void setCreateRadius(String createRadius) {
        this.createRadius = createRadius == null || createRadius.isBlank() ? "4" : createRadius.trim();
    }

    public String createHealAmount() {
        return createHealAmount == null || createHealAmount.isBlank() ? "6" : createHealAmount;
    }

    public void setCreateHealAmount(String createHealAmount) {
        this.createHealAmount = createHealAmount == null || createHealAmount.isBlank() ? "6" : createHealAmount.trim();
    }

    public String createProjectileKind() {
        return createProjectileKind == null || createProjectileKind.isBlank() ? "snowball" : createProjectileKind;
    }

    public void setCreateProjectileKind(String createProjectileKind) {
        this.createProjectileKind = createProjectileKind == null || createProjectileKind.isBlank()
                ? "snowball"
                : createProjectileKind.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public void cycleCreateProjectileKind() {
        String[] opts = {"snowball", "arrow", "egg", "ender_pearl", "fireball"};
        String cur = createProjectileKind();
        int idx = 0;
        for (int i = 0; i < opts.length; i++) {
            if (opts[i].equals(cur)) {
                idx = i;
                break;
            }
        }
        createProjectileKind = opts[(idx + 1) % opts.length];
    }

    public String createBreakRadius() {
        return createBreakRadius == null || createBreakRadius.isBlank() ? "0" : createBreakRadius;
    }

    public void setCreateBreakRadius(String createBreakRadius) {
        this.createBreakRadius = createBreakRadius == null || createBreakRadius.isBlank() ? "0" : createBreakRadius.trim();
    }

    public String createBreakCount() {
        return createBreakCount == null || createBreakCount.isBlank() ? "1" : createBreakCount;
    }

    public void setCreateBreakCount(String createBreakCount) {
        this.createBreakCount = createBreakCount == null || createBreakCount.isBlank() ? "1" : createBreakCount.trim();
    }

    public String createPotionDurationSec() {
        return createPotionDurationSec == null || createPotionDurationSec.isBlank() ? "10" : createPotionDurationSec;
    }

    public void setCreatePotionDurationSec(String createPotionDurationSec) {
        String raw = createPotionDurationSec == null || createPotionDurationSec.isBlank()
                ? "10"
                : createPotionDurationSec.trim();
        if ("unlimited".equalsIgnoreCase(raw) || "infinite".equalsIgnoreCase(raw) || "inf".equalsIgnoreCase(raw)) {
            this.createPotionDurationSec = "unlimited";
            return;
        }
        this.createPotionDurationSec = raw;
    }

    public String createPotionAmplifier() {
        return createPotionAmplifier == null || createPotionAmplifier.isBlank() ? "0" : createPotionAmplifier;
    }

    public void setCreatePotionAmplifier(String createPotionAmplifier) {
        this.createPotionAmplifier = createPotionAmplifier == null || createPotionAmplifier.isBlank()
                ? "0"
                : createPotionAmplifier.trim();
    }

    /** Potion duration in Minecraft ticks for --duration (-1 = unlimited). */
    public String createPotionDurationTicks() {
        if ("unlimited".equalsIgnoreCase(createPotionDurationSec())) {
            return "-1";
        }
        try {
            int sec = Integer.parseInt(createPotionDurationSec());
            return Integer.toString(Math.max(1, sec) * 20);
        } catch (NumberFormatException e) {
            return "200";
        }
    }

    public boolean createFxEnabled() {
        return createFxEnabled;
    }

    public void setCreateFxEnabled(boolean createFxEnabled) {
        this.createFxEnabled = createFxEnabled;
    }

    public void toggleCreateFxEnabled() {
        createFxEnabled = !createFxEnabled;
    }

    public String createFxSound() {
        return createFxSound == null || createFxSound.isBlank() ? "DEFAULT" : createFxSound;
    }

    public void setCreateFxSound(String createFxSound) {
        this.createFxSound = createFxSound == null || createFxSound.isBlank() ? "DEFAULT" : createFxSound.trim();
    }

    public String createFxParticle() {
        return createFxParticle == null || createFxParticle.isBlank() ? "DEFAULT" : createFxParticle;
    }

    public void setCreateFxParticle(String createFxParticle) {
        this.createFxParticle = createFxParticle == null || createFxParticle.isBlank()
                ? "DEFAULT"
                : createFxParticle.trim();
    }

    public String createFxCount() {
        return createFxCount == null || createFxCount.isBlank() ? "DEFAULT" : createFxCount;
    }

    public void setCreateFxCount(String createFxCount) {
        this.createFxCount = createFxCount == null || createFxCount.isBlank() ? "DEFAULT" : createFxCount.trim();
    }

    public boolean createShowAllAbilities() {
        return createShowAllAbilities;
    }

    public void setCreateShowAllAbilities(boolean createShowAllAbilities) {
        this.createShowAllAbilities = createShowAllAbilities;
    }

    public void toggleCreateShowAllAbilities() {
        createShowAllAbilities = !createShowAllAbilities;
    }

    public String createItemGroup() {
        var entry = ItemTemplateCatalog.get(createTemplate());
        return entry == null ? "weapon" : entry.group();
    }
}
