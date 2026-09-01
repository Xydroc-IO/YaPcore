package com.yapcore.finetune;

import java.util.List;

public final class PlayerDataTuneModule extends FineTuneModule {
    @Override
    protected String requiredPaperPlugin() {
        return "YaPPlayerData";
    }

    @Override
    protected String guideTitle() {
        return "YaP PlayerData fine-tune";
    }

    @Override
    protected List<String> guideLines() {
        return List.of(
                "Config: plugins/YaPPlayerData/config.yml",
                "",
                "economy.enabled          — master money switch",
                "features.homes|warps|kits|mail|shops|jobs|auctions|claims|traders",
                "auth.*                   — offline /login",
                "sync.inventory|xp|vitals|economy",
                "claims.* / claims.tax.*  — land claim + tax (tax needs economy)",
                "",
                "Docs: docs/data/PLAYERDATA.md · docs/data/MARIADB.md",
                "No-econ network: economy.enabled=false"
        );
    }
}
