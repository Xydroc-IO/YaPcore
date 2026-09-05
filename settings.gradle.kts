plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "YaPcore"

// First-party product sources — see yap-first-party/README.md
// Gradle project names stay stable; only projectDir paths moved under yap-first-party/.

include("bench-plugin")
project(":bench-plugin").projectDir = file("yap-first-party/dev/bench-plugin")

include("gameplay-knobs-plugin")
project(":gameplay-knobs-plugin").projectDir = file("yap-first-party/gameplay/gameplay-knobs-plugin")


include("stacker-plugin")
project(":stacker-plugin").projectDir = file("yap-first-party/gameplay/stacker-plugin")

include("placeholderapi-plugin")
project(":placeholderapi-plugin").projectDir = file("yap-first-party/core-network/placeholderapi-plugin")

include("plugin-compat-plugin")
project(":plugin-compat-plugin").projectDir = file("yap-first-party/core-network/plugin-compat-plugin")

include("pregen-plugin")
project(":pregen-plugin").projectDir = file("yap-first-party/core-network/pregen-plugin")

include("playerdata-plugin")
project(":playerdata-plugin").projectDir = file("yap-first-party/core-network/playerdata-plugin")

include("yap-db-api")
project(":yap-db-api").projectDir = file("yap-first-party/api/yap-db-api")

include("yap-db-plugin")
project(":yap-db-plugin").projectDir = file("yap-first-party/core-network/yap-db-plugin")

include("packs-plugin")
project(":packs-plugin").projectDir = file("yap-first-party/core-network/packs-plugin")

include("commands-plugin")
project(":commands-plugin").projectDir = file("yap-first-party/core-network/commands-plugin")

include("chat-plugin")
project(":chat-plugin").projectDir = file("yap-first-party/core-network/chat-plugin")

include("floodgate-plugin")
project(":floodgate-plugin").projectDir = file("yap-first-party/core-network/floodgate-plugin")

include("folia-bridge-plugin")
project(":folia-bridge-plugin").projectDir = file("yap-first-party/core-network/folia-bridge-plugin")

include("yap-perms-api")
project(":yap-perms-api").projectDir = file("yap-first-party/api/yap-perms-api")

include("yap-perms-plugin")
project(":yap-perms-plugin").projectDir = file("yap-first-party/core-network/yap-perms-plugin")

include("yap-moderation-api")
project(":yap-moderation-api").projectDir = file("yap-first-party/api/yap-moderation-api")

include("moderation-plugin")
project(":moderation-plugin").projectDir = file("yap-first-party/core-network/moderation-plugin")

include("essentials-plugin")
project(":essentials-plugin").projectDir = file("yap-first-party/core-network/essentials-plugin")

include("disasters-plugin")
project(":disasters-plugin").projectDir = file("yap-first-party/gameplay/disasters-plugin")

include("admin-plugin")
project(":admin-plugin").projectDir = file("yap-first-party/core-network/admin-plugin")

include("yap-chat-api")
project(":yap-chat-api").projectDir = file("yap-first-party/api/yap-chat-api")

include("yap-playerdata-api")
project(":yap-playerdata-api").projectDir = file("yap-first-party/api/yap-playerdata-api")

include("yap-regions-api")
project(":yap-regions-api").projectDir = file("yap-first-party/api/yap-regions-api")

include("yap-protect-api")
project(":yap-protect-api").projectDir = file("yap-first-party/api/yap-protect-api")

include("protect-plugin")
project(":protect-plugin").projectDir = file("yap-first-party/core-network/protect-plugin")

include("yap-world-api")
project(":yap-world-api").projectDir = file("yap-first-party/api/yap-world-api")

include("yap-worldedit-compat")
project(":yap-worldedit-compat").projectDir = file("yap-first-party/api/yap-worldedit-compat")

include("regions-plugin")
project(":regions-plugin").projectDir = file("yap-first-party/core-network/regions-plugin")

include("yap-npcs-api")
project(":yap-npcs-api").projectDir = file("yap-first-party/api/yap-npcs-api")

include("npcs-plugin")
project(":npcs-plugin").projectDir = file("yap-first-party/core-network/npcs-plugin")

include("world-plugin")
project(":world-plugin").projectDir = file("yap-first-party/core-network/world-plugin")

include("worldedit-shim-plugin")
project(":worldedit-shim-plugin").projectDir = file("yap-first-party/core-network/worldedit-shim-plugin")

include("yap-tab-api")
project(":yap-tab-api").projectDir = file("yap-first-party/api/yap-tab-api")

include("tab-plugin")
project(":tab-plugin").projectDir = file("yap-first-party/core-network/tab-plugin")

include("discord-plugin")
project(":discord-plugin").projectDir = file("yap-first-party/core-network/discord-plugin")

include("yap-sched")
project(":yap-sched").projectDir = file("yap-first-party/engine/yap-sched")

include("yap-sched-agent")
project(":yap-sched-agent").projectDir = file("yap-first-party/engine/yap-sched-agent")

include("yap-guard-api")
project(":yap-guard-api").projectDir = file("yap-first-party/api/yap-guard-api")

include("guard-plugin")
project(":guard-plugin").projectDir = file("yap-first-party/core-network/guard-plugin")

include("yap-lagguard-api")
project(":yap-lagguard-api").projectDir = file("yap-first-party/api/yap-lagguard-api")

include("lagguard-plugin")
project(":lagguard-plugin").projectDir = file("yap-first-party/core-network/lagguard-plugin")

include("map-plugin")
project(":map-plugin").projectDir = file("yap-first-party/core-network/map-plugin")

// Native YaP Link (Velocity-class proxy, phased)
include("yap-protocol")
project(":yap-protocol").projectDir = file("yap-first-party/link/protocol")

include("yap-link-api")
project(":yap-link-api").projectDir = file("yap-first-party/link/api")

include("yap-link-native")
project(":yap-link-native").projectDir = file("yap-first-party/link/native")

include("yap-link-plugin-chat-bridge")
project(":yap-link-plugin-chat-bridge").projectDir = file("yap-first-party/link/plugins/chat-bridge")

include("yap-link-plugin-mod-sync")
project(":yap-link-plugin-mod-sync").projectDir = file("yap-first-party/link/plugins/mod-sync")

include("yap-link-plugin-server-selector")
project(":yap-link-plugin-server-selector").projectDir = file("yap-first-party/link/plugins/server-selector")

include("yap-link-plugin-tab-bridge")
project(":yap-link-plugin-tab-bridge").projectDir = file("yap-first-party/link/plugins/tab-bridge")

include("yap-link-plugin-discord")
project(":yap-link-plugin-discord").projectDir = file("yap-first-party/link/plugins/discord")

// Deprecated Velocity fork removed — native Link only (yap-first-party/link/)
include("finetune-modules")
project(":finetune-modules").projectDir = file("yap-first-party/modules/finetune-modules")


include("yap-mmo-api")
project(":yap-mmo-api").projectDir = file("yap-first-party/api/yap-mmo-api")


include("yap-bedrock-ui-api")
project(":yap-bedrock-ui-api").projectDir = file("yap-first-party/api/yap-bedrock-ui-api")

include("bedrock-ui-plugin")
project(":bedrock-ui-plugin").projectDir = file("yap-first-party/core-network/bedrock-ui-plugin")


include("skills-plugin")
project(":skills-plugin").projectDir = file("yap-first-party/gameplay/skills-plugin")


include("yap-factions-api")
project(":yap-factions-api").projectDir = file("yap-first-party/api/yap-factions-api")

include("factions-plugin")
project(":factions-plugin").projectDir = file("yap-first-party/core-network/factions-plugin")


