package com.yapcore.finetune;

import java.util.List;

public final class PermsTuneModule extends FineTuneModule {
    @Override
    protected String requiredPaperPlugin() {
        return "YaPPerms";
    }

    @Override
    protected String guideTitle() {
        return "YaP Perms fine-tune";
    }

    @Override
    protected List<String> guideLines() {
        return List.of(
                "Config: plugins/YaPPerms/config.yml",
                "Native groups/tracks — /yapperm, /promote, /demote",
                "Starter pack: /yapperm applypack",
                "Docs: docs/ops/PERMISSIONS.md"
        );
    }
}
