package com.yapcore.admin.session;

import org.bukkit.Material;

import java.util.UUID;

/** Per-admin GUI state for the super menu. */
public final class AdminSession {

    public enum MaterialCategory {
        ALL, BLOCKS, TOOLS, COMBAT, FOOD, MISC
    }

    private UUID targetUuid;
    private String targetName;
    private int giveAmount = 1;
    private int materialPage;
    private MaterialCategory category = MaterialCategory.ALL;
    private boolean confirmClear;
    private Material pendingMaterial;
    /** When true, player-head click opens troll menu instead of player actions. */
    private boolean pickForTrolls;
    private String customItemId = "";
    /** Template group for create wizard: weapon|tool|gem|prop|other */
    private String createTemplateGroup = "weapon";
    /** When true, next chat message is parsed as ability cooldown duration. */
    private boolean pendingAbilityCooldownChat;
    private final ItemCreateDraft itemCreate = new ItemCreateDraft();

    public UUID targetUuid() {
        return targetUuid;
    }

    public String targetName() {
        return targetName;
    }

    public void setTarget(UUID uuid, String name) {
        this.targetUuid = uuid;
        this.targetName = name;
        this.confirmClear = false;
    }

    public void clearTarget() {
        this.targetUuid = null;
        this.targetName = null;
        this.confirmClear = false;
    }

    public boolean hasTarget() {
        return targetUuid != null;
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

    public void setGiveAmount(int amount) {
        this.giveAmount = Math.max(1, Math.min(64, amount));
    }

    public int materialPage() {
        return materialPage;
    }

    public void setMaterialPage(int page) {
        this.materialPage = Math.max(0, page);
    }

    public MaterialCategory category() {
        return category;
    }

    public void setCategory(MaterialCategory category) {
        this.category = category == null ? MaterialCategory.ALL : category;
        this.materialPage = 0;
    }

    public boolean confirmClear() {
        return confirmClear;
    }

    public void setConfirmClear(boolean confirmClear) {
        this.confirmClear = confirmClear;
    }

    public Material pendingMaterial() {
        return pendingMaterial;
    }

    public void setPendingMaterial(Material pendingMaterial) {
        this.pendingMaterial = pendingMaterial;
    }

    public boolean pickForTrolls() {
        return pickForTrolls;
    }

    public void setPickForTrolls(boolean pickForTrolls) {
        this.pickForTrolls = pickForTrolls;
    }

    public String customItemId() {
        return customItemId == null ? "" : customItemId;
    }

    public void setCustomItemId(String customItemId) {
        this.customItemId = customItemId == null ? "" : customItemId.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public String createTemplateGroup() {
        return createTemplateGroup == null || createTemplateGroup.isBlank() ? "weapon" : createTemplateGroup;
    }

    public void setCreateTemplateGroup(String createTemplateGroup) {
        this.createTemplateGroup = createTemplateGroup == null || createTemplateGroup.isBlank()
                ? "weapon"
                : createTemplateGroup.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public boolean pendingAbilityCooldownChat() {
        return pendingAbilityCooldownChat;
    }

    public void setPendingAbilityCooldownChat(boolean pendingAbilityCooldownChat) {
        this.pendingAbilityCooldownChat = pendingAbilityCooldownChat;
    }

    public ItemCreateDraft itemCreate() {
        return itemCreate;
    }
}
