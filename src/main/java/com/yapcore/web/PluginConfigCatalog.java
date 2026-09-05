package com.yapcore.web;

import java.util.List;

/** First-party plugins that get a dashboard YAML editor. */
public final class PluginConfigCatalog {

    public record Entry(String id, String title, String dataDir, String file, String jarToken, String reload) {
    }

    private PluginConfigCatalog() {
    }

    public static List<Entry> all() {
        return List.of(
                e("yap-perms", "YaPPerms", "YaPPerms", "config.yml", "yap-perms", "yapperm reload"),
                e("yap-playerdata", "YaPPlayerData", "YaPPlayerData", "config.yml", "yap-playerdata", "yapdata reload"),
                e("yap-moderation", "YaPModeration", "YaPModeration", "config.yml", "yap-moderation", "yapmod reload"),
                e("yap-essentials", "YaPEssentials", "YaPEssentials", "config.yml", "yap-essentials", "yapess reload"),
                e("yap-admin", "YaPAdmin", "YaPAdmin", "config.yml", "yap-admin", ""),
                e("yap-protect", "YaPProtect", "YaPProtect", "config.yml", "yap-protect", "yapprotect reload"),
                e("yap-world", "YaPWorld", "YaPWorld", "config.yml", "yap-world", "yapworld reload"),
                e("yap-packs", "YaPPacks", "YaPPacks", "config.yml", "yap-packs", "yappacks reload"),
                e("yap-commands", "YaPCommands", "YaPCommands", "config.yml", "yap-commands", "yapcommands reload"),
                e("yap-chat", "YaPChat", "YaPChat", "config.yml", "yap-chat", "yapchat reload"),
                e("yap-tab", "YaPTab", "YaPTab", "config.yml", "yap-tab", "yaptab reload"),
                e("yap-discord", "YaPDiscord", "YaPDiscord", "config.yml", "yap-discord", "yapdiscord reload"),
                e("tebex", "Tebex store", "Tebex", "config.yml", "tebex", "tebex reload"),
                e("yap-floodgate", "YaPFloodgate", "YaPFloodgate", "config.yml", "yap-floodgate", "yapfloodgate reload"),
                e("yap-bedrock-ui", "YaPBedrockUI", "YaPBedrockUI", "config.yml", "yap-bedrock-ui", ""),
                e("yap-folia-bridge", "YaPFoliaBridge", "YaPFoliaBridge", "config.yml", "yap-folia-bridge", ""),
                e("yap-regions", "YaPRegions", "YaPRegions", "config.yml", "yap-regions", "region reload"),
                e("yap-npcs", "YaPNpcs", "YaPNpcs", "config.yml", "yap-npcs", "npc reload"),
                e("yap-guard", "YaPGuard", "YaPGuard", "config.yml", "yap-guard", "yapguard reload"),
                e("yap-lagguard", "YaPLagGuard", "YaPLagGuard", "config.yml", "yap-lagguard", "yaplagguard reload"),
                e("yap-map", "YaPMap", "YaPMap", "config.yml", "yap-map", "yapmap reload"),
                e("yap-factions", "YaPFactions", "YaPFactions", "config.yml", "yap-factions", "yapfactions reload"),
                e("yap-db", "YaPDB", "YaPDB", "config.yml", "yap-db", "yapdb reload"),
                e("yap-pregen", "YaPPregen", "YaPPregen", "config.yml", "yap-pregen", ""),
                e("yap-placeholderapi", "PlaceholderAPI", "PlaceholderAPI", "config.yml", "placeholderapi", "placeholderapi reload"),
                e("yap-plugin-compat", "YaPPluginCompat", "YaPPluginCompat", "config.yml", "yap-plugin-compat", ""),
                e("yap-stacker", "YaPStacker", "YaPStacker", "config.yml", "yap-stacker", "yapstacker reload"),
                e("yap-gameplay-knobs", "YaPGameplayKnobs", "YaPGameplayKnobs", "knobs.yml", "yap-gameplay-knobs", "yapknobs reload"),
                e("yap-skills", "YaPSkills", "YaPSkills", "config.yml", "yap-skills", "yskills reload"),
                e("yap-disasters", "YaPDisasters", "YaPDisasters", "config.yml", "yap-disasters", "yapdisaster reload")
        );
    }

    public static Entry byId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String key = id.trim().toLowerCase();
        for (Entry e : all()) {
            if (e.id().equals(key)) {
                return e;
            }
        }
        return null;
    }

    private static Entry e(String id, String title, String dataDir, String file, String jar, String reload) {
        return new Entry(id, title, dataDir, file, jar, reload);
    }
}
