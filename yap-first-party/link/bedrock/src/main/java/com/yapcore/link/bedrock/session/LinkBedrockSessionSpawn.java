package com.yapcore.link.bedrock.session;

import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.translator.JavaBlockUpdateTranslator;
import java.util.Locale;

/** Split from {@link LinkBedrockSession} for the ≤500-line domain gate. */
final class LinkBedrockSessionSpawn {

    private final LinkBedrockSession s;

    LinkBedrockSessionSpawn(LinkBedrockSession session) {
        this.s = session;
    }

    void pendingSpawnSolidSample(int nearFeet, int columnNonAir, long mapHits, long mapMisses) {
        pendingSpawnSolidSample(nearFeet, columnNonAir, mapHits, mapMisses, Double.NaN);
    }

    void pendingSpawnSolidSample(int nearFeet, int columnNonAir, long mapHits, long mapMisses, double standOnFeetY) {
        s.pendingSolidNear = nearFeet;
        s.pendingSolidColumn = columnNonAir;
        s.pendingMapHits = mapHits;
        s.pendingMapMisses = mapMisses;
        s.pendingStandOnFeetY = standOnFeetY;
    }

    void flushPendingSpawnSolidSample() {
        int near = s.pendingSolidNear;
        if (near >= 0) {
            s.pendingSolidNear = -1;
            int column = s.pendingSolidColumn;
            double standOn = s.pendingStandOnFeetY;
            s.pendingStandOnFeetY = Double.NaN;
            applyStandOnFeetIfNeeded(standOn, false, near);
            BedrockJoinProbe.noteEvent(
                    s.guid,
                    "spawn_column solidBlocks="
                            + near
                            + " columnNonAir="
                            + column
                            + " mapHits="
                            + s.pendingMapHits
                            + " mapMisses="
                            + s.pendingMapMisses
                            + " feetY="
                            + fmt(s.spawnFeetY));
            LinkBedrockSession.LOG.info(
                    "BE spawn_column solidBlocks="
                            + near
                            + " columnNonAir="
                            + column
                            + " user="
                            + s.username
                            + " mapMisses="
                            + s.pendingMapMisses);
            noteSpawnColumnSolid(near);
        }
    }

    void applyStandOnFeetIfNeeded(double standOnFeetY, boolean platform) {
        applyStandOnFeetIfNeeded(standOnFeetY, platform, s.spawnColumnSolidBlocks.get());
    }

    void applyStandOnFeetIfNeeded(double standOnFeetY, boolean platform, int solidNearFeet) {
        if (!Double.isNaN(standOnFeetY)) {
            double before = s.spawnFeetY;
            if (standOnFeetY > before + 0.05) {
                s.setSpawnFromFeet(s.spawnFeetX, standOnFeetY, s.spawnFeetZ);
                s.standOnLiftActive = true;
                punchStandOnAirCells();
                syncJeFeetAfterStandOnLift();
                BedrockJoinProbe.noteEvent(
                        s.guid,
                        "stand_on lift fromFeetY="
                                + fmt(before)
                                + " surfaceY="
                                + fmt(standOnFeetY)
                                + " feetY="
                                + fmt(s.spawnFeetY)
                                + " eyeY="
                                + fmt(s.spawnFeetY + 1.62)
                                + " platform="
                                + platform);
                LinkBedrockSession.LOG.info(
                        "BE stand_on lift user=" + s.username + " from=" + fmt(before) + " to=" + fmt(standOnFeetY));
            } else if (solidNearFeet >= 4) {
                punchStandOnAirCells();
                BedrockJoinProbe.noteEvent(
                        s.guid,
                        "stand_on noLift punchAir feetY="
                                + fmt(s.spawnFeetY)
                                + " solidBlocks="
                                + solidNearFeet
                                + " platform="
                                + platform);
            }
        }
    }

    boolean isStandOnLiftActive() {
        return s.standOnLiftActive;
    }

