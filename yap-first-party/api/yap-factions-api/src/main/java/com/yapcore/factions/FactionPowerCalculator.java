package com.yapcore.factions;

/** Pure power math for factions (unit tested). */
public final class FactionPowerCalculator {

    public record Config(int baseMaxPower, int powerPerMember, int claimBlocksPerPower) {
        public Config {
            if (baseMaxPower < 0) {
                baseMaxPower = 0;
            }
            if (powerPerMember < 0) {
                powerPerMember = 0;
            }
            if (claimBlocksPerPower < 1) {
                claimBlocksPerPower = 1;
            }
        }
    }

    private FactionPowerCalculator() {
    }

    public static int maxPower(Config config, int memberCount) {
        return config.baseMaxPower() + Math.max(0, memberCount) * config.powerPerMember();
    }

    public static int claimCost(Config config, int claimAreaBlocks) {
        if (claimAreaBlocks <= 0) {
            return 1;
        }
        return Math.max(1, (claimAreaBlocks + config.claimBlocksPerPower() - 1) / config.claimBlocksPerPower());
    }

    public static int clampPower(int power, int maxPower) {
        return Math.max(0, Math.min(maxPower, power));
    }
}
