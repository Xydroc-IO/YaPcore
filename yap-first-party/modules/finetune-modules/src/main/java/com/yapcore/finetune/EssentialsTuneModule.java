package com.yapcore.finetune;

import java.util.List;

public final class EssentialsTuneModule extends FineTuneModule {
    @Override
    protected String requiredPaperPlugin() {
        return "YaPEssentials";
    }

    @Override
    protected String guideTitle() {
        return "YaP Essentials fine-tune";
    }

    @Override
    protected List<String> guideLines() {
        return List.of(
                "Config: plugins/YaPEssentials/config.yml",
                "QoL commands: /spawn /back /tpa /fly /vanish /repair …",
                "Staff (features.staff): /freeze /check — PM spy is YaPChat yapchat.socialspy",
                "Set spawn: /setspawn (config + shared DB)",
                "Docs: plugins/README.md"
        );
    }
}
