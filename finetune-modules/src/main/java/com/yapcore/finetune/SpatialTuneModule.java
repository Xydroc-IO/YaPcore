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
                "  -Dyapcore.phase3.spatial-distant-brain=true",
                "",
                "Docs: docs/PAPER_YAPENGINE_PORT.md · docs/BENCH_VS_PAPER.md"
        );
    }
}
