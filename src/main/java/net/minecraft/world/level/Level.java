package net.minecraft.world.level;

/** NMS Level base. */
public class Level {

    private final String dimensionKey;

    public Level(String dimensionKey) {
        this.dimensionKey = dimensionKey;
    }

    public String getDimensionKey() {
        return dimensionKey;
    }
}
