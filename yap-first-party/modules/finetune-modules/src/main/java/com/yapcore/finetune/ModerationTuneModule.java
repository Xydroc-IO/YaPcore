package com.yapcore.finetune;

import java.util.List;

public final class ModerationTuneModule extends FineTuneModule {
    @Override
    protected String requiredPaperPlugin() {
        return "YaPModeration";
    }

    @Override
    protected String guideTitle() {
        return "YaP Moderation fine-tune";
    }

    @Override
    protected List<String> guideLines() {
        return List.of(
                "Config: plugins/YaPModeration/config.yml",
                "Commands: /ban /tempban /mute /warn /kick /modhistory",
                "Shared MariaDB via yap-db.jar",
                "Docs: plugins/README.md"
        );
    }
}
