package com.yapcore.finetune;

import java.util.List;

public final class DiscordTuneModule extends FineTuneModule {
    @Override
    protected String requiredPaperPlugin() {
        return "YaPDiscord";
    }

    @Override
    protected String guideTitle() {
        return "YaP Discord fine-tune";
    }

    @Override
    protected List<String> guideLines() {
        return List.of(
                "Config: plugins/YaPDiscord/config.yml",
                "MC→Discord: relay.mc-to-discord + webhooks.chat",
                "Discord→MC: relay.discord-to-mc + inbound HTTP (port/secret)",
                "Proxy mod-log: yap-link-plugin-discord + webhooks.moderation",
                "Commands: /yapdiscord reload|test [moderation|chat]|say",
                "Dashboard: Discord panel — webhooks, relay toggles, test buttons"
        );
    }
}
