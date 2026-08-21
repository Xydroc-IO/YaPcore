package com.yapcore.finetune;

import java.util.List;

public final class FloodgateTuneModule extends FineTuneModule {
    @Override
    protected String requiredPaperPlugin() {
        return "YaPFloodgate";
    }

    @Override
    protected String guideTitle() {
        return "YaP Floodgate fine-tune";
    }

    @Override
    protected List<String> guideLines() {
        return List.of(
                "Config: plugins/YaPFloodgate/config.yml + key.pem from Velocity Floodgate",
                "Docs: docs/VELOCITY.md · docs/CROSSPLAY.md"
        );
    }
}
