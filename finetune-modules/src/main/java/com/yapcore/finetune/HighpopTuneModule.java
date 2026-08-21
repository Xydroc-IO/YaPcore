package com.yapcore.finetune;

import java.util.List;

public final class HighpopTuneModule extends FineTuneModule {
    @Override
    protected String guideTitle() {
        return "YaP High-pop Paper templates";
    }

    @Override
    protected List<String> guideLines() {
        return List.of(
                "Live configs: config/paper/, config/spigot.yml, config/bukkit.yml",
                "Canonical templates: config/templates/highpop/",
                "Optional tighter EAR: config/templates/highpop-ear/",
                "",
                "GUI Tune tab opens the same paths.",
                "Fair MSPT benches keep EAR uncapped (0) — do not copy highpop-ear for scoreboards.",
                "",
                "Docs: docs/TUNE.md"
        );
    }
}
