package com.yapcore.finetune;

import java.util.List;

public final class GameplayKnobsTuneModule extends FineTuneModule {
    @Override
    protected String requiredPaperPlugin() {
        return "YaPGameplayKnobs";
    }

    @Override
    protected String guideTitle() {
        return "YaP Gameplay knobs fine-tune (GAMEPLAY)";
    }

    @Override
    protected List<String> guideLines() {
        return List.of(
                "Requires GAMEPLAY install: gradle installGameplayDefaults",
                "Config: plugins/YaPGameplayKnobs/knobs.yml",
                "  settings.* / blocks.* / gameplay.* / mobs.<type>.*",
                "Command: /yapknobs reload",
                "Docs: docs/ops/TUNE.md"
        );
    }
}
