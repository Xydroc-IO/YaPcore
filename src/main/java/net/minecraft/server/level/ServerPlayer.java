package net.minecraft.server.level;

import net.minecraft.world.entity.player.Player;

/** NMS server player handle (getHandle() target). */
public final class ServerPlayer extends Player {

    private double x;
    private double y = 64;
    private double z;
    private float yRot;
    private float xRot;

    public ServerPlayer(String name) {
        super(name);
    }

    public void setPos(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYRot() {
        return yRot;
    }

    public float getXRot() {
        return xRot;
    }

    public void teleportTo(ServerLevel level, double x, double y, double z) {
        setPos(x, y, z);
    }
}
