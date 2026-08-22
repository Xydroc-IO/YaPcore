package com.yapcore.finetune;

import java.util.List;

public final class PacksTuneModule extends FineTuneModule {
    @Override
    protected String requiredPaperPlugin() {
        return "YaPPacks";
    }

    @Override
    protected String guideTitle() {
        return "YaP Packs fine-tune";
    }

    @Override
    protected List<String> guideLines() {
        return List.of(
                "Product: config/server.properties",
                "  resource-pack-enabled / resource-pack-file(s) / resource-pack-url",
                "  resource-pack-forced / resource-pack-http-port / public-pack-port",
                "",
                "Plugin: plugins/YaPPacks/config.yml (extras push on join)",
                "GAMEPLAY fat pack: gradle assembleRelease -PyapGameplay=true",
                "",
                "Docs: docs/CLIENTS_AND_PACKS.md"
        );
    }
}
