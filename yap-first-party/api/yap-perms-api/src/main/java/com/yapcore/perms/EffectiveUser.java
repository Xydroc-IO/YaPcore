package com.yapcore.perms;

import java.util.Map;
import java.util.UUID;

/** Resolved permission state for one user. */
public record EffectiveUser(
        UUID uuid,
        String name,
        String primaryGroup,
        String prefix,
        String suffix,
        int weight,
        Map<String, Boolean> permissions
) {
}