    boolean shouldRejectBuriedJeFeet(double jeFeetY) {
        if (!s.standOnLiftActive || Double.isNaN(s.spawnFeetY)) {
            return false;
        } else if (!(jeFeetY < s.spawnFeetY - 0.25)) {
            return false;
        } else if (Math.abs(s.posY - jeFeetY) < 0.75) {
            clearStandOnLift("settled_at_je_feet");
            return false;
        } else {
            double dx = s.posX - s.spawnFeetX;
            double dz = s.posZ - s.spawnFeetZ;
            if (dx * dx + dz * dz > 4.0) {
                clearStandOnLift("left_spawn_column");
                return false;
            } else {
                return true;
            }
        }
    }

    void clearStandOnLift(String reason) {
        if (s.standOnLiftActive) {
            s.standOnLiftActive = false;
            BedrockJoinProbe.noteEvent(
                    s.guid,
                    "stand_on lift cleared reason="
                            + reason
                            + " posY="
                            + fmt(s.posY)
                            + " spawnFeetY="
                            + fmt(s.spawnFeetY));
            LinkBedrockSession.LOG.info("BE stand_on lift cleared user=" + s.username + " reason=" + reason);
        }
    }

    void punchStandOnAirCells() {
        int bx = s.spawnX;
        int by = (int) Math.floor(s.spawnFeetY);
        int bz = s.spawnZ;
        int airRt = s.airRuntimeId();

        for (int dy = 0; dy <= 2; dy++) {
            JavaBlockUpdateTranslator.sendUpdateBlock(s, bx, by + dy, bz, airRt);
        }

        BedrockJoinProbe.noteEvent(
                s.guid, "stand_on UpdateBlock air feetY=" + by + " head=" + (by + 1) + " above=" + (by + 2) + " airRt=" + airRt);
    }

    void placeStandOnCollisionPlatform() {
        int standFeetBlockY = (int) Math.floor(s.spawnFeetY);
        int platformY = standFeetBlockY - 1;
        int stoneRt = s.stoneRuntimeId();
        JavaBlockUpdateTranslator.sendUpdateBlock(s, s.spawnX, platformY, s.spawnZ, stoneRt);
        punchStandOnAirCells();
        s.spawnPlatformPlaced = true;
        BedrockJoinProbe.noteEvent(
                s.guid,
                "stand_on collision_platform stoneRt="
                        + stoneRt
                        + " y="
                        + platformY
                        + " feetY="
                        + fmt(s.spawnFeetY)
                        + " size=1");
        LinkBedrockSession.LOG.info(
                "BE stand_on collision_platform user=" + s.username + " stoneRt=" + stoneRt + " y=" + platformY + " size=1");
    }

    void syncJeFeetAfterStandOnLift() {
        JavaDownstreamClient down = s.downstream;
        if (down != null && down.channel() != null && down.channel().isActive()) {
            down.sendMovePosRot(s.spawnFeetX, s.spawnFeetY, s.spawnFeetZ, s.yaw, s.pitch, true);
            s.lastSyncX = s.spawnFeetX;
            s.lastSyncY = s.spawnFeetY;
            s.lastSyncZ = s.spawnFeetZ;
            BedrockJoinProbe.noteEvent(s.guid, "stand_on →JE movePosRot feetY=" + fmt(s.spawnFeetY));
        }
    }

    void noteSpawnColumnReal(int chunkX, int chunkZ) {
        int scx = s.spawnX >> 4;
        int scz = s.spawnZ >> 4;
        if (chunkX == scx && chunkZ == scz) {
            s.spawnColumnReal.set(true);
            s.tryCompletePlayerSpawn("spawn_column_REAL");
        }
    }

    void noteSpawnColumnSolid(int solidBlocksNearFeet) {
        int n = Math.max(0, solidBlocksNearFeet);
        s.spawnColumnSolidBlocks.set(n);
        s.spawnColumnReal.set(true);
        if (n >= 4) {
            s.spawnColumnSolid.set(true);
            s.tryCompletePlayerSpawn("solid");
        } else {
            BedrockJoinProbe.noteEvent(s.guid, "spawn_column solidBlocks=" + n + " need>=4 — defer PLAYER_SPAWN");
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }
}
