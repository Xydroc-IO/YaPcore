package com.yapcore.staff;

/** Client-side staff UI state (target player, give amount, browse page, ranks editor). */
public final class StaffSession {

    private final StaffSessionWorldEdit worldEdit = new StaffSessionWorldEdit();
    private final StaffSessionRanks ranks = new StaffSessionRanks();
    private final StaffSessionItemCreate itemCreate = new StaffSessionItemCreate();

    private String targetName;
    private int giveAmount = 1;
    private int givePage;
    private String giveFilter = "";
    private int mobAmount = 1;
    private int mobPage;
    private String mobFilter = "";
    private int playerPage;
    private String playerFilter = "";

    private final java.util.LinkedHashSet<String> customItemIds = new java.util.LinkedHashSet<>();
    private String itemsIdDraft = "";
    private String itemsCooldown = "8s";

    public StaffSessionWorldEdit worldEdit() {
        return worldEdit;
    }

    public StaffSessionRanks ranks() {
        return ranks;
    }

    public StaffSessionItemCreate itemCreate() {
        return itemCreate;
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

    // --- WorldEdit (forwarded) ---
    public String wePattern() { return worldEdit.wePattern(); }
    public void setWePattern(String wePattern) { worldEdit.setWePattern(wePattern); }
    public String weSchemName() { return worldEdit.weSchemName(); }
    public void setWeSchemName(String weSchemName) { worldEdit.setWeSchemName(weSchemName); }
    public String weWorldName() { return worldEdit.weWorldName(); }
    public void setWeWorldName(String weWorldName) { worldEdit.setWeWorldName(weWorldName); }
    public int weBrushRadius() { return worldEdit.weBrushRadius(); }
    public void cycleWeBrushRadius() { worldEdit.cycleWeBrushRadius(); }

    // --- Ranks / perms (forwarded) ---
    public int permPage() { return ranks.permPage(); }
    public void setPermPage(int page) { ranks.setPermPage(page); }
    public String permFilter() { return ranks.permFilter(); }
    public void setPermFilter(String permFilter) { ranks.setPermFilter(permFilter); }
    public String permCategory() { return ranks.permCategory(); }
    public void setPermCategory(String permCategory) { ranks.setPermCategory(permCategory); }
    public int permModeIndex() { return ranks.permModeIndex(); }
    public void cyclePermMode() { ranks.cyclePermMode(); }
    public int permDurationIndex() { return ranks.permDurationIndex(); }
    public void cyclePermDuration() { ranks.cyclePermDuration(); }
    public int rankAssignMode() { return ranks.rankAssignMode(); }
    public void cycleRankAssignMode() { ranks.cycleRankAssignMode(); }
    public String rankAssignLabel() { return ranks.rankAssignLabel(); }
    public int createWeight() { return ranks.createWeight(); }
    public void cycleCreateWeight() { ranks.cycleCreateWeight(); }
    public String customGroup() { return ranks.customGroup(); }
    public void setCustomGroup(String customGroup) { ranks.setCustomGroup(customGroup); }
    public String customNode() { return ranks.customNode(); }
    public void setCustomNode(String customNode) { ranks.setCustomNode(customNode); }

    // --- Item create wizard (forwarded) ---
    public String createDisplayName() { return itemCreate.createDisplayName(); }
    public void setCreateDisplayName(String createDisplayName) { itemCreate.setCreateDisplayName(createDisplayName); }
    public String createIdDraft() { return itemCreate.createIdDraft(); }
    public void setCreateIdDraft(String createIdDraft) { itemCreate.setCreateIdDraft(createIdDraft); }
    public String createTemplate() { return itemCreate.createTemplate(); }
    public void setCreateTemplate(String createTemplate) { itemCreate.setCreateTemplate(createTemplate); }
    public String createAbility() { return itemCreate.createAbility(); }
    public String createAbilitiesLabel() { return itemCreate.createAbilitiesLabel(); }
    public String createAbilitiesPrettyLabel() { return itemCreate.createAbilitiesPrettyLabel(); }
    public java.util.List<String> createAbilities() { return itemCreate.createAbilities(); }
    public java.util.Map<String, String> createAbilityTriggers() { return itemCreate.createAbilityTriggers(); }
    public void setCreateAbility(String createAbility) { itemCreate.setCreateAbility(createAbility); }
    public void toggleCreateAbility(String ability) { itemCreate.toggleCreateAbility(ability); }
    public boolean hasCreateAbility(String ability) { return itemCreate.hasCreateAbility(ability); }
    public void cycleCreateAbilityTrigger(String ability) { itemCreate.cycleCreateAbilityTrigger(ability); }
    public String createAbilityTrigger(String ability) { return itemCreate.createAbilityTrigger(ability); }
    public String createDamage() { return itemCreate.createDamage(); }
    public void setCreateDamage(String createDamage) { itemCreate.setCreateDamage(createDamage); }
    public String createRange() { return itemCreate.createRange(); }
    public void setCreateRange(String createRange) { itemCreate.setCreateRange(createRange); }
    public String createCooldown() { return itemCreate.createCooldown(); }
    public void setCreateCooldown(String createCooldown) { itemCreate.setCreateCooldown(createCooldown); }
    public String createGearAttack() { return itemCreate.createGearAttack(); }
    public void setCreateGearAttack(String createGearAttack) { itemCreate.setCreateGearAttack(createGearAttack); }
    public boolean createGlow() { return itemCreate.createGlow(); }
    public void setCreateGlow(boolean createGlow) { itemCreate.setCreateGlow(createGlow); }
    public void toggleCreateGlow() { itemCreate.toggleCreateGlow(); }
    public boolean createUnbreakable() { return itemCreate.createUnbreakable(); }
    public void setCreateUnbreakable(boolean createUnbreakable) { itemCreate.setCreateUnbreakable(createUnbreakable); }
    public void toggleCreateUnbreakable() { itemCreate.toggleCreateUnbreakable(); }
    public java.util.Map<String, Integer> createEnchants() { return itemCreate.createEnchants(); }
    public int createEnchantLevel(String id) { return itemCreate.createEnchantLevel(id); }
    /** Cycle off → 1 → … → max → off. */
    public void cycleCreateEnchant(String id) { itemCreate.cycleCreateEnchant(id); }
    public void clearCreateEnchants() { itemCreate.clearCreateEnchants(); }
    public String createEnchantsCompact() { return itemCreate.createEnchantsCompact(); }
    public String createEnchantsPrettyLabel() { return itemCreate.createEnchantsPrettyLabel(); }
    public String createPotionEffect() { return itemCreate.createPotionEffect(); }
    public void setCreatePotionEffect(String createPotionEffect) { itemCreate.setCreatePotionEffect(createPotionEffect); }
    public void cycleCreatePotionEffect() { itemCreate.cycleCreatePotionEffect(); }
    public String createRadius() { return itemCreate.createRadius(); }
    public void setCreateRadius(String createRadius) { itemCreate.setCreateRadius(createRadius); }
    public String createHealAmount() { return itemCreate.createHealAmount(); }
    public void setCreateHealAmount(String createHealAmount) { itemCreate.setCreateHealAmount(createHealAmount); }
    public String createProjectileKind() { return itemCreate.createProjectileKind(); }
    public void setCreateProjectileKind(String createProjectileKind) { itemCreate.setCreateProjectileKind(createProjectileKind); }
    public void cycleCreateProjectileKind() { itemCreate.cycleCreateProjectileKind(); }
    public String createBreakRadius() { return itemCreate.createBreakRadius(); }
    public void setCreateBreakRadius(String createBreakRadius) { itemCreate.setCreateBreakRadius(createBreakRadius); }
    public String createBreakCount() { return itemCreate.createBreakCount(); }
    public void setCreateBreakCount(String createBreakCount) { itemCreate.setCreateBreakCount(createBreakCount); }
    public String createPotionDurationSec() { return itemCreate.createPotionDurationSec(); }
    public void setCreatePotionDurationSec(String createPotionDurationSec) { itemCreate.setCreatePotionDurationSec(createPotionDurationSec); }
    public String createPotionAmplifier() { return itemCreate.createPotionAmplifier(); }
    public void setCreatePotionAmplifier(String createPotionAmplifier) { itemCreate.setCreatePotionAmplifier(createPotionAmplifier); }
    /** Potion duration in Minecraft ticks for --duration. */
    public String createPotionDurationTicks() { return itemCreate.createPotionDurationTicks(); }
    public boolean createShowAllAbilities() { return itemCreate.createShowAllAbilities(); }
    public void setCreateShowAllAbilities(boolean createShowAllAbilities) { itemCreate.setCreateShowAllAbilities(createShowAllAbilities); }
    public void toggleCreateShowAllAbilities() { itemCreate.toggleCreateShowAllAbilities(); }
    public String createItemGroup() { return itemCreate.createItemGroup(); }
}
