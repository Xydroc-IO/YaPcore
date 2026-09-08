package com.yapcore.staff;

/** Client-side staff UI state (target player, give amount, browse page, ranks editor). */
public final class StaffSession {

    private String targetName;
    private int giveAmount = 1;
    private int givePage;
    private String giveFilter = "";
    private int mobAmount = 1;
    private int mobPage;
    private String mobFilter = "";
    private String wePattern = "stone";
    private String weSchemName = "";
    private String weWorldName = "world";
    private int weBrushRadius = 5;
    private int playerPage;
    private String playerFilter = "";

    private int permPage;
    private String permFilter = "";
    private String permCategory = "all";
    private int permModeIndex; // 0 allow, 1 deny, 2 unset
    private int permDurationIndex;
    private int rankAssignMode; // 0 set primary, 1 add parent, 2 remove parent
    private int createWeight = 25;
    private String customGroup = "";
    private String customNode = "";
    private final java.util.LinkedHashSet<String> customItemIds = new java.util.LinkedHashSet<>();
    private String itemsIdDraft = "";
    private String itemsCooldown = "8s";
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
    private String createPotionEffect = "SPEED";
    private String createRadius = "4";
    private String createHealAmount = "6";
    private String createProjectileKind = "snowball";
    private String createBreakRadius = "0";
    private String createBreakCount = "1";
    private String createPotionDurationSec = "10";
    private String createPotionAmplifier = "0";
    private boolean createShowAllAbilities;

    {
        createAbilities.put("lightning_dash", "right_click");
    }

    public String targetName() {
        return targetName;
    }

    public void setTarget(String name) {
        this.targetName = name == null || name.isBlank() ? null : name;
    }

    public void clearTarget() {
        this.targetName = null;
    }

    public boolean hasTarget() {
        return targetName != null && !targetName.isBlank();
    }

    public int giveAmount() {
        return giveAmount;
    }

    public void cycleGiveAmount() {
        giveAmount = switch (giveAmount) {
            case 1 -> 16;
            case 16 -> 64;
            default -> 1;
        };
    }

    public int givePage() {
        return givePage;
    }

    public void setGivePage(int page) {
        this.givePage = Math.max(0, page);
    }

    public String giveFilter() {
        return giveFilter;
    }

    public void setGiveFilter(String giveFilter) {
        this.giveFilter = giveFilter == null ? "" : giveFilter;
        this.givePage = 0;
    }

    public int mobAmount() {
        return mobAmount;
    }

    public void cycleMobAmount() {
        mobAmount = switch (mobAmount) {
            case 1 -> 5;
            case 5 -> 10;
            case 10 -> 25;
            default -> 1;
        };
    }

    public int mobPage() {
        return mobPage;
    }

    public void setMobPage(int page) {
        this.mobPage = Math.max(0, page);
    }

    public String mobFilter() {
        return mobFilter;
    }

    public void setMobFilter(String mobFilter) {
        this.mobFilter = mobFilter == null ? "" : mobFilter;
        this.mobPage = 0;
    }

    public String wePattern() {
        return wePattern == null || wePattern.isBlank() ? "stone" : wePattern;
    }

    public void setWePattern(String wePattern) {
        this.wePattern = wePattern == null || wePattern.isBlank() ? "stone" : wePattern.trim();
    }

    public String weSchemName() {
        return weSchemName == null ? "" : weSchemName;
    }

    public void setWeSchemName(String weSchemName) {
        this.weSchemName = weSchemName == null ? "" : weSchemName.trim();
    }

    public String weWorldName() {
        return weWorldName == null || weWorldName.isBlank() ? "world" : weWorldName;
    }

    public void setWeWorldName(String weWorldName) {
        this.weWorldName = weWorldName == null || weWorldName.isBlank() ? "world" : weWorldName.trim();
    }

    public int weBrushRadius() {
        return weBrushRadius;
    }

    public void cycleWeBrushRadius() {
        weBrushRadius = switch (weBrushRadius) {
            case 3 -> 5;
            case 5 -> 8;
            case 8 -> 12;
            case 12 -> 16;
            default -> 3;
        };
    }

    public int playerPage() {
        return playerPage;
    }

    public void setPlayerPage(int page) {
        this.playerPage = Math.max(0, page);
    }

    public String playerFilter() {
        return playerFilter;
    }

    public void setPlayerFilter(String playerFilter) {
        this.playerFilter = playerFilter == null ? "" : playerFilter;
        this.playerPage = 0;
    }

    public int permPage() {
        return permPage;
    }

    public void setPermPage(int page) {
        this.permPage = Math.max(0, page);
    }

    public String permFilter() {
        return permFilter;
    }

    public void setPermFilter(String permFilter) {
        this.permFilter = permFilter == null ? "" : permFilter;
        this.permPage = 0;
    }

    public String permCategory() {
        return permCategory;
    }

    public void setPermCategory(String permCategory) {
        this.permCategory = permCategory == null || permCategory.isBlank() ? "all" : permCategory;
        this.permPage = 0;
    }

    public int permModeIndex() {
        return permModeIndex;
    }

    public void cyclePermMode() {
        permModeIndex = (permModeIndex + 1) % 3;
    }

    public int permDurationIndex() {
        return permDurationIndex;
    }

