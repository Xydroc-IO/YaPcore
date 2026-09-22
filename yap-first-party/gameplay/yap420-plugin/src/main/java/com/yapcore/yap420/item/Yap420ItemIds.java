package com.yapcore.yap420.item;

import com.yapcore.yap420.plant.StrainId;
import com.yapcore.yap420.market.PackUnit;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Stable YaPItems ids for YaP420 content. */
public final class Yap420ItemIds {

    public static final String SEED_SATIVA = "yap420_seed_sativa";
    public static final String SEED_INDICA = "yap420_seed_indica";
    public static final String BUD_WET_SATIVA = "yap420_bud_wet_sativa";
    public static final String BUD_WET_INDICA = "yap420_bud_wet_indica";
    public static final String BUD_CURED_SATIVA = "yap420_bud_cured_sativa";
    public static final String BUD_CURED_INDICA = "yap420_bud_cured_indica";
    public static final String GRAM_SATIVA = "yap420_gram_sativa";
    public static final String GRAM_INDICA = "yap420_gram_indica";
    public static final String OUNCE_SATIVA = "yap420_ounce_sativa";
    public static final String OUNCE_INDICA = "yap420_ounce_indica";
    public static final String BRICK_SATIVA = "yap420_brick_sativa";
    public static final String BRICK_INDICA = "yap420_brick_indica";
    public static final String ROLLING_PAPER = "yap420_rolling_paper";
    public static final String JOINT_SATIVA = "yap420_joint_sativa";
    public static final String JOINT_INDICA = "yap420_joint_indica";
    public static final String BLUNT_SATIVA = "yap420_blunt_sativa";
    public static final String BLUNT_INDICA = "yap420_blunt_indica";
    public static final String BROWNIE = "yap420_brownie";
    public static final String DRYING_RACK = "yap420_drying_rack";
    public static final String PACKAGING_PRESS = "yap420_packaging_press";
    public static final String HEMP_FIBER = "yap420_hemp_fiber";

    private static final Set<String> CONSUMABLES = Set.of(
            JOINT_SATIVA, JOINT_INDICA, BLUNT_SATIVA, BLUNT_INDICA, BROWNIE);

    private Yap420ItemIds() {
    }

    public static String plantStage(StrainId strain, int stage) {
        int s = Math.max(0, stage);
        return "yap420_plant_" + strain.id() + "_" + s;
    }

    public static String wetBud(StrainId strain) {
        return strain == StrainId.INDICA ? BUD_WET_INDICA : BUD_WET_SATIVA;
    }

    public static String curedBud(StrainId strain) {
        return strain == StrainId.INDICA ? BUD_CURED_INDICA : BUD_CURED_SATIVA;
    }

    public static String seed(StrainId strain) {
        return strain == StrainId.INDICA ? SEED_INDICA : SEED_SATIVA;
    }

    public static String gram(StrainId strain) {
        return strain == StrainId.INDICA ? GRAM_INDICA : GRAM_SATIVA;
    }

    public static String ounce(StrainId strain) {
        return strain == StrainId.INDICA ? OUNCE_INDICA : OUNCE_SATIVA;
    }

    public static String brick(StrainId strain) {
        return strain == StrainId.INDICA ? BRICK_INDICA : BRICK_SATIVA;
    }

    public static String packId(PackUnit unit, StrainId strain) {
        return switch (unit) {
            case GRAM -> gram(strain);
            case OUNCE -> ounce(strain);
            case BRICK -> brick(strain);
        };
    }

    public static Optional<StrainId> strainFromSeed(String itemId) {
        if (SEED_SATIVA.equals(itemId)) {
            return Optional.of(StrainId.SATIVA);
        }
        if (SEED_INDICA.equals(itemId)) {
            return Optional.of(StrainId.INDICA);
        }
        return Optional.empty();
    }

    public static Optional<StrainId> strainFromWetBud(String itemId) {
        if (BUD_WET_SATIVA.equals(itemId)) {
            return Optional.of(StrainId.SATIVA);
        }
        if (BUD_WET_INDICA.equals(itemId)) {
            return Optional.of(StrainId.INDICA);
        }
        return Optional.empty();
    }

    public static Optional<StrainId> strainFromPack(String itemId) {
        if (itemId == null) {
            return Optional.empty();
        }
        String id = itemId.toLowerCase(Locale.ROOT);
        if (id.endsWith("_indica") || id.contains("_indica_")) {
            return Optional.of(StrainId.INDICA);
        }
        if (id.endsWith("_sativa") || id.contains("_sativa_")) {
            return Optional.of(StrainId.SATIVA);
        }
        return Optional.empty();
    }

    public static Optional<PackUnit> packUnitOf(String itemId) {
        if (itemId == null) {
            return Optional.empty();
        }
        String id = itemId.toLowerCase(Locale.ROOT);
        if (GRAM_SATIVA.equals(id) || GRAM_INDICA.equals(id)
                || BUD_CURED_SATIVA.equals(id) || BUD_CURED_INDICA.equals(id)) {
            return Optional.of(PackUnit.GRAM);
        }
        if (OUNCE_SATIVA.equals(id) || OUNCE_INDICA.equals(id)) {
            return Optional.of(PackUnit.OUNCE);
        }
        if (BRICK_SATIVA.equals(id) || BRICK_INDICA.equals(id)) {
            return Optional.of(PackUnit.BRICK);
        }
        return Optional.empty();
    }

    /** True when the stack counts toward gram packing (cured bud or bagged gram). */
    public static boolean isGramSource(String itemId, StrainId strain) {
        if (itemId == null || strain == null) {
            return false;
        }
        return curedBud(strain).equals(itemId) || gram(strain).equals(itemId);
    }

    public static boolean isConsumable(String itemId) {
        return itemId != null && CONSUMABLES.contains(itemId);
    }

    public static boolean isYap420(String itemId) {
        return itemId != null && itemId.toLowerCase(Locale.ROOT).startsWith("yap420_");
    }
}
