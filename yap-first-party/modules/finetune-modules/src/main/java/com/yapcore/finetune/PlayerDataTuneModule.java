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
                "features.homes|warps|kits|mail|shops|jobs|auctions|traders|backpack",
                "backpack.default-pages / max-pages — extra bag (/bag)",
                "auth.*                   — offline /login",
                "sync.inventory|xp|vitals|economy",
                "",
                "Land claims: YaPClaims (yap-claims.jar) — not this plugin",
                "Docs: docs/data/PLAYERDATA.md · docs/data/YAPDB.md",
                "No-econ network: economy.enabled=false"
        );
    }
}
