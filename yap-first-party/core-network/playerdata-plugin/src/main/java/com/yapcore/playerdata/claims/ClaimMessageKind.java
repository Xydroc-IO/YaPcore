package com.yapcore.playerdata.claims;

import com.yapcore.regions.RegionMessageKind;

/** Alias for claim greeting/farewell kinds (same enum as admin regions). */
public final class ClaimMessageKind {

    private ClaimMessageKind() {
    }

    public static final RegionMessageKind GREETING = RegionMessageKind.GREETING;
    public static final RegionMessageKind FAREWELL = RegionMessageKind.FAREWELL;
}
