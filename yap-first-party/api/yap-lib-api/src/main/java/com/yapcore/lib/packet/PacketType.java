package com.yapcore.lib.packet;

import java.util.Locale;
import java.util.Objects;

/**
 * Identity for a Minecraft packet. Name is Mojang protocol snake_case
 * ({@code add_entity}, {@code move_player_pos}) without the {@code minecraft:} prefix.
 */
public final class PacketType {

    public static final PacketType ALL = new PacketType(PacketState.ANY, null, "*");

    private final PacketState state;
    private final PacketDirection direction;
    private final String name;

    public PacketType(PacketState state, PacketDirection direction, String name) {
        this.state = Objects.requireNonNull(state, "state");
        this.direction = direction;
        this.name = Objects.requireNonNull(name, "name").toLowerCase(Locale.ROOT);
    }

    public PacketState state() {
        return state;
    }

    /** Null only for {@link #ALL}. */
    public PacketDirection direction() {
        return direction;
    }

    public String name() {
        return name;
    }

    public boolean isWildcard() {
        return this == ALL || "*".equals(name);
    }

    public boolean matches(PacketType other) {
        if (other == null) {
            return false;
        }
        if (isWildcard() || other.isWildcard()) {
            return true;
        }
        if (direction != null && other.direction != null && direction != other.direction) {
            return false;
        }
        if (!name.equals(other.name)) {
            return false;
        }
        if (state == PacketState.ANY || other.state == PacketState.ANY) {
            return true;
        }
        if (state == PacketState.COMMON || other.state == PacketState.COMMON) {
            return true;
        }
        return state == other.state;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PacketType other)) {
            return false;
        }
        return state == other.state && direction == other.direction && name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, direction, name);
    }

    @Override
    public String toString() {
        String dir = direction == null ? "*" : direction.name().toLowerCase(Locale.ROOT);
        return state.name().toLowerCase(Locale.ROOT) + "/" + dir + "/" + name;
    }
}
