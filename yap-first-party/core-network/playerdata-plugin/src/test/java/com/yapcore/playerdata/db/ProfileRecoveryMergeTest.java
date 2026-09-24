package com.yapcore.playerdata.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards helpers used by lobby→global recovery (restart inv/bag dupe source). */
final class ProfileRecoveryMergeTest {

    @Test
    void blankBlobDetected() {
        assertTrue(ProfileRecovery.isBlank(null, 45));
        assertTrue(ProfileRecovery.isBlank(new byte[0], 45));
        assertTrue(ProfileRecovery.isBlank(new byte[16], 45));
    }

    @Test
    void emptyStackDetected() {
        assertTrue(ProfileRecovery.isEmpty(null));
    }
}
