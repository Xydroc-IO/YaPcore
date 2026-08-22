package com.yapcore.finetune;

import java.util.List;

public final class StackerTuneModule extends FineTuneModule {
    @Override
    protected String requiredPaperPlugin() {
        return "YaPStacker";
    }

    @Override
    protected String guideTitle() {
        return "YaP Stacker fine-tune (GAMEPLAY)";
    }

    @Override
    protected List<String> guideLines() {
        return List.of(
                "Requires GAMEPLAY install: gradle installGameplayDefaults",
                "Config: plugins/YaPStacker/config.yml",
                "  mobs.* / items.* / spawners.* radii & caps",
                "  kill-mode, tools, PlaceholderAPI hooks",
                "Command: /yapstacker",
                "Docs: docs/STACKER.md"
        );
    }
}
