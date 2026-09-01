// ---------------------------------------------------------------------------
// Product defaults — CORE+NETWORK (default) vs GAMEPLAY (opt-in)
// ---------------------------------------------------------------------------

import org.gradle.api.tasks.bundling.Jar

val yapGameplayProp: Provider<String> = providers.gradleProperty("yapGameplay").orElse("false")
val yapGameplayEnabled: Boolean =
    yapGameplayProp.get() == "true" || yapGameplayProp.get() == "1"

tasks.register("installProductDefaults") {
    group = "distribution"
    description =
        "CORE+NETWORK plugins + CORE fine-tune modules"
    dependsOn(
        ":placeholderapi-plugin:installIntoPlugins",
        ":plugin-compat-plugin:installIntoPlugins",
        ":finetune-modules:installCoreIntoModules",
    )
    if (findProject(":pregen-plugin") != null) {
        dependsOn(":pregen-plugin:installIntoPlugins")
    }
    if (findProject(":yap-db-plugin") != null) {
        dependsOn(":yap-db-plugin:installIntoPlugins")
    }
    if (findProject(":yap-perms-plugin") != null) {
        dependsOn(":yap-perms-plugin:installIntoPlugins")
    }
    if (findProject(":playerdata-plugin") != null) {
        dependsOn(":playerdata-plugin:installIntoPlugins")
    }
    if (findProject(":moderation-plugin") != null) {
        dependsOn(":moderation-plugin:installIntoPlugins")
    }
    if (findProject(":essentials-plugin") != null) {
        dependsOn(":essentials-plugin:installIntoPlugins")
    }
    if (findProject(":admin-plugin") != null) {
        dependsOn(":admin-plugin:installIntoPlugins")
    }
    if (findProject(":protect-plugin") != null) {
        dependsOn(":protect-plugin:installIntoPlugins")
    }
    if (findProject(":world-plugin") != null) {
        dependsOn(":world-plugin:installIntoPlugins")
    }
    if (findProject(":packs-plugin") != null) {
        dependsOn(":packs-plugin:installIntoPlugins")
    }
    if (findProject(":chat-plugin") != null) {
        dependsOn(":chat-plugin:installIntoPlugins")
    }
    if (findProject(":tab-plugin") != null) {
        dependsOn(":tab-plugin:installIntoPlugins")
    }
    if (findProject(":discord-plugin") != null) {
        dependsOn(":discord-plugin:installIntoPlugins")
    }
    if (findProject(":floodgate-plugin") != null) {
        dependsOn(":floodgate-plugin:installIntoPlugins")
    }
    if (findProject(":bedrock-ui-plugin") != null) {
        dependsOn(":bedrock-ui-plugin:installIntoPlugins")
    }
    if (findProject(":folia-bridge-plugin") != null) {
        dependsOn(":folia-bridge-plugin:installIntoPlugins")
    }
    if (findProject(":regions-plugin") != null) {
        dependsOn(":regions-plugin:installIntoPlugins")
    }
    if (findProject(":npcs-plugin") != null) {
        dependsOn(":npcs-plugin:installIntoPlugins")
    }
    if (findProject(":guard-plugin") != null) {
        dependsOn(":guard-plugin:installIntoPlugins")
    }
    if (findProject(":lagguard-plugin") != null) {
        dependsOn(":lagguard-plugin:installIntoPlugins")
    }
    if (findProject(":map-plugin") != null) {
        dependsOn(":map-plugin:installIntoPlugins")
    }
    if (findProject(":factions-plugin") != null) {
        dependsOn(":factions-plugin:installIntoPlugins")
    }
}

