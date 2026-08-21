package com.yapcore.vehicles.api;

/**
 * Additive / multiplicative stat changes from an upgrade part.
 * Multipliers default to 1.0; additives default to 0.
 */
public final class StatModifier {

    private final double maxSpeedMul;
    private final double accelerationMul;
    private final double brakeMul;
    private final double turnRateMul;
    private final double tractionMul;
    private final double lateralGripMul;
    private final double fuelBurnMul;
    private final double maxFuelAdd;
    private final double maxHealthAdd;
    private final double boostMul;
    private final double rollingResistanceMul;
    private final double rideHeightAdd;
    private final double tireScaleMul;
    private final double slopeGripMul;

    private StatModifier(Builder b) {
        this.maxSpeedMul = b.maxSpeedMul;
        this.accelerationMul = b.accelerationMul;
        this.brakeMul = b.brakeMul;
        this.turnRateMul = b.turnRateMul;
        this.tractionMul = b.tractionMul;
        this.lateralGripMul = b.lateralGripMul;
        this.fuelBurnMul = b.fuelBurnMul;
        this.maxFuelAdd = b.maxFuelAdd;
        this.maxHealthAdd = b.maxHealthAdd;
        this.boostMul = b.boostMul;
        this.rollingResistanceMul = b.rollingResistanceMul;
        this.rideHeightAdd = b.rideHeightAdd;
        this.tireScaleMul = b.tireScaleMul;
        this.slopeGripMul = b.slopeGripMul;
    }

    public double maxSpeedMul() {
        return maxSpeedMul;
    }

    public double accelerationMul() {
        return accelerationMul;
    }

    public double brakeMul() {
        return brakeMul;
    }

    public double turnRateMul() {
        return turnRateMul;
    }

    public double tractionMul() {
        return tractionMul;
    }

    public double lateralGripMul() {
        return lateralGripMul;
    }

    public double fuelBurnMul() {
        return fuelBurnMul;
    }

    public double maxFuelAdd() {
        return maxFuelAdd;
    }

    public double maxHealthAdd() {
        return maxHealthAdd;
    }

    public double boostMul() {
        return boostMul;
    }

    public double rollingResistanceMul() {
        return rollingResistanceMul;
    }

    /** Extra clearance above ground (blocks) — lift kits. */
    public double rideHeightAdd() {
        return rideHeightAdd;
    }

    /** Wheel visual + grip scale (1.0 = stock). */
    public double tireScaleMul() {
        return tireScaleMul;
    }

    public double slopeGripMul() {
        return slopeGripMul;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static StatModifier none() {
        return builder().build();
    }

    public StatModifier and(StatModifier other) {
        return builder()
                .maxSpeedMul(maxSpeedMul * other.maxSpeedMul)
                .accelerationMul(accelerationMul * other.accelerationMul)
                .brakeMul(brakeMul * other.brakeMul)
                .turnRateMul(turnRateMul * other.turnRateMul)
                .tractionMul(tractionMul * other.tractionMul)
                .lateralGripMul(lateralGripMul * other.lateralGripMul)
                .fuelBurnMul(fuelBurnMul * other.fuelBurnMul)
                .maxFuelAdd(maxFuelAdd + other.maxFuelAdd)
                .maxHealthAdd(maxHealthAdd + other.maxHealthAdd)
                .boostMul(boostMul * other.boostMul)
                .rollingResistanceMul(rollingResistanceMul * other.rollingResistanceMul)
                .rideHeightAdd(rideHeightAdd + other.rideHeightAdd)
                .tireScaleMul(tireScaleMul * other.tireScaleMul)
                .slopeGripMul(slopeGripMul * other.slopeGripMul)
                .build();
    }

    public static final class Builder {
        private double maxSpeedMul = 1;
        private double accelerationMul = 1;
        private double brakeMul = 1;
        private double turnRateMul = 1;
        private double tractionMul = 1;
        private double lateralGripMul = 1;
        private double fuelBurnMul = 1;
        private double maxFuelAdd;
        private double maxHealthAdd;
        private double boostMul = 1;
        private double rollingResistanceMul = 1;
        private double rideHeightAdd;
        private double tireScaleMul = 1;
        private double slopeGripMul = 1;

        public Builder maxSpeedMul(double v) {
            this.maxSpeedMul = v;
            return this;
        }

        public Builder accelerationMul(double v) {
            this.accelerationMul = v;
            return this;
        }

        public Builder brakeMul(double v) {
            this.brakeMul = v;
            return this;
        }

        public Builder turnRateMul(double v) {
            this.turnRateMul = v;
            return this;
        }

        public Builder tractionMul(double v) {
            this.tractionMul = v;
            return this;
        }

        public Builder lateralGripMul(double v) {
            this.lateralGripMul = v;
            return this;
        }

        public Builder fuelBurnMul(double v) {
            this.fuelBurnMul = v;
            return this;
        }

        public Builder maxFuelAdd(double v) {
            this.maxFuelAdd = v;
            return this;
        }

        public Builder maxHealthAdd(double v) {
            this.maxHealthAdd = v;
            return this;
        }

        public Builder boostMul(double v) {
            this.boostMul = v;
            return this;
        }

        public Builder rollingResistanceMul(double v) {
            this.rollingResistanceMul = v;
            return this;
        }

        public Builder rideHeightAdd(double v) {
            this.rideHeightAdd = v;
            return this;
        }

        public Builder tireScaleMul(double v) {
            this.tireScaleMul = v;
            return this;
        }

        public Builder slopeGripMul(double v) {
            this.slopeGripMul = v;
            return this;
        }

        public StatModifier build() {
            return new StatModifier(this);
        }
    }
}