    public void cyclePermDuration() {
        permDurationIndex = (permDurationIndex + 1) % com.yapcore.staff.ranks.PermCmds.DURATIONS.length;
    }

    public int rankAssignMode() {
        return rankAssignMode;
    }

    public void cycleRankAssignMode() {
        rankAssignMode = (rankAssignMode + 1) % 3;
    }

    public String rankAssignLabel() {
        return switch (rankAssignMode) {
            case 1 -> "Add parent";
            case 2 -> "Remove parent";
            default -> "Set primary";
        };
    }

    public int createWeight() {
        return createWeight;
    }

    public void cycleCreateWeight() {
        createWeight = switch (createWeight) {
            case 0 -> 10;
            case 10 -> 25;
            case 25 -> 50;
            case 50 -> 100;
            case 100 -> 200;
            default -> 0;
        };
    }

    public String customGroup() {
        return customGroup;
    }

    public void setCustomGroup(String customGroup) {
        this.customGroup = customGroup == null ? "" : customGroup.trim().toLowerCase();
    }

    public String customNode() {
        return customNode;
    }

    public void setCustomNode(String customNode) {
        this.customNode = customNode == null ? "" : customNode.trim();
    }

    public void rememberCustomItem(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        String clean = id.trim().toLowerCase(java.util.Locale.ROOT);
        customItemIds.add(clean);
        YapStaffClient.config().rememberItemId(clean);
    }

    public void forgetCustomItem(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        String clean = id.trim().toLowerCase(java.util.Locale.ROOT);
        customItemIds.remove(clean);
        YapStaffClient.config().forgetItemId(clean);
    }

    /** Load persisted ids from config into the live session set. */
    public void loadRememberedFromConfig() {
        var cfg = YapStaffClient.config();
        if (cfg.customItemIds != null) {
            customItemIds.addAll(cfg.customItemIds);
        }
    }

    public void replaceRememberedCustomItems(java.util.Collection<String> ids) {
        customItemIds.clear();
        if (ids != null) {
            for (String id : ids) {
                if (id != null && !id.isBlank()) {
                    customItemIds.add(id.trim().toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
        YapStaffClient.config().customItemIds = new java.util.LinkedHashSet<>(customItemIds);
        YapStaffClient.config().save();
    }

    public java.util.List<String> rememberedCustomItems() {
        return java.util.List.copyOf(customItemIds);
    }

    public String itemsIdDraft() {
        return itemsIdDraft;
    }

    public void setItemsIdDraft(String itemsIdDraft) {
        this.itemsIdDraft = itemsIdDraft == null ? "" : itemsIdDraft.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public String itemsCooldown() {
        return itemsCooldown == null || itemsCooldown.isBlank() ? "8s" : itemsCooldown;
    }

    public void setItemsCooldown(String itemsCooldown) {
        this.itemsCooldown = itemsCooldown == null || itemsCooldown.isBlank() ? "8s" : itemsCooldown.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public void cycleItemsCooldown() {
        String[] presets = {"0s", "1s", "2s", "3s", "5s", "8s", "10s", "12s", "15s", "20s", "30s", "60s"};
        int idx = 0;
        for (int i = 0; i < presets.length; i++) {
            if (presets[i].equalsIgnoreCase(itemsCooldown())) {
                idx = i;
                break;
            }
        }
        setItemsCooldown(presets[(idx + 1) % presets.length]);
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

    public String createPotionEffect() {
        return createPotionEffect == null || createPotionEffect.isBlank() ? "SPEED" : createPotionEffect;
    }

    public void setCreatePotionEffect(String createPotionEffect) {
        this.createPotionEffect = createPotionEffect == null || createPotionEffect.isBlank()
                ? "SPEED"
                : createPotionEffect.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public void cycleCreatePotionEffect() {
        String[] opts = {
                "SPEED", "STRENGTH", "REGENERATION", "RESISTANCE", "JUMP_BOOST",
                "INVISIBILITY", "FIRE_RESISTANCE", "HASTE", "NIGHT_VISION",
                "SLOWNESS", "WEAKNESS", "POISON", "WITHER", "BLINDNESS", "NAUSEA", "LEVITATION"
        };
        String cur = createPotionEffect();
        int idx = 0;
        for (int i = 0; i < opts.length; i++) {
            if (opts[i].equals(cur)) {
                idx = i;
                break;
            }
        }
        createPotionEffect = opts[(idx + 1) % opts.length];
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
        this.createPotionDurationSec = createPotionDurationSec == null || createPotionDurationSec.isBlank()
                ? "10"
                : createPotionDurationSec.trim();
    }

    public String createPotionAmplifier() {
        return createPotionAmplifier == null || createPotionAmplifier.isBlank() ? "0" : createPotionAmplifier;
    }

    public void setCreatePotionAmplifier(String createPotionAmplifier) {
        this.createPotionAmplifier = createPotionAmplifier == null || createPotionAmplifier.isBlank()
                ? "0"
                : createPotionAmplifier.trim();
    }

    /** Potion duration in Minecraft ticks for --duration. */
    public String createPotionDurationTicks() {
        try {
            int sec = Integer.parseInt(createPotionDurationSec());
            return Integer.toString(Math.max(1, sec) * 20);
        } catch (NumberFormatException e) {
            return "200";
        }
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
