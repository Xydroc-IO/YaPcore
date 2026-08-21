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
                "Clears unsigned-chat toasts on offline / Via joins.",
                "Docs: plugins/README.md"
        );
    }
}
