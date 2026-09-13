package com.yapcore.presence.movement;

/** Holds the active Bedrock movement profile for local JE prediction. */
public final class MovementProfileStore {

    private static volatile MovementProfile active = MovementProfile.catalogDefaults();

    private MovementProfileStore() {
    }

    public static void set(MovementProfile profile) {
        if (profile != null) {
            active = profile;
        }
    }

    public static MovementProfile get() {
        return active;
    }
}
