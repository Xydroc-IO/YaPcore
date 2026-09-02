package com.yapcore.perms;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Resolved permission state for one user. */
public record EffectiveUser(
        UUID uuid,
        String name,
        String primaryGroup,
        String displayGroup,
        String prefix,
        String suffix,
        int weight,
        Map<String, Boolean> permissions,
        List<String> groups
) {
    public EffectiveUser {
        permissions = permissions == null ? Map.of() : Map.copyOf(permissions);
        groups = groups == null ? List.of() : List.copyOf(groups);
        if (displayGroup == null || displayGroup.isBlank()) {
            displayGroup = primaryGroup;
        }
    }
}
