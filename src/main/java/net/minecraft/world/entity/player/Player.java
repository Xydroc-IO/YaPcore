package net.minecraft.world.entity.player;

/** NMS player entity base. */
public class Player {

    private final String name;

    public Player(String name) {
        this.name = name;
    }

    public String getScoreboardName() {
        return name;
    }

    public String getName() {
        return name;
    }
}
