package com.yapcore.factions;

import java.util.UUID;

public record FactionMember(long factionId, UUID playerId, FactionRole role) {
}
