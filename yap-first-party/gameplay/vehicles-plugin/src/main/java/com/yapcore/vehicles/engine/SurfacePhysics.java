package com.yapcore.vehicles.engine;

import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * Surface → grip / rolling resistance (not in vanilla vehicle physics).
 */
final class SurfacePhysics {

    record Sample(double traction, double rolling, String name) {
    }

    private SurfacePhysics() {
    }

    static Sample sample(Block under) {
        if (under == null) {
            return new Sample(0.55, 1.0, "air");
        }
        Material m = under.getType();
        String n = m.name();
        if (n.contains("ICE") || n.contains("PACKED_ICE") || n.contains("BLUE_ICE")) {
            return new Sample(0.22, 0.35, "ice");
        }
        if (n.contains("SLIME")) {
            return new Sample(0.35, 0.5, "slime");
        }
        if (n.contains("SOUL_SAND") || n.contains("SOUL_SOIL")) {
            return new Sample(0.45, 2.2, "soul");
        }
        if (n.contains("SAND") || n.contains("GRAVEL") || n.contains("SNOW")) {
            return new Sample(0.55, 1.6, "loose");
        }
        if (n.contains("MUD") || n.contains("FARMLAND")) {
            return new Sample(0.5, 1.8, "mud");
        }
        if (n.contains("CONCRETE") || n.contains("BLACKSTONE") || n.contains("BASALT")
                || n.contains("STONE_BRICK") || n.contains("DEEPSLATE")
                || n.contains("ASPHALT") || n.contains("TAR")) {
            return new Sample(1.05, 0.75, "paved");
        }
        if (n.contains("PATH") || n.contains("DIRT") || n.contains("GRASS") || n.contains("PODZOL")) {
            return new Sample(0.85, 1.1, "dirt");
        }
        if (m.isSolid()) {
            return new Sample(0.9, 1.0, "solid");
        }
        return new Sample(0.5, 1.2, "soft");
    }
}
