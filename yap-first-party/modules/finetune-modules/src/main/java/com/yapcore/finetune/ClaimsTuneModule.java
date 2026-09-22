package com.yapcore.finetune;

import java.util.List;

public final class ClaimsTuneModule extends FineTuneModule {
    @Override
    protected String requiredPaperPlugin() {
        return "YaPClaims";
    }

    @Override
    protected String guideTitle() {
        return "YaP Claims fine-tune";
    }

    @Override
    protected List<String> guideLines() {
        return List.of(
                "Config: plugins/YaPClaims/config.yml",
                "",
                "claims.enabled           — master land-claim switch",
                "claims.tool / inspect-tool — shovel + stick",
                "claims.starting-blocks / blocks-per-hour / min-area / max-area",
                "claims.deny-worlds       — e.g. resource mining worlds",
                "claims.tax.*             — needs YaPPlayerData economy",
                "claims.default-flags.*   — pvp, fire-spread, entry, …",
                "",
                "Command: /claim (via YaPEssentials)",
                "Docs: docs/plugins/PLUGINS.md · docs/data/PLAYERDATA.md (data plane)"
        );
    }
}
