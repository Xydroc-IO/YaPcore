import org.gradle.api.tasks.bundling.Jar

tasks.register("assembleNetworkSuite") {
    group = "distribution"
    description = "Zip yap-link.jar + link-data/plugins/*.jar → build/dist/yap-network-suite.zip"
    dependsOn(
        ":yap-link-native:shadowJar",
        ":yap-link-plugin-chat-bridge:installIntoLinkPlugins",
        ":yap-link-plugin-mod-sync:installIntoLinkPlugins",
        ":yap-link-plugin-server-selector:installIntoLinkPlugins",
        ":yap-link-plugin-tab-bridge:installIntoLinkPlugins",
        ":yap-link-plugin-discord:installIntoLinkPlugins",
    )
    doLast {
        val outDir = layout.buildDirectory.dir("dist/yap-network-suite").get().asFile
        outDir.deleteRecursively()
        outDir.mkdirs()
        val linkJar = project.file("yap-first-party/link/native/build/libs/yap-link.jar")
        require(linkJar.isFile) { "Missing $linkJar" }
        linkJar.copyTo(outDir.resolve("yap-link.jar"), overwrite = true)
        val plugins = project.file("link-data/plugins")
        if (plugins.isDirectory) {
            project.copy {
                from(plugins)
                into(outDir.resolve("plugins"))
                include("*.jar")
            }
        }
        project.copy {
            from(project.file("link-data"))
            into(outDir)
            include("link.properties.example", "link.toml.example")
        }
        val zip = layout.buildDirectory.file("dist/yap-network-suite.zip").get().asFile
        project.ant.withGroovyBuilder {
            "zip"(
                "destfile" to zip,
                "basedir" to outDir,
            )
        }
        logger.lifecycle("Network suite: ${zip.absolutePath}")
    }
}

tasks.register("verifyConcurrency") {
    group = "verification"
    description = "SpotBugs (sync packages) + unit tests + Fray interleaving tests"
    dependsOn("spotbugsMain", "test", "frayTest")
}

/** GAMEPLAY opt-in bundle (also shipped inside assembleRelease -PyapGameplay=true). */
tasks.register("assembleGameplaySuite") {
    group = "distribution"
    description = "Zip gameplay plugins + modules + yap-vehicles.zip → build/dist/yap-gameplay-suite.zip"
    dependsOn(
        "installGameplayDefaults",
        "prepareClientPack",
        ":vehicles-plugin:jar",
        ":stacker-plugin:jar",
        ":gameplay-knobs-plugin:jar",
        ":vehicles-module:jar",
        ":finetune-modules:buildAllFineTuneModules",
    )
    doLast {
        val outDir = layout.buildDirectory.dir("dist/yap-gameplay-suite").get().asFile
        outDir.deleteRecursively()
        outDir.mkdirs()
        val plugins = outDir.resolve("plugins").also { it.mkdirs() }
        val modules = outDir.resolve("modules").also { it.mkdirs() }
        val packs = outDir.resolve("resourcepacks").also { it.mkdirs() }
        fun jarOf(path: String, taskName: String = "jar"): java.io.File =
            project.project(path).tasks.named(taskName, Jar::class.java).get().archiveFile.get().asFile
        listOf(
            jarOf(":vehicles-plugin") to "yap-vehicles.jar",
            jarOf(":stacker-plugin") to "yap-stacker.jar",
            jarOf(":gameplay-knobs-plugin") to "yap-gameplay-knobs.jar",
        ).forEach { (src, name) -> src.copyTo(plugins.resolve(name), overwrite = true) }
        jarOf(":vehicles-module").copyTo(modules.resolve("yap-vehicles-module.jar"), overwrite = true)
        project.project(":finetune-modules").tasks.withType(Jar::class.java).forEach { jarTask ->
            if (!jarTask.enabled || jarTask.name == "jar") return@forEach
            val f = jarTask.archiveFile.get().asFile
            if (f.isFile && (f.name.contains("stacker") || f.name.contains("gameplay-knobs"))) {
                f.copyTo(modules.resolve(f.name), overwrite = true)
            }
        }
        val vehPack = project.file("resourcepacks/yap-vehicles.zip")
        if (vehPack.isFile) vehPack.copyTo(packs.resolve("yap-vehicles.zip"), overwrite = true)
        outDir.resolve("README.txt").writeText(
            """
            YaPcore GAMEPLAY suite (v1.0.0.0)
            =================================
            Drop plugins/ and modules/ into your YaPcore server tree.
            Requires CORE+NETWORK release (yapcore.jar + yap-db + playerdata).
            Docs: docs/VEHICLES.md · docs/STACKER.md · docs/TUNE.md
            Rebuild: gradle assembleGameplaySuite
            """.trimIndent() + "\n"
        )
        val zip = layout.buildDirectory.file("dist/yap-gameplay-suite.zip").get().asFile
        project.ant.withGroovyBuilder { "zip"("destfile" to zip, "basedir" to outDir) }
        logger.lifecycle("Gameplay suite: ${zip.absolutePath}")
    }
}

/** Example / third-party style addons shipped separately from the main box. */
tasks.register("assembleAddonsRelease") {
    group = "distribution"
    description = "Zip example yap-vehicle-addon jar → build/dist/yap-addons-release.zip"
    dependsOn(":yap-vehicle-addon:jar")
    doLast {
        val outDir = layout.buildDirectory.dir("dist/yap-addons-release").get().asFile
        outDir.deleteRecursively()
        outDir.mkdirs()
        val addonJar = project.project(":yap-vehicle-addon").tasks.named("jar", Jar::class.java).get().archiveFile.get().asFile
        addonJar.copyTo(outDir.resolve("plugins").also { it.mkdirs() }.resolve(addonJar.name), overwrite = true)
        project.copy {
            from(project.file("examples/yap-vehicle-addon"))
            into(outDir.resolve("examples/yap-vehicle-addon"))
            include("README.md", "src/**")
        }
        outDir.resolve("README.txt").writeText(
            """
            YaPcore add-ons (v1.0.0.0)
            ==========================
            plugins/     example vehicle addon jar (requires yap-vehicles)
            examples/    source + README for authors
            Docs: docs/VEHICLES.md · examples/yap-vehicle-addon/README.md
            Rebuild: gradle assembleAddonsRelease
            """.trimIndent() + "\n"
        )
        val zip = layout.buildDirectory.file("dist/yap-addons-release.zip").get().asFile
        project.ant.withGroovyBuilder { "zip"("destfile" to zip, "basedir" to outDir) }
        logger.lifecycle("Addons release: ${zip.absolutePath}")
    }
}

tasks.register("assembleAllReleases") {
    group = "distribution"
    description = "Full linux/windows box + network + gameplay + addons zips"
    dependsOn(
        "assembleRelease",
        "assembleNetworkSuite",
        "assembleGameplaySuite",
        "assembleAddonsRelease",
    )
}
