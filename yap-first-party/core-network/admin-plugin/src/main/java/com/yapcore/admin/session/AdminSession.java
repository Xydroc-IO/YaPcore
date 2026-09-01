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
}
