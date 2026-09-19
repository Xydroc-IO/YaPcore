package com.yapcore.finetune;

import java.util.List;

public final class HoloTuneModule extends FineTuneModule {
    @Override
    protected String requiredPaperPlugin() {
        return "YaPHolo";
    }

    @Override
    protected String guideTitle() {
        return "YaP Holo fine-tune";
    }

    @Override
    protected List<String> guideLines() {
        return List.of(
                "Config: plugins/YaPHolo/config.yml",
                "Packet holograms — attach, PAPI, clicks, items, pages",
                "Commands: /yapholo create|attach|click|setpages|setlines …",
                "NPC nametags: YaPNpcs hologram-nametags (npcntag_<id>)",
                "Storage: plugins/YaPHolo/holograms.yml + animations.yml",
                "Docs: docs/plugins/YAPHOLO.md"
        );
    }
}
