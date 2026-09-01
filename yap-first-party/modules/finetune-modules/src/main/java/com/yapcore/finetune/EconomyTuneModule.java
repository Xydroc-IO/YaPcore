package com.yapcore.finetune;

import java.util.List;

public final class EconomyTuneModule extends FineTuneModule {
    @Override
    protected String requiredPaperPlugin() {
        return "YaPPlayerData";
    }

    @Override
    protected String guideTitle() {
        return "YaP Economy fine-tune";
    }

    @Override
    protected List<String> guideLines() {
        return List.of(
                "Requires module provides: playerdata (and plugins/yap-playerdata.jar).",
                "Config: plugins/YaPPlayerData/config.yml",
                "",
                "economy.enabled: true",
                "features.shops / jobs / auctions / traders — opt-in money surfaces",
                "sync.economy: true for cross-server balance",
                "Vault: soft-depend — YaPEconomy registers when Vault is present",
                "",
                "Docs: docs/data/PLAYERDATA.md"
        );
    }
}
