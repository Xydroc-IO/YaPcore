package com.yapcore.vehicles.api;

/**
 * CustomModelData IDs for high-res vehicle body models in
 * {@code resourcepacks/yap-vehicles} (paper overrides 77201+).
 */
public final class HighResModels {

    private HighResModels() {
    }

    public static final int CHASSIS = 77200;
    public static final int BUGGY = 77201;
    public static final int TRUCK_4X4 = 77202;
    public static final int MONSTER_TRUCK = 77203;
    public static final int SPORT_CAR = 77204;
    public static final int HYPERCAR = 77205;
    public static final int LAMBO = 77206;
    public static final int FERRARI = 77207;
    public static final int MCLAREN = 77208;
    public static final int PORSCHE = 77209;
    public static final int HOVERBIKE = 77210;

    /**
     * ItemDisplay scale for Automobility-derived body models (element space ~30–45).
     * Tuned lower than the old 17-cube placeholders.
     */
    public static double bodyScale(String typeId) {
        return switch (typeId.toLowerCase()) {
            case "monster_truck" -> 1.35;
            case "truck_4x4" -> 1.2;
            case "hoverbike" -> 0.95;
            case "chassis" -> 1.0;
            case "hypercar", "lambo", "ferrari", "mclaren" -> 1.05;
            case "sport_car", "porsche", "buggy" -> 1.0;
            default -> 1.05;
        };
    }
}
