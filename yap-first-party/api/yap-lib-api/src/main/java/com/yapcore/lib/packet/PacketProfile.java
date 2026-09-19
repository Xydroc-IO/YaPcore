package com.yapcore.lib.packet;

import java.util.Objects;
import java.util.UUID;

/** ProtocolLib-class game profile (converts to Mojang {@code GameProfile}). */
public final class PacketProfile {

    private final UUID uuid;
    private final String name;

    public PacketProfile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name == null ? "" : name;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PacketProfile other)) {
            return false;
        }
        return Objects.equals(uuid, other.uuid) && Objects.equals(name, other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, name);
    }
}
