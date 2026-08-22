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
    if (findProject(":map-plugin") != null) {
        dependsOn(":map-plugin:installIntoPlugins")
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
    description = "Install all fine-tune packaging modules into modules/ (incl. vehicles)"
    dependsOn(
        ":finetune-modules:installIntoModules",
        ":vehicles-module:installIntoModules",
    )
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
        ":map-plugin:shadowJar",
        ":vehicles-plugin:jar",
        ":vehicles-module:jar",
        ":gameplay-knobs-plugin:jar",
        ":stacker-plugin:jar",
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
        if (findProject(":map-plugin") != null) {
            copyNamed(jarOf(":map-plugin", "shadowJar"), coreDir)
        }

        copyNamed(jarOf(":vehicles-plugin"), gameplayDir)
        copyNamed(jarOf(":gameplay-knobs-plugin"), gameplayDir)
        copyNamed(jarOf(":stacker-plugin"), gameplayDir)

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
        if (findProject(":yap-tab-api") != null) {
            copyNamed(jarOf(":yap-tab-api"), apiDir)
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
            modules/gameplay/ vehicles + stacker + knobs modules
            api/              yap-*-api jars

            Rebuild:  gradle assemblePluginDist
                      gradle installFineTuneModules
            Full box: gradle assembleRelease
            Full + gameplay: gradle assembleRelease -PyapGameplay=true

            Docs: docs/MODULES_AND_API.md · docs/TUNE.md · plugins/README.md
            """.trimIndent() + "\n"
        )

        println("Plugin dist → ${dest.absolutePath}")
        dest.walkTopDown().filter { it.isFile }.sortedBy { it.path }.forEach {
            println("  ${it.relativeTo(dest)}")
        }
    }
}

