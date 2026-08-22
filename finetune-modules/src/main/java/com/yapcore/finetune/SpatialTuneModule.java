package com.yapcore.finetune;

import java.util.List;

public final class SpatialTuneModule extends FineTuneModule {
    @Override
    protected String guideTitle() {
        return "YaP Spatial / Phase 3 fine-tune";
    }

    @Override
    protected List<String> guideLines() {
        return List.of(
                "Chassis knobs (not a tick engine — do not fork Paper here).",
                "",
                "config/server.properties:",
                "  paper-phase3-tick-bridge=true",
                "  paper-phase3-nms-tick=true",
                "",
                "JVM (optional overrides):",
                "  -Dyapcore.phase3.spatial-tick=true",
                "  -Dyapcore.phase3.spatial-blockfluid=true",
                "  -Dyapcore.phase3.spatial-random=true",
                "  -Dyapcore.phase3.spatial-blockentities=true",
                "  -Dyapcore.phase3.spatial-redstone=true",
                "  -Dyapcore.phase3.spatial-borders=true",
                "  -Dyapcore.phase3.spatial-tracker=true",
                "  -Dyapcore.phase3.spatial-tracker-players=true  # player sendChanges export (tick stays main)",
                "  -Dyapcore.phase3.spatial-distant-brain=true",
                "  # optional hub/spawn box on its own spatial worker (default off):",
                "  # -Dyapcore.phase3.spatial-spawn=true",
                "  # -Dyapcore.phase3.spawn-radius-chunks=8",
                "",
                "Player tick + Bukkit events stay on Paper main. Tracker packet export",
                "(incl. players when tracker-players=true) runs on spatial after tick.",
                "SPAWN only offloads non-player world work inside |chunk|≤R of origin.",
                "",
                "Docs: docs/PAPER_YAPENGINE_PORT.md · docs/BENCH_VS_PAPER.md"
        );
    }
}
