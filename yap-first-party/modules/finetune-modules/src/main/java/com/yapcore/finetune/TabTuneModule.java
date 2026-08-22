package com.yapcore.finetune;

import java.util.List;

public final class TabTuneModule extends FineTuneModule {
    @Override
    protected String requiredPaperPlugin() {
        return "YaPTab";
    }

    @Override
    protected String guideTitle() {
        return "YaP Tab fine-tune";
    }

    @Override
    protected List<String> guideLines() {
        return List.of(
                "Config: plugins/YaPTab/config.yml",
                "Cross-server sync: yap-link-plugin-tab-bridge on YaP Link",
                "Commands: /yaptab reload|refresh",
                "Dashboard: Tab panel — edit header/footer/sidebar/boss bar",
                "Docs: plugins/README.md"
        );
    }
}
