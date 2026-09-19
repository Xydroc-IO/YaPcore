package com.yapcore.lib.packet;

import java.util.Objects;

/** ProtocolLib-class block position (converts to NMS {@code BlockPos}). */
public final class PacketBlockPos {

    private final int x;
    private final int y;
    private final int z;

    public PacketBlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PacketBlockPos other)) {
            return false;
        }
        return x == other.x && y == other.y && z == other.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return x + "," + y + "," + z;
    }
}
