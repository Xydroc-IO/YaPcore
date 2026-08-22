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

    /** Suggested ItemDisplay scale for a full body model at cabin mount. */
    public static double bodyScale(String typeId) {
        return switch (typeId.toLowerCase()) {
            case "monster_truck" -> 2.8;
            case "truck_4x4" -> 2.5;
            case "hoverbike" -> 1.6;
            case "chassis" -> 2.0;
            case "hypercar", "lambo", "ferrari", "mclaren" -> 2.2;
            case "sport_car", "porsche", "buggy" -> 2.15;
            default -> 2.2;
        };
    }
}
