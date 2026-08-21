package com.yapcore.finetune;

import java.util.List;

public final class OpsDashboardTuneModule extends FineTuneModule {
    @Override
    protected String guideTitle() {
        return "YaP Web dashboard fine-tune";
    }

    @Override
    protected List<String> guideLines() {
        return List.of(
                "config/server.properties:",
                "  web-dashboard-enabled=true",
                "  web-dashboard-port=8080",
                "  web-dashboard-bind=127.0.0.1",
                "  web-dashboard-token=<secret>",
                "  web-dashboard-localhost-only=true",
                "  yap-ranks-auto-apply=false",
                "",
                "Open http://127.0.0.1:8080/ — Console, Packs, Ranks tabs.",
                "Docs: docs/WEB_DASHBOARD.md · docs/PERMISSIONS.md"
        );
    }
}