tasks.register("installGameplayDefaults") {
    group = "distribution"
    description = "GAMEPLAY opt-in: Vehicles + Stacker + GameplayKnobs (+ fine-tune modules)"
    dependsOn(
        ":vehicles-plugin:installIntoPlugins",
        ":vehicles-module:installIntoModules",
        ":gameplay-knobs-plugin:installIntoPlugins",
        ":finetune-modules:installGameplayIntoModules",
    )
    if (findProject(":skills-plugin") != null) {
        dependsOn(":skills-plugin:installIntoPlugins")
    }
    if (findProject(":combat-plugin") != null) {
        dependsOn(":combat-plugin:installIntoPlugins")
    }
    if (findProject(":crafting-plugin") != null) {
        dependsOn(":crafting-plugin:installIntoPlugins")
    }
    if (findProject(":mmo-content-plugin") != null) {
        dependsOn(":mmo-content-plugin:installIntoPlugins")
    }
    if (findProject(":games-plugin") != null) {
        dependsOn(":games-plugin:installIntoPlugins")
    }
    if (findProject(":games-module") != null) {
        dependsOn(":games-module:installIntoModules")
    }
    if (findProject(":games-ffa-module") != null) {
        dependsOn(":games-ffa-module:installIntoModules")
    }
    if (findProject(":games-duels-module") != null) {
        dependsOn(":games-duels-module:installIntoModules")
    }
    if (findProject(":floodgate-plugin") != null) {
        dependsOn(":floodgate-plugin:installIntoPlugins")
    }
    if (findProject(":bedrock-ui-plugin") != null) {
        dependsOn(":bedrock-ui-plugin:installIntoPlugins")
    }
    if (findProject(":mmo-bedrock-plugin") != null) {
        dependsOn(":mmo-bedrock-plugin:installIntoPlugins")
    }
    if (findProject(":guilds-plugin") != null) {
        dependsOn(":guilds-plugin:installIntoPlugins")
    }
    if (findProject(":mechanics-plugin") != null) {
        dependsOn(":mechanics-plugin:installIntoPlugins")
    }
    if (findProject(":abilities-plugin") != null) {
        dependsOn(":abilities-plugin:installIntoPlugins")
    }
    if (findProject(":stacker-plugin") != null) {
        dependsOn(":stacker-plugin:installIntoPlugins")
    }
}

tasks.register("installAllProductDefaults") {
    group = "distribution"
    description = "CORE+NETWORK + GAMEPLAY jars + all fine-tune modules"
    dependsOn("installProductDefaults", "installGameplayDefaults")
}

