package com.yapcore.yapblock;

/** Membership role on a YaPblock island. */
public enum IslandRole {
    OWNER,
    MEMBER,
    TRUSTED,
    BANNED;

    public boolean canBuild() {
        return this == OWNER || this == MEMBER || this == TRUSTED;
    }

    public boolean canManage() {
        return this == OWNER;
    }

    public boolean isBanned() {
        return this == BANNED;
    }
}
