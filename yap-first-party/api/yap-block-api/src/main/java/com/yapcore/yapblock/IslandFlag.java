package com.yapcore.yapblock;

/** Per-island toggle flags. */
public enum IslandFlag {
    PVP,
    MOB_SPAWN,
    FIRE,
    PUBLIC_VISIT,
    LOCK;

    public boolean defaultValue() {
        return switch (this) {
            case PVP, FIRE, LOCK -> false;
            case MOB_SPAWN, PUBLIC_VISIT -> true;
        };
    }
}
