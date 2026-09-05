package com.yapcore.playerdata.claims;

import com.yapcore.regions.FlagValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimFlagDecisionTest {

    @Test
    void allowWhenFlagAllow() {
        assertTrue(ClaimFlagDecision.allowPlayerAction(FlagValue.ALLOW, false, false));
    }

    @Test
    void denyUnlessTrustOrBypass() {
        assertFalse(ClaimFlagDecision.allowPlayerAction(FlagValue.DENY, false, false));
        assertTrue(ClaimFlagDecision.allowPlayerAction(FlagValue.DENY, true, false));
        assertTrue(ClaimFlagDecision.allowPlayerAction(FlagValue.DENY, false, true));
    }

    @Test
    void explosionIsFlagOnly() {
        assertTrue(ClaimFlagDecision.allowExplosion(FlagValue.ALLOW));
        assertFalse(ClaimFlagDecision.allowExplosion(FlagValue.DENY));
    }
}
