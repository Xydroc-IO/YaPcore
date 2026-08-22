package com.yapcore.finetune;

import java.util.List;

public final class WorldTuneModule extends FineTuneModule {
    @Override
    protected String requiredPaperPlugin() {
        return "YaPWorld";
    }

    @Override
    protected String guideTitle() {
        return "YaP World fine-tune";
    }

    @Override
    protected List<String> guideLines() {
        return List.of(
                "Config: plugins/YaPWorld/config.yml",
                "World load/unload/teleport (Multiverse-class)",
                "Region selection + schematic paste (WorldEdit-class, Folia-safe)",
                "In-game editor: /yapworld gui — golden axe tool with shift+right-click",
                "Integrates with yap-pregen for bulk generation"
        );
    }
}
