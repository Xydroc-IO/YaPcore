package com.yapcore.staff;

/** WorldEdit-related staff UI memory (pattern, schem, world, brush radius). */
public final class StaffSessionWorldEdit {

    private String wePattern = "stone";
    private String weSchemName = "";
    private String weWorldName = "world";
    private int weBrushRadius = 5;

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
}
