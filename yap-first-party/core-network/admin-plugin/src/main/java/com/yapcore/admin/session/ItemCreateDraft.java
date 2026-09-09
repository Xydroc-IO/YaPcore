package com.yapcore.admin.session;

import com.yapcore.admin.gui.AbilityCatalog;
import com.yapcore.admin.gui.EnchantCatalog;
import com.yapcore.admin.gui.ItemTemplateCatalog;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** Draft state for the custom-item create wizard. */
public final class ItemCreateDraft {

    private String template = "sword";
    private String id = "";
    private String displayName = "";
    private final List<ItemCreateAbilitySlot> abilities = new ArrayList<>();
    private String cooldown = "5s";
    private double damage = 8;
    private double range = 8;
    private int gearAttack = 0;
    private int gearStrength = 0;
    private boolean furniture;
    private final ItemCreateDraftLook look = new ItemCreateDraftLook();
    private final java.util.LinkedHashMap<String, Integer> enchants = new java.util.LinkedHashMap<>();
    private final java.util.LinkedHashSet<String> potionEffects = new java.util.LinkedHashSet<>();
    private double radius = 4;
    private double healAmount = 6;
    private String projectileKind = "snowball";
    private int breakRadius = 0;
    private int breakCount = 1;
    private int potionDurationSec = 10;
    private int potionAmplifier = 0;
    private final ItemCreateDraftFx fx = new ItemCreateDraftFx();
    private boolean showAllAbilities;
    /** When true, buildCreateCommand includes --replace (edit existing custom item). */
    private boolean replaceExisting;
    /** 0=none, 1=display name, 2=id */
    private int pendingChat;
    /** Index into abilities for trigger-edit menu; -1 = none. */
    private int editingTriggerIndex = -1;

    List<ItemCreateAbilitySlot> abilitiesMutable() {
        return abilities;
    }

    java.util.LinkedHashMap<String, Integer> enchantsMutable() {
        return enchants;
    }

    java.util.LinkedHashSet<String> potionEffectsMutable() {
        return potionEffects;
    }

    {
        potionEffects.add("SPEED");
    }

    public void resetForTemplate(String template) {
        ItemCreateDraftLoader.resetForTemplate(this, template);
    }

