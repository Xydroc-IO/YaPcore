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
    description = "Zip gameplay plugins + modules → build/dist/yap-gameplay-suite.zip"
    dependsOn(
        "installGameplayDefaults",
        "prepareClientPack",
        ":stacker-plugin:jar",
        ":gameplay-knobs-plugin:jar",
        ":items-plugin:jar",
        ":skills-plugin:shadowJar",
        ":dungeons-plugin:shadowJar",
        ":disasters-plugin:jar",
        ":leveled-mobs-plugin:jar",
        ":finetune-modules:buildAllFineTuneModules",
    )
    doLast {
        val outDir = layout.buildDirectory.dir("dist/yap-gameplay-suite").get().asFile
        outDir.deleteRecursively()
        outDir.mkdirs()
        val plugins = outDir.resolve("plugins").also { it.mkdirs() }
        val modules = outDir.resolve("modules").also { it.mkdirs() }
        fun jarOf(path: String, taskName: String = "jar"): java.io.File =
            project.project(path).tasks.named(taskName, Jar::class.java).get().archiveFile.get().asFile
        listOf(
            jarOf(":stacker-plugin") to "yap-stacker.jar",
            jarOf(":gameplay-knobs-plugin") to "yap-gameplay-knobs.jar",
            jarOf(":items-plugin") to "yap-items.jar",
            jarOf(":skills-plugin", "shadowJar") to "yap-skills.jar",
            jarOf(":dungeons-plugin", "shadowJar") to "yap-dungeons.jar",
            jarOf(":disasters-plugin") to "yap-disasters.jar",
            jarOf(":leveled-mobs-plugin") to "yap-leveled-mobs.jar",
        ).forEach { (src, name) -> src.copyTo(plugins.resolve(name), overwrite = true) }
        project.project(":finetune-modules").tasks.withType(Jar::class.java).forEach { jarTask ->
            if (!jarTask.enabled || jarTask.name == "jar") return@forEach
            val f = jarTask.archiveFile.get().asFile
            if (f.isFile && (f.name.contains("stacker") || f.name.contains("gameplay-knobs"))) {
                f.copyTo(modules.resolve(f.name), overwrite = true)
            }
        }
        outDir.resolve("README.txt").writeText(
            """
            YaPcore GAMEPLAY suite (v1.0.0.0)
            =================================
            Drop plugins/ and modules/ into your YaPcore server tree.
            Requires CORE+NETWORK release (yapcore.jar + yap-db + playerdata).
            Docs: docs/plugins/STACKER.md · docs/plugins/YAPITEMS.md · docs/plugins/SKILLS.md · docs/plugins/DUNGEONS.md · docs/ops/TUNE.md
            Rebuild: gradle assembleGameplaySuite
            """.trimIndent() + "\n"
        )
        val zip = layout.buildDirectory.file("dist/yap-gameplay-suite.zip").get().asFile
        project.ant.withGroovyBuilder { "zip"("destfile" to zip, "basedir" to outDir) }
        logger.lifecycle("Gameplay suite: ${zip.absolutePath}")
    }
}

tasks.register("assembleAllReleases") {
    group = "distribution"
    description = "Full linux/windows box + network + gameplay zips"
    dependsOn(
        "assembleRelease",
        "assembleNetworkSuite",
        "assembleGameplaySuite",
    )
}

/**
 * Copy full release trees + zips into a durable top-level folder:
 *   releases/<version>/
 *     linux/  windows/
 *     yapcore-release-linux.zip  yapcore-release-windows.zip
 *     yap-network-suite.zip  yap-gameplay-suite.zip
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
        ).forEach { name ->
            val src = dist.resolve(name)
            if (src.isFile) {
                src.copyTo(dest.resolve(name), overwrite = true)
            } else {
                logger.warn("Suite zip missing (skipped): $src")
            }
        }

        // Phase 6: optional Fabric client_mods + default packs when pre-built
        val clientMods = project.layout.projectDirectory.file("dist/client-mods/client_mods.zip").asFile
        if (clientMods.isFile) {
            clientMods.copyTo(dest.resolve("client_mods.zip"), overwrite = true)
            logger.lifecycle("Copied client_mods.zip (${clientMods.length() / 1024} KiB)")
        } else {
            logger.warn("client_mods.zip missing — run ./scripts/build-yap-client-render.sh before upload")
        }
        val defaultPack = project.layout.projectDirectory.file("resourcepacks/yapcore-default.zip").asFile
        if (defaultPack.isFile) {
            defaultPack.copyTo(dest.resolve("yapcore-default.zip"), overwrite = true)
        }
        val defaultMcpack = project.layout.projectDirectory.file("resourcepacks/yapcore-default.mcpack").asFile
        if (defaultMcpack.isFile) {
            defaultMcpack.copyTo(dest.resolve("yapcore-default.mcpack"), overwrite = true)
        }
        val parityPin = project.layout.projectDirectory
            .file("src/main/resources/protocol/bedrock/parity/band_26_50/provenance/manifest.v1.json").asFile
        if (parityPin.isFile) {
            dest.resolve("parity-band_26_50-provenance.manifest.v1.json")
                .writeText(parityPin.readText())
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

            Bedrock-feel parity (Phase 6):
              client_mods.zip            → Fabric mods (presence/blocks/visuals/…)
              yapcore-default.zip        → JE resource pack (incl. Bedrock block ports)
              yapcore-default.mcpack     → Bedrock pack
              parity-band_26_50-provenance.manifest.v1.json

            Rebuild server:  gradle publishReleasesFolder
            Rebuild clients: ./scripts/build-yap-client-render.sh
            Parity smoke:    ./scripts/parity/smoke-bedrock-feel.sh
            Slim CORE+NETWORK only:  gradle assembleRelease -PyapGameplay=false

            Docs: docs/start/RELEASES.md · docs/product/BEDROCK_FEEL_MATRIX.md
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
