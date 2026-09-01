package com.yapcore.finetune;

import java.util.List;

public final class PregenTuneModule extends FineTuneModule {
    @Override
    protected String requiredPaperPlugin() {
        return "YaPPregen";
    }

    @Override
    protected String guideTitle() {
        return "YaP Pregen fine-tune";
    }

    @Override
    protected List<String> guideLines() {
        return List.of(
                "Config: plugins/YaPPregen/config.yml",
                "Command: /yappregen",
                "Docs: docs/plugins/PREGEN.md"
        );
    }
}
