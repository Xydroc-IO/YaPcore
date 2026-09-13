package com.yapcore.presence.movement;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Bedrock-style placement face assist from catalog
 * {@code player.placement.face_assist}.
 */
public final class FaceAssist {

    /** Cosine threshold: hits more glancing than this get remapped. */
    private static final double GLANCE_DOT = 0.55;

    private FaceAssist() {
    }

    /**
     * If the look direction is glancing off {@code hit}'s face, return a hit
     * whose face is the dominant look axis (N/S/E/W/U/D). Otherwise return
     * {@code hit} unchanged.
     */
    public static BlockHitResult adjust(LocalPlayer player, BlockHitResult hit) {
        if (player == null || hit == null) {
            return hit;
        }
        Vec3 look = player.getLookAngle();
        Direction face = hit.getDirection();
        Vec3 normal = Vec3.atLowerCornerOf(face.getUnitVec3i());
        double align = -look.dot(normal); // facing into the face → positive
        if (align >= GLANCE_DOT) {
            return hit;
        }
        Direction assisted = dominantLookFace(look);
        if (assisted == face) {
            return hit;
        }
        return hit.withDirection(assisted);
    }

    static Direction dominantLookFace(Vec3 look) {
        double ax = Math.abs(look.x);
        double ay = Math.abs(look.y);
        double az = Math.abs(look.z);
        if (ay >= ax && ay >= az) {
            return look.y > 0 ? Direction.UP : Direction.DOWN;
        }
        if (ax >= az) {
            return look.x > 0 ? Direction.EAST : Direction.WEST;
        }
        return look.z > 0 ? Direction.SOUTH : Direction.NORTH;
    }
}