tasks.register("installFineTuneModules") {
    group = "distribution"
    description = "Install all fine-tune packaging modules into modules/ (incl. vehicles + games)"
    dependsOn(
        ":finetune-modules:installIntoModules",
        ":vehicles-module:installIntoModules",
    )
    if (findProject(":games-module") != null) {
        dependsOn(":games-module:installIntoModules")
    }
    if (findProject(":games-ffa-module") != null) {
        dependsOn(":games-ffa-module:installIntoModules")
    }
    if (findProject(":games-duels-module") != null) {
        dependsOn(":games-duels-module:installIntoModules")
    }
}
/** Flat folder of every first-party product plugin jar for distribution / mirrors. */
tasks.register("assemblePluginDist") {
    group = "distribution"
    description =
        "Copy all YaP plugin + fine-tune module jars into build/dist/yap-plugins/"

    dependsOn(
        ":placeholderapi-plugin:shadowJar",
        ":plugin-compat-plugin:jar",
        ":pregen-plugin:jar",
        ":yap-db-plugin:shadowJar",
        ":yap-perms-plugin:shadowJar",
        ":playerdata-plugin:shadowJar",
        ":moderation-plugin:shadowJar",
        ":essentials-plugin:shadowJar",
        ":admin-plugin:jar",
        ":protect-plugin:shadowJar",
        ":world-plugin:shadowJar",
        ":packs-plugin:jar",
        ":chat-plugin:jar",
        ":tab-plugin:jar",
        ":discord-plugin:jar",
        ":floodgate-plugin:jar",
        ":folia-bridge-plugin:jar",
        ":regions-plugin:shadowJar",
        ":npcs-plugin:shadowJar",
        ":guard-plugin:shadowJar",
        ":lagguard-plugin:shadowJar",
        ":map-plugin:shadowJar",
        ":factions-plugin:shadowJar",
        ":vehicles-plugin:jar",
        ":vehicles-module:jar",
        ":gameplay-knobs-plugin:jar",
        ":stacker-plugin:jar",
        ":yap-mmo-api:jar",
        ":skills-plugin:shadowJar",
        ":combat-plugin:shadowJar",
        ":crafting-plugin:shadowJar",
        ":mmo-content-plugin:shadowJar",
        ":guilds-plugin:shadowJar",
        ":bedrock-ui-plugin:jar",
        ":mmo-bedrock-plugin:jar",
        ":games-plugin:shadowJar",
        ":mechanics-plugin:jar",
        ":yap-mechanics-api:jar",
        ":yap-abilities-api:jar",
        ":abilities-plugin:shadowJar",
        ":yap-games-api:jar",
        ":yap-bedrock-ui-api:jar",
        ":yap-db-api:jar",
        ":yap-perms-api:jar",
        ":yap-moderation-api:jar",
        ":yap-chat-api:jar",
        ":yap-playerdata-api:jar",
        ":yap-protect-api:jar",
        ":yap-world-api:jar",
        ":yap-regions-api:jar",
        ":yap-npcs-api:jar",
        ":yap-tab-api:jar",
        ":finetune-modules:buildAllFineTuneModules",
        ":games-module:jar",
        ":games-ffa-module:jar",
        ":games-duels-module:jar",
    )

    val outDir = layout.buildDirectory.dir("dist/yap-plugins")

    doLast {
        val dest = outDir.get().asFile
        if (dest.exists()) {
            dest.deleteRecursively()
        }
        val coreDir = dest.resolve("core-network")
        val gameplayDir = dest.resolve("gameplay")
        val apiDir = dest.resolve("api")
        val modulesCore = dest.resolve("modules/core")
        val modulesGameplay = dest.resolve("modules/gameplay")
        listOf(coreDir, gameplayDir, apiDir, modulesCore, modulesGameplay).forEach { it.mkdirs() }

        fun jarOf(path: String, taskName: String = "jar"): File {
            return project.project(path).tasks.named(taskName, Jar::class.java).get().archiveFile.get().asFile
        }

        fun copyNamed(from: File, into: File, asName: String = from.name) {
            require(from.isFile) { "Missing jar for plugin dist: $from" }
            from.copyTo(into.resolve(asName), overwrite = true)
        }

        copyNamed(jarOf(":placeholderapi-plugin", "shadowJar"), coreDir)
        copyNamed(jarOf(":plugin-compat-plugin"), coreDir)
        copyNamed(jarOf(":pregen-plugin"), coreDir)
        copyNamed(jarOf(":yap-db-plugin", "shadowJar"), coreDir)
        copyNamed(jarOf(":yap-perms-plugin", "shadowJar"), coreDir)
        copyNamed(jarOf(":playerdata-plugin", "shadowJar"), coreDir)
        copyNamed(jarOf(":moderation-plugin", "shadowJar"), coreDir)
        copyNamed(jarOf(":essentials-plugin", "shadowJar"), coreDir)
        if (findProject(":admin-plugin") != null) {
            copyNamed(jarOf(":admin-plugin"), coreDir)
        }
        copyNamed(jarOf(":protect-plugin", "shadowJar"), coreDir)
        copyNamed(jarOf(":world-plugin", "shadowJar"), coreDir)
        copyNamed(jarOf(":packs-plugin"), coreDir)
        copyNamed(jarOf(":chat-plugin"), coreDir)
        if (findProject(":tab-plugin") != null) {
            copyNamed(jarOf(":tab-plugin"), coreDir)
        }
        if (findProject(":discord-plugin") != null) {
            copyNamed(jarOf(":discord-plugin"), coreDir)
        }
        copyNamed(jarOf(":floodgate-plugin"), coreDir)
        if (findProject(":folia-bridge-plugin") != null) {
            copyNamed(jarOf(":folia-bridge-plugin"), coreDir)
        }
        if (findProject(":regions-plugin") != null) {
            copyNamed(jarOf(":regions-plugin", "shadowJar"), coreDir)
        }
        if (findProject(":npcs-plugin") != null) {
            copyNamed(jarOf(":npcs-plugin", "shadowJar"), coreDir)
        }
        if (findProject(":guard-plugin") != null) {
            copyNamed(jarOf(":guard-plugin", "shadowJar"), coreDir)
        }
        if (findProject(":lagguard-plugin") != null) {
            copyNamed(jarOf(":lagguard-plugin", "shadowJar"), coreDir)
        }
        if (findProject(":map-plugin") != null) {
            copyNamed(jarOf(":map-plugin", "shadowJar"), coreDir)
        }
        if (findProject(":factions-plugin") != null) {
            copyNamed(jarOf(":factions-plugin", "shadowJar"), coreDir)
        }

        copyNamed(jarOf(":vehicles-plugin"), gameplayDir)
        copyNamed(jarOf(":gameplay-knobs-plugin"), gameplayDir)
        copyNamed(jarOf(":stacker-plugin"), gameplayDir)
        if (findProject(":skills-plugin") != null) {
            copyNamed(jarOf(":skills-plugin", "shadowJar"), gameplayDir)
        }
        if (findProject(":combat-plugin") != null) {
            copyNamed(jarOf(":combat-plugin", "shadowJar"), gameplayDir)
        }
        if (findProject(":crafting-plugin") != null) {
            copyNamed(jarOf(":crafting-plugin", "shadowJar"), gameplayDir)
        }
        if (findProject(":mmo-content-plugin") != null) {
            copyNamed(jarOf(":mmo-content-plugin", "shadowJar"), gameplayDir)
        }
        if (findProject(":guilds-plugin") != null) {
            copyNamed(jarOf(":guilds-plugin", "shadowJar"), gameplayDir)
        }
        if (findProject(":games-plugin") != null) {
            copyNamed(jarOf(":games-plugin", "shadowJar"), gameplayDir)
        }
        if (findProject(":mechanics-plugin") != null) {
            copyNamed(jarOf(":mechanics-plugin"), gameplayDir)
        }
        if (findProject(":abilities-plugin") != null) {
            copyNamed(jarOf(":abilities-plugin", "shadowJar"), gameplayDir)
        }

        copyNamed(jarOf(":yap-db-api"), apiDir)
        copyNamed(jarOf(":yap-perms-api"), apiDir)
        copyNamed(jarOf(":yap-moderation-api"), apiDir)
        copyNamed(jarOf(":yap-chat-api"), apiDir)
        copyNamed(jarOf(":yap-playerdata-api"), apiDir)
        copyNamed(jarOf(":yap-protect-api"), apiDir)
        copyNamed(jarOf(":yap-world-api"), apiDir)
        copyNamed(jarOf(":yap-regions-api"), apiDir)
        copyNamed(jarOf(":yap-npcs-api"), apiDir)
        if (findProject(":yap-guard-api") != null) {
            copyNamed(jarOf(":yap-guard-api"), apiDir)
        }
        if (findProject(":yap-lagguard-api") != null) {
            copyNamed(jarOf(":yap-lagguard-api"), apiDir)
        }
        if (findProject(":yap-tab-api") != null) {
            copyNamed(jarOf(":yap-tab-api"), apiDir)
        }
        if (findProject(":yap-mmo-api") != null) {
            copyNamed(jarOf(":yap-mmo-api"), apiDir)
        }
        if (findProject(":yap-mechanics-api") != null) {
            copyNamed(jarOf(":yap-mechanics-api"), apiDir)
        }
        if (findProject(":yap-abilities-api") != null) {
            copyNamed(jarOf(":yap-abilities-api"), apiDir)
        }
        if (findProject(":yap-games-api") != null) {
            copyNamed(jarOf(":yap-games-api"), apiDir)
        }
        if (findProject(":yap-factions-api") != null) {
            copyNamed(jarOf(":yap-factions-api"), apiDir)
        }
        if (findProject(":yap-guilds-api") != null) {
            copyNamed(jarOf(":yap-guilds-api"), apiDir)
        }

        // Fine-tune modules (drop into server modules/)
        val ft = project.project(":finetune-modules")
        ft.tasks.withType(Jar::class.java).forEach { jarTask ->
            if (!jarTask.enabled || jarTask.name == "jar") {
                return@forEach
            }
            val f = jarTask.archiveFile.get().asFile
            if (!f.isFile) {
                return@forEach
            }
            val name = f.name
            val gameplayModule = name.contains("stacker") || name.contains("gameplay-knobs")
            copyNamed(f, if (gameplayModule) modulesGameplay else modulesCore)
        }
        copyNamed(jarOf(":vehicles-module"), modulesGameplay)
        if (findProject(":games-module") != null) {
            copyNamed(jarOf(":games-module"), modulesGameplay)
        }
        if (findProject(":games-ffa-module") != null) {
            copyNamed(jarOf(":games-ffa-module"), modulesGameplay)
        }
        if (findProject(":games-duels-module") != null) {
            copyNamed(jarOf(":games-duels-module"), modulesGameplay)
        }

        dest.resolve("README.txt").writeText(
            """
            YaPcore — first-party plugin + fine-tune module distribution
            ===========================================================

            plugins/  ← drop core-network/ and optionally gameplay/ jars
            modules/  ← drop modules/core/ (and modules/gameplay/ when using GAMEPLAY)

            Fine-tune modules do not replace configs — they declare provides/requires,
            verify the Paper plugin is present, and write FINE_TUNE.txt under
            modules/<Name>/ pointing at the real knobs.

            core-network/     CORE plugins
            gameplay/         GAMEPLAY plugins (opt-in)
            modules/core/     CORE fine-tune modules
            modules/gameplay/ vehicles + stacker + knobs + games modules
            api/              yap-*-api jars

            Rebuild:  gradle assemblePluginDist
                      gradle installFineTuneModules
            Full box: gradle assembleRelease
            Full + gameplay: gradle assembleRelease -PyapGameplay=true

            Docs: docs/plugins/MODULES_AND_API.md · docs/ops/TUNE.md · plugins/README.md
            """.trimIndent() + "\n"
        )

        println("Plugin dist → ${dest.absolutePath}")
        dest.walkTopDown().filter { it.isFile }.sortedBy { it.path }.forEach {
            println("  ${it.relativeTo(dest)}")
        }
    }
}

tasks.register<Exec>("fetchTebex") {
    group = "distribution"
    description =
        "Download official Tebex Folia plugin (GPLv3) into plugins/tebex.jar"
    workingDir = project.projectDir
    commandLine("bash", "scripts/fetch-tebex.sh")
}

tasks.register<Exec>("fetchGrim") {
    group = "distribution"
    description =
        "Download official Grim Anticheat Folia jar (GPLv3) into plugins/grim.jar"
    workingDir = project.projectDir
    commandLine("bash", "scripts/fetch-grim.sh")
}

