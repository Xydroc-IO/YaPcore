package com.yapcore.dungeons;

/** Lifecycle of an ephemeral dungeon instance. */
public enum DungeonRunState {
    GENERATING,
    OPEN,
    ACTIVE,
    CLEARED,
    FAILED,
    CLEANING
}
