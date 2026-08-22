package com.yapcore.finetune;

import java.util.List;

public final class DbTuneModule extends FineTuneModule {
    @Override
    protected String requiredPaperPlugin() {
        return "YaPDB";
    }

    @Override
    protected String guideTitle() {
        return "YaPDB fine-tune";
    }

    @Override
    protected List<String> guideLines() {
        return List.of(
                "Config: plugins/YaPDB/config.yml (shared Hikari JDBC)",
                "Docker: ./scripts/db/start-mariadb.sh && ./scripts/db/configure-db.sh",
                "Commands: /yapdb status|reload",
                "API: yap-db-api.jar for plugin authors",
                "Docs: docs/YAPDB.md · docs/MARIADB.md"
        );
    }
}
