package com.yapcore.yap420.market;

/**
 * Exact pack ladder: 1 cured bud ≡ 1 gram; {@code gramsPerOunce} grams → 1 ounce;
 * {@code ouncesPerBrick} ounces → 1 brick (pound).
 */
public final class PackMath {

    private final int gramsPerOunce;
    private final int ouncesPerBrick;

    public PackMath(int gramsPerOunce, int ouncesPerBrick) {
        this.gramsPerOunce = Math.max(1, gramsPerOunce);
        this.ouncesPerBrick = Math.max(1, ouncesPerBrick);
    }

    public int gramsPerOunce() {
        return gramsPerOunce;
    }

    public int ouncesPerBrick() {
        return ouncesPerBrick;
    }

    public int gramsPerBrick() {
        return gramsPerOunce * ouncesPerBrick;
    }

    /** How many output packs can be made from {@code inputCount} of the input unit. */
    public int packsFrom(PackUnit from, PackUnit to, int inputCount) {
        if (inputCount <= 0 || from == null || to == null) {
            return 0;
        }
        int grams = toGrams(from, inputCount);
        return switch (to) {
            case GRAM -> grams;
            case OUNCE -> grams / gramsPerOunce;
            case BRICK -> grams / gramsPerBrick();
        };
    }

    /** Grams consumed to produce {@code packs} of {@code unit}. */
    public int gramsFor(PackUnit unit, int packs) {
        if (packs <= 0 || unit == null) {
            return 0;
        }
        return switch (unit) {
            case GRAM -> packs;
            case OUNCE -> packs * gramsPerOunce;
            case BRICK -> packs * gramsPerBrick();
        };
    }

    public int toGrams(PackUnit unit, int count) {
        if (count <= 0 || unit == null) {
            return 0;
        }
        return switch (unit) {
            case GRAM -> count;
            case OUNCE -> count * gramsPerOunce;
            case BRICK -> count * gramsPerBrick();
        };
    }

    public int fromGrams(PackUnit unit, int grams) {
        if (grams <= 0 || unit == null) {
            return 0;
        }
        return switch (unit) {
            case GRAM -> grams;
            case OUNCE -> grams / gramsPerOunce;
            case BRICK -> grams / gramsPerBrick();
        };
    }
}
