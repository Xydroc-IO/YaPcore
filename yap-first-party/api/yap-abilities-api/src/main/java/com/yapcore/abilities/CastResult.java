package com.yapcore.abilities;

public enum CastResult {
    SUCCESS,
    UNKNOWN_ABILITY,
    ON_COOLDOWN,
    LEVEL_TOO_LOW,
    MISSING_COST,
    NO_TARGET,
    INVALID_TARGET,
    PVP_DENIED,
    CONDITION_FAILED,
    FAILED;

    public boolean ok() {
        return this == SUCCESS;
    }
}
