package com.yapcore.protect;

/** One block mutation attributed to a YaPWorld / WorldEdit apply batch. */
public record ProtectEditBlock(
        String world,
        int x,
        int y,
        int z,
        String blockBefore,
        String blockAfter
) {
}
