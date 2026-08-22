package com.yapcore.finetune;

import java.util.List;

public final class ProtectTuneModule extends FineTuneModule {
    @Override
    protected String requiredPaperPlugin() {
        return "YaPProtect";
    }

    @Override
    protected String guideTitle() {
        return "YaP Protect fine-tune";
    }

    @Override
    protected List<String> guideLines() {
        return List.of(
                "Config: plugins/YaPProtect/config.yml",
                "Audit logging + rollback (CoreProtect-class)",
                "Requires shared YaPDB pool",
                "Complements playerdata claims (prevention vs recovery)"
        );
    }
}
