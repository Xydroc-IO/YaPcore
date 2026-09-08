package com.yapcore.staff;

/** Ranks / permissions editor UI memory. */
public final class StaffSessionRanks {

    private int permPage;
    private String permFilter = "";
    private String permCategory = "all";
    private int permModeIndex; // 0 allow, 1 deny, 2 unset
    private int permDurationIndex;
    private int rankAssignMode; // 0 set primary, 1 add parent, 2 remove parent
    private int createWeight = 25;
    private String customGroup = "";
    private String customNode = "";

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
}
