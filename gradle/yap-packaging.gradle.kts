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
        project.file("LICENSE").copyTo(outDir.resolve("LICENSE"), overwrite = true)
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
    description = "Zip gameplay plugins + modules + packs → build/dist/yap-gameplay-suite.zip"
    dependsOn(
        "installGameplayDefaults",
        "prepareClientPack",
        ":vehicles-plugin:jar",
        ":stacker-plugin:jar",
        ":gameplay-knobs-plugin:jar",
        ":skills-plugin:shadowJar",
        ":combat-plugin:shadowJar",
        ":crafting-plugin:shadowJar",
        ":mmo-content-plugin:shadowJar",
        ":mmo-bedrock-plugin:jar",
        ":guilds-plugin:shadowJar",
        ":games-plugin:shadowJar",
        ":mechanics-plugin:jar",
        ":abilities-plugin:shadowJar",
        ":disasters-plugin:jar",
        ":vehicles-module:jar",
        ":games-module:jar",
        ":games-ffa-module:jar",
        ":games-duels-module:jar",
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
            jarOf(":skills-plugin", "shadowJar") to "yap-skills.jar",
            jarOf(":combat-plugin", "shadowJar") to "yap-combat.jar",
            jarOf(":crafting-plugin", "shadowJar") to "yap-crafting.jar",
            jarOf(":mmo-content-plugin", "shadowJar") to "yap-mmo-content.jar",
            jarOf(":mmo-bedrock-plugin") to "yap-mmo-bedrock.jar",
            jarOf(":guilds-plugin", "shadowJar") to "yap-guilds.jar",
            jarOf(":games-plugin", "shadowJar") to "yap-games.jar",
            jarOf(":mechanics-plugin") to "yap-mechanics.jar",
            jarOf(":abilities-plugin", "shadowJar") to "yap-abilities.jar",
            jarOf(":disasters-plugin") to "yap-disasters.jar",
        ).forEach { (src, name) -> src.copyTo(plugins.resolve(name), overwrite = true) }
        jarOf(":vehicles-module").copyTo(modules.resolve("yap-vehicles-module.jar"), overwrite = true)
        jarOf(":games-module").copyTo(modules.resolve(jarOf(":games-module").name), overwrite = true)
        jarOf(":games-ffa-module").copyTo(modules.resolve(jarOf(":games-ffa-module").name), overwrite = true)
        jarOf(":games-duels-module").copyTo(modules.resolve(jarOf(":games-duels-module").name), overwrite = true)
        project.project(":finetune-modules").tasks.withType(Jar::class.java).forEach { jarTask ->
            if (!jarTask.enabled || jarTask.name == "jar") return@forEach
            val f = jarTask.archiveFile.get().asFile
            if (f.isFile && (f.name.contains("stacker") || f.name.contains("gameplay-knobs"))) {
                f.copyTo(modules.resolve(f.name), overwrite = true)
            }
        }
        val vehPack = project.file("resourcepacks/yap-vehicles.zip")
        if (vehPack.isFile) vehPack.copyTo(packs.resolve("yap-vehicles.zip"), overwrite = true)
        val abilPack = project.file("resourcepacks/yap-abilities.zip")
        if (abilPack.isFile) abilPack.copyTo(packs.resolve("yap-abilities.zip"), overwrite = true)
        outDir.resolve("README.txt").writeText(
            """
            YaPcore GAMEPLAY suite (v1.0.0.0)
            =================================
            Drop plugins/ and modules/ into your YaPcore server tree.
            Requires CORE+NETWORK release (yapcore.jar + yap-db + playerdata).
            Docs: docs/plugins/VEHICLES.md · docs/plugins/STACKER.md · docs/ops/TUNE.md
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
            Docs: docs/plugins/VEHICLES.md · examples/yap-vehicle-addon/README.md
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

/**
 * Copy full release trees + zips into a durable top-level folder:
 *   releases/<version>/
 *     linux/  windows/
 *     yapcore-release-linux.zip  yapcore-release-windows.zip
 *     yap-network-suite.zip  yap-gameplay-suite.zip  yap-addons-release.zip
 */
tasks.register("publishReleasesFolder") {
    group = "distribution"
    description =
        "Publish full release packages into releases/<version>/ (trees + zip archives)"
    dependsOn("assembleAllReleases")

    val ver = project.version.toString()
    val destRoot = project.layout.projectDirectory.dir("releases/$ver")
    val distDir = layout.buildDirectory.dir("dist")

    doLast {
        val dest = destRoot.asFile
        if (dest.exists()) dest.deleteRecursively()
        dest.mkdirs()

        val dist = distDir.get().asFile
        val box = dist.resolve("yapcore-release")
        require(box.resolve("linux").isDirectory) {
            "Missing ${box.resolve("linux")} — assembleRelease failed?"
        }
        require(box.resolve("windows").isDirectory) {
            "Missing ${box.resolve("windows")} — assembleRelease failed?"
        }

        project.copy {
            from(box.resolve("linux"))
            into(dest.resolve("linux"))
        }
        project.copy {
            from(box.resolve("windows"))
            into(dest.resolve("windows"))
        }
        box.resolve("README.txt").takeIf { it.isFile }?.copyTo(
            dest.resolve("README-box.txt"),
            overwrite = true,
        )

        // Match CI / QUICK_START layout: yapcore-release/{linux,windows}/…
        val staging = dest.resolve("yapcore-release")
        staging.mkdirs()
        dest.resolve("linux").renameTo(staging.resolve("linux"))
        dest.resolve("windows").renameTo(staging.resolve("windows"))

        val linuxZip = dest.resolve("yapcore-release-linux.zip")
        val windowsZip = dest.resolve("yapcore-release-windows.zip")
        // Ant's plain basedir zip stores Unix entries as 0644 and drops +x — break ./gui.sh → start.sh.
        // Split zipfilesets: scripts + yapctl → 0755; everything else → 0644.
        fun zipLinuxTree(zipFile: File, includesPrefix: String) {
            project.ant.withGroovyBuilder {
                "zip"("destfile" to zipFile) {
                    "zipfileset"(
                        "dir" to dest,
                        "includes" to
                            "$includesPrefix/**/*.sh,$includesPrefix/**/yapctl",
                        "filemode" to "755",
                        "dirmode" to "755",
                    )
                    "zipfileset"(
                        "dir" to dest,
                        "includes" to "$includesPrefix/**",
                        "excludes" to
                            "$includesPrefix/**/*.sh,$includesPrefix/**/yapctl",
                        "filemode" to "644",
                        "dirmode" to "755",
                    )
                }
            }
        }
        zipLinuxTree(linuxZip, "yapcore-release/linux")
        // Windows tree has no +x requirement; keep simple zip.
        project.ant.withGroovyBuilder {
            "zip"(
                "destfile" to windowsZip,
                "basedir" to dest,
                "includes" to "yapcore-release/windows/**",
            )
        }

        listOf(
            "yap-network-suite.zip",
            "yap-gameplay-suite.zip",
            "yap-addons-release.zip",
        ).forEach { name ->
            val src = dist.resolve(name)
            if (src.isFile) {
                src.copyTo(dest.resolve(name), overwrite = true)
            } else {
                logger.warn("Suite zip missing (skipped): $src")
            }
        }

        dest.resolve("README.txt").writeText(
            """
            YaPcore $ver — release folder
            =============================

            Full server trees (self-contained):
              yapcore-release/linux/     → ./start.sh --fg
              yapcore-release/windows/   → start.cmd -Fg

            Zip archives (same trees; unzip then cd yapcore-release/linux):
              yapcore-release-linux.zip
              yapcore-release-windows.zip

            Standalone suites:
              yap-network-suite.zip
              yap-gameplay-suite.zip
              yap-addons-release.zip

            Rebuild:  gradle publishReleasesFolder
            Slim CORE+NETWORK only:  gradle assembleRelease -PyapGameplay=false

            Docs: docs/start/RELEASES.md · docs/start/QUICK_START.md
            """.trimIndent() + "\n"
        )

        logger.lifecycle("Release folder → ${dest.absolutePath}")
        dest.walkTopDown().maxDepth(2).sortedBy { it.path }.forEach { f ->
            if (f == dest) return@forEach
            val rel = f.relativeTo(dest).path
            if (f.isFile) {
                logger.lifecycle("  $rel (${f.length() / (1024 * 1024)} MiB)")
            } else if (f.isDirectory && f.parentFile == dest) {
                logger.lifecycle("  $rel/")
            }
        }
    }
}
