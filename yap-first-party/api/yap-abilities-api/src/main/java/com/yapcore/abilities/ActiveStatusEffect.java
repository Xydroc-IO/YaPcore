package com.yapcore.abilities;

import java.util.UUID;

public record ActiveStatusEffect(
        String effectId,
        UUID sourceId,
        int stacks,
        long expiresAtTick) {
}
