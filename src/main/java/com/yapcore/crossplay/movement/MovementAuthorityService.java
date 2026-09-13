package com.yapcore.crossplay.movement;

import com.yapcore.crossplay.bedrock.parity.MovementParityTable;
import com.yapcore.crossplay.bedrock.parity.ParityBand;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * Phase 3 movement authority — frozen Bedrock constants for BE attributes, JE attrs,
 * presence predict, and Guard baselines.
 */
public final class MovementAuthorityService {

    private static final Logger LOG = Logger.getLogger("YaPcore.Movement");

    private final MovementParityTable table;

    public MovementAuthorityService(ParityBand band) {
        this.table = MovementParityTable.load(Objects.requireNonNull(band, "band"));
        LOG.info("Movement authority ready band=" + band.id()
                + " speed=" + table.speed()
                + " sprint×=" + table.sprintMultiplier()
                + " fly=" + table.flySpeed()
                + " reachB/E=" + table.reachBlock() + "/" + table.reachEntity());
    }

    public static MovementAuthorityService createDefault() {
        return new MovementAuthorityService(ParityBand.of(ParityBand.DEFAULT));
    }

    public MovementParityTable table() {
        return table;
    }

    public ParityBand band() {
        return table.band();
    }
}
