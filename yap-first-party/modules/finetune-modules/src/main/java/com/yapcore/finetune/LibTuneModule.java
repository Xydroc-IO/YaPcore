package com.yapcore.finetune;

import java.util.List;

public final class LibTuneModule extends FineTuneModule {
    @Override
    protected String requiredPaperPlugin() {
        return "YaPLib";
    }

    @Override
    protected String guideTitle() {
        return "YaP Lib fine-tune";
    }

    @Override
    protected List<String> guideLines() {
        return List.of(
                "Config: plugins/YaPLib/config.yml",
                "Folia-safe packet intercept (ProtocolLib-class)",
                "API: PacketService via ServicesManager / PacketServices",
                "Play.Client = from player (serverbound); Play.Server = to player",
                "REGION cancel holds the packet (MONITOR region is observe-only)",
                "Handshake/login REGION uses the global scheduler + event.address()",
                "Holograms are a separate plugin: yap-holo.jar",
                "Docs: docs/plugins/YAPLIB.md"
        );
    }
}
