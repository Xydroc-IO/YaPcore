package com.yapcore.lib.packet;

import java.util.Objects;

/** ProtocolLib-class NBT handle (NMS {@code Tag} / SNBT string). */
public final class PacketNbt {

    private final Object handle;

    public PacketNbt(Object handle) {
        this.handle = handle;
    }

    public static PacketNbt wrap(Object handle) {
        if (handle instanceof PacketNbt nbt) {
            return nbt;
        }
        return new PacketNbt(handle);
    }

    public static PacketNbt snbt(String snbt) {
        return new PacketNbt(snbt);
    }

    public Object handle() {
        return handle;
    }

    public String snbt() {
        return handle == null ? "" : String.valueOf(handle);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PacketNbt other)) {
            return false;
        }
        return Objects.equals(handle, other.handle);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(handle);
    }

    @Override
    public String toString() {
        return snbt();
    }
}
