package com.yapcore.finetune;

import java.util.List;

public final class ChatTuneModule extends FineTuneModule {
    @Override
    protected String requiredPaperPlugin() {
        return "YaPChat";
    }

    @Override
    protected String guideTitle() {
        return "YaP Chat fine-tune";
    }

    @Override
    protected List<String> guideLines() {
        return List.of(
                "Config: plugins/YaPChat/config.yml",
                "Channels, PM (/msg /reply), staff chat, filter, slow mode",
                "Unsigned system chat for offline/Via joins",
                "Docs: plugins/README.md"
        );
    }
}
