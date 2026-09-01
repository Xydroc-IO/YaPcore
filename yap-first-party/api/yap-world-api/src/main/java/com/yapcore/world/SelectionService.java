package com.yapcore.world;

import java.util.Optional;
import java.util.UUID;

/**
 * WorldEdit-class region selection contract (Folia region-safe consumers use owning plugin).
 */
public interface SelectionService {

    Optional<CuboidSelection> selection(UUID playerUuid);

    void setPos1(UUID playerUuid, String world, int x, int y, int z);

    void setPos2(UUID playerUuid, String world, int x, int y, int z);

    void clearSelection(UUID playerUuid);
}