    void applyResetDisplayName(String label) {
        if (displayName == null || displayName.isBlank()) {
            this.displayName = "&f" + label;
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

    public List<ItemCreateAbilitySlot> abilitySlots() {
        return List.copyOf(abilities);
    }

    public List<String> abilities() {
        List<String> out = new ArrayList<>();
        for (ItemCreateAbilitySlot slot : abilities) {
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
            ItemCreateAbilitySlot s = abilities.get(i);
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
            abilities.add(new ItemCreateAbilitySlot(ability, "right_click"));
        }
    }

    public void toggleAbility(String ability) {
        if (ability == null || ability.isBlank() || "none".equalsIgnoreCase(ability)) {
            abilities.clear();
            return;
        }
        String a = ability.trim().toLowerCase(Locale.ROOT);
        for (Iterator<ItemCreateAbilitySlot> it = abilities.iterator(); it.hasNext(); ) {
            if (it.next().type().equals(a)) {
                it.remove();
                return;
            }
        }
        // First ability defaults to right-click; extras default to Together (same key).
        String trigger = abilities.isEmpty() ? "right_click" : "together";
        abilities.add(new ItemCreateAbilitySlot(a, trigger));
    }

    public boolean hasAbility(String ability) {
        if (ability == null) {
            return false;
        }
        String a = ability.trim().toLowerCase(Locale.ROOT);
        for (ItemCreateAbilitySlot slot : abilities) {
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
        ItemCreateDraftCycles.cycleTrigger(this, index);
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
        ItemCreateDraftCycles.cycleDamage(this);
    }

    public String damageLabel() {
        return damage < 0 ? "INSTAKILL" : ItemCreateDraftCommands.trimNum(damage);
    }

    public double range() {
        return range;
    }

    public void setRange(double range) {
        this.range = Math.max(1, range);
    }

    public void cycleRange() {
        ItemCreateDraftCycles.cycleRange(this);
    }

    public void cycleCooldown() {
        ItemCreateDraftCycles.cycleCooldown(this);
    }

    public int gearAttack() {
        return gearAttack;
    }

    public void setGearAttack(int gearAttack) {
        this.gearAttack = Math.max(0, gearAttack);
    }

    public void cycleGearAttack() {
        ItemCreateDraftCycles.cycleGearAttack(this);
    }

    public int gearStrength() {
        return gearStrength;
    }

    public void setGearStrength(int gearStrength) {
        this.gearStrength = Math.max(0, gearStrength);
    }

    public void cycleGearStrength() {
        ItemCreateDraftCycles.cycleGearStrength(this);
    }

    public String potionEffect() {
        return ItemCreateDraftCycles.primaryPotion(this);
    }

    public String potionEffectsCompact() {
        return ItemCreateDraftCycles.potionCompact(this);
    }

    public String potionEffectsLabel() {
        return ItemCreateDraftCycles.potionLabel(this);
    }

    public void setPotionEffect(String potionEffect) {
        ItemCreateDraftCycles.setPrimaryPotion(this, potionEffect);
    }

    public void clearPotionEffects() {
        ItemCreateDraftCycles.clearPotions(this);
    }

    public void cyclePotionEffect(boolean shift) {
        ItemCreateDraftCycles.cyclePotion(this, shift);
    }

    public double radius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = Math.max(1, radius);
    }

    public void cycleRadius() {
        ItemCreateDraftCycles.cycleRadius(this);
    }

    public double healAmount() {
        return healAmount;
    }

    public void setHealAmount(double healAmount) {
        this.healAmount = Math.max(1, Math.min(100, healAmount));
    }

    public void cycleHealAmount() {
        ItemCreateDraftCycles.cycleHealAmount(this);
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
        ItemCreateDraftCycles.cycleProjectileKind(this);
    }

    public int breakRadius() {
        return breakRadius;
    }

    void setBreakRadius(int breakRadius) {
        this.breakRadius = Math.max(0, breakRadius);
    }

    public void cycleBreakRadius() {
        ItemCreateDraftCycles.cycleBreakRadius(this);
    }

    public int breakCount() {
        return breakCount;
    }

    void setBreakCount(int breakCount) {
        this.breakCount = Math.max(1, breakCount);
    }

    public void cycleBreakCount() {
        ItemCreateDraftCycles.cycleBreakCount(this);
    }

    /** Seconds of potion duration, or {@code -1} for unlimited. */
    public int potionDurationSec() {
        return potionDurationSec < 0 ? -1 : Math.max(1, potionDurationSec);
    }
    public String potionDurationLabel() {
        return potionDurationSec() < 0 ? "unlimited" : (potionDurationSec() + "s");
    }
    void setPotionDurationSec(int potionDurationSec) {
        this.potionDurationSec = potionDurationSec < 0 ? -1 : Math.max(1, potionDurationSec);
    }
    public void cyclePotionDurationSec() { ItemCreateDraftCycles.cyclePotionDurationSec(this); }
    public int potionAmplifier() { return Math.max(0, potionAmplifier); }
    void setPotionAmplifier(int potionAmplifier) {
        this.potionAmplifier = Math.max(0, Math.min(99, potionAmplifier));
    }
    public void cyclePotionAmplifier() { ItemCreateDraftCycles.cyclePotionAmplifier(this); }
    public ItemCreateDraftFx fx() { return fx; }
    public boolean showAllAbilities() { return showAllAbilities; }
    public void toggleShowAllAbilities() { showAllAbilities = !showAllAbilities; }

    public String itemGroup() {
        ItemTemplateCatalog.Entry e = ItemTemplateCatalog.get(template());
        return e == null ? "weapon" : e.group();
    }

    public boolean furniture() { return furniture; }
    public void setFurniture(boolean furniture) { this.furniture = furniture; }
    public boolean glow() { return look.glow(); }
    public void setGlow(boolean glow) { look.setGlow(glow); }
    public void toggleGlow() { look.toggleGlow(); }
    public boolean unbreakable() { return look.unbreakable(); }
    public void setUnbreakable(boolean unbreakable) { look.setUnbreakable(unbreakable); }
    public void toggleUnbreakable() { look.toggleUnbreakable(); }
    public boolean rainbow() { return look.rainbow(); }
    public void setRainbow(boolean rainbow) { look.setRainbow(rainbow); }
    public void toggleRainbow() { look.toggleRainbow(); }

    public java.util.Map<String, Integer> enchants() {
        return java.util.Collections.unmodifiableMap(enchants);
    }

    public int enchantLevel(String id) {
        if (id == null) {
            return 0;
        }
        return enchants.getOrDefault(id.toLowerCase(Locale.ROOT), 0);
    }

    public void cycleEnchant(String id) {
        ItemCreateDraftCycles.cycleEnchant(this, id);
    }

    public void clearEnchants() {
        enchants.clear();
    }

    public String enchantsCompact() {
        if (enchants.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (var e : enchants.entrySet()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(e.getKey()).append(':').append(e.getValue());
        }
        return sb.toString();
    }

    public String enchantsLabel() {
        if (enchants.isEmpty()) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        for (var e : enchants.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(EnchantCatalog.labelWithLevel(e.getKey(), e.getValue()));
        }
        return sb.toString();
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
        return ItemCreateDraftLoader.loadFromCustomFile(this, itemId);
    }

    public int pendingChat() {
        return pendingChat;
    }

    public void setPendingChat(int pendingChat) {
        this.pendingChat = pendingChat;
    }

    public String buildCreateCommand() {
        return ItemCreateDraftCommands.buildCreateCommand(this);
    }

    public boolean usesDamage() { return ItemCreateDraftCommands.usesDamage(this); }
    public boolean usesRange() { return ItemCreateDraftCommands.usesRange(this); }
    public boolean usesRadius() { return ItemCreateDraftCommands.usesRadius(this); }
    public boolean usesPotion() { return ItemCreateDraftCommands.usesPotion(this); }
    public boolean usesPotionPower() { return ItemCreateDraftCommands.usesPotionPower(this); }
    public boolean usesHeal() { return ItemCreateDraftCommands.usesHeal(this); }
    public boolean usesProjectile() { return ItemCreateDraftCommands.usesProjectile(this); }
    public boolean usesBreakVolume() { return ItemCreateDraftCommands.usesBreakVolume(this); }
}
