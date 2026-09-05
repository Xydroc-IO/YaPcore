val yapGameplayProp: Provider<String> = providers.gradleProperty("yapGameplay").orElse("false")
val yapGameplayEnabled: Boolean =
    yapGameplayProp.get() != "false" && yapGameplayProp.get() != "0"

tasks.register<Exec>("prepareClientPack") {
    group = "distribution"
    description =
        "Build yapcore-default.zip (Faithful CORE + YaP Skies overlays)"
    workingDir = project.projectDir
    commandLine("bash", "scripts/build-default-resourcepack.sh")
    outputs.file(project.file("resourcepacks/yapcore-default.zip"))
    inputs.dir(project.file("resourcepacks/yap-skies")).optional()
    inputs.file(project.file("scripts/generate-yap-skies.py")).optional()
    inputs.file(project.file("resourcepacks/faithful-64x.zip")).optional()
}

tasks.register("assembleRelease") {
    group = "distribution"
    description =
        "Release package with all first-party plugins (CORE+NETWORK+GAMEPLAY)."
    dependsOn(
        tasks.named("distJar"),
        "installProductDefaults",
        "prepareClientPack",
        "assemblePluginDist",
        "assembleNetworkSuite",
        ":yap-link-native:shadowJar",
        ":yap-link-plugin-chat-bridge:installIntoLinkPlugins",
        ":yap-link-plugin-mod-sync:installIntoLinkPlugins",
        ":yap-link-plugin-server-selector:installIntoLinkPlugins",
        ":yap-link-plugin-tab-bridge:installIntoLinkPlugins",
        ":yap-link-plugin-discord:installIntoLinkPlugins",
    )
    if (yapGameplayEnabled) {
        dependsOn("installGameplayDefaults")
    }

    val releaseRoot = layout.buildDirectory.dir("dist/yapcore-release")
    val includeGameplay = yapGameplayEnabled

    doLast {
        val root = releaseRoot.get().asFile
        root.mkdirs()
        val jar = layout.buildDirectory.file("dist/yapcore.jar").get().asFile
        require(jar.isFile) { "Missing $jar — run distJar first" }

        val corePluginJars = listOf(
            "yap-placeholderapi.jar",
            "yap-plugin-compat.jar",
            "yap-pregen.jar",
            "yap-db.jar",
            "yap-perms.jar",
            "yap-playerdata.jar",
            "yap-moderation.jar",
            "yap-essentials.jar",
            "yap-admin.jar",
            "yap-protect.jar",
            "yap-world.jar",
            "WorldEdit.jar",
            "yap-regions.jar",
            "yap-npcs.jar",
            "yap-guard.jar",
            "yap-lagguard.jar",
            "yap-map.jar",
            "yap-factions.jar",
            "yap-packs.jar",
            "yap-commands.jar",
            "yap-chat.jar",
            "yap-tab.jar",
            "yap-discord.jar",
            "yap-floodgate.jar",
            "yap-bedrock-ui.jar",
            "yap-folia-bridge.jar",
        )
        val gameplayPluginJars = listOf(
            "yap-gameplay-knobs.jar",
            "yap-stacker.jar",
            "yap-skills.jar",
            "yap-disasters.jar",
        )
        val pluginJars = if (includeGameplay) {
            corePluginJars + gameplayPluginJars
        } else {
            corePluginJars
        }
        val packFiles = buildList {
            add("yapcore-default.zip")
            add("faithful-64x.zip")
            add("CREDITS.md")
            add("FAITHFUL_LICENSE.txt")
            add("README.md")
        }
        val linuxScripts = listOf(
            "lib.sh", "start.sh", "start-prod.sh", "stop.sh", "status.sh", "gui.sh",
            "start-yap-link.sh", "nginx-setup.sh", "setup-velocity-forwarding.sh",
            "build-default-resourcepack.sh", "fetch-faithful-64x.sh",
            "generate-yap-skies.py",
            "fetch-folia.sh", "fetch-tebex.sh", "fetch-grim.sh", "grim-ac.sh",
            "vendor-folia.sh", "folia-patch.sh", "build-yap-folia.sh",
            "seed-defaults.sh", "apply-production-profile.sh",
            "yapctl",
        )

        fun copyCommon(dest: File) {
            dest.mkdirs()
            project.file("LICENSE").copyTo(dest.resolve("LICENSE"), overwrite = true)
            jar.copyTo(dest.resolve("yapcore.jar"), overwrite = true)
            val linkJar = project.file("yap-first-party/link/native/build/libs/yap-link.jar")
            if (linkJar.isFile) {
                linkJar.copyTo(dest.resolve("yap-link.jar"), overwrite = true)
            }
            val linkData = project.file("link-data")
            if (linkData.isDirectory) {
                project.copy {
                    from(linkData)
                    into(dest.resolve("link-data"))
                    exclude("forwarding.secret")
                }
            }
            project.copy {
                from(project.file("config/defaults"))
                into(dest.resolve("config/defaults"))
            }
            project.copy {
                from(project.file("config"))
                into(dest.resolve("config"))
                include("README.md", "*.example", ".gitkeep")
                // Never ship operator live config (tokens, ops, JDBC overrides)
                exclude("server.properties", "yap-ranks-applied")
            }
            val templates = project.file("config/templates")
            if (templates.isDirectory) {
                project.copy {
                    from(templates)
                    into(dest.resolve("config/templates"))
                }
            }
            project.copy {
                from(project.file("plugins"))
                into(dest.resolve("plugins"))
                include(*(pluginJars + "README.md").toTypedArray())
            }
            // Optional Tebex Folia plugin (GPLv3) — run ./scripts/fetch-tebex.sh before assemble
            val tebexJar = project.file("plugins/tebex.jar")
            if (tebexJar.isFile) {
                tebexJar.copyTo(dest.resolve("plugins/tebex.jar"), overwrite = true)
                listOf("tebex-NOTICE.txt", "tebex-LICENSE-GPLv3.txt").forEach { name ->
                    val f = project.file("plugins/$name")
                    if (f.isFile) {
                        f.copyTo(dest.resolve("plugins/$name"), overwrite = true)
                    }
                }
            }
            // Optional Grim AC (GPLv3) — fetched on seed-defaults as grim.jar.disabled
            val grimJar = project.file("plugins/grim.jar")
            val grimDisabled = project.file("plugins/grim.jar.disabled")
            val grimSource = when {
                grimDisabled.isFile -> grimDisabled
                grimJar.isFile -> grimJar
                else -> null
            }
            if (grimSource != null) {
                grimSource.copyTo(dest.resolve("plugins/grim.jar.disabled"), overwrite = true)
                listOf("grim-NOTICE.txt", "grim-LICENSE-GPLv3.txt").forEach { name ->
                    val f = project.file("plugins/$name")
                    if (f.isFile) {
                        f.copyTo(dest.resolve("plugins/$name"), overwrite = true)
                    }
                }
            }
            project.copy {
                from(project.file("third-party/tebex"))
                into(dest.resolve("third-party/tebex"))
                include("NOTICE.txt", "LICENSE-GPLv3.txt", "README.md")
            }
            project.copy {
                from(project.file("third-party/grim"))
                into(dest.resolve("third-party/grim"))
                include("NOTICE.txt", "LICENSE-GPLv3.txt", "README.md")
            }
            project.copy {
                from(project.file("modules"))
                into(dest.resolve("modules"))
                include("*.jar", "README.md")
                if (!includeGameplay) {
                    exclude(
                        "yap-stacker-module.jar",
                        "yap-gameplay-knobs-module.jar",
                    )
                }
            }
            project.copy {
                from(project.file("resourcepacks"))
                into(dest.resolve("resourcepacks"))
                include(*packFiles.toTypedArray())
            }
            project.copy {
                from(project.file("docs"))
                into(dest.resolve("docs"))
                exclude("pdf/**")
            }
            project.copy {
                from(project.file("branding"))
                into(dest.resolve("branding"))
                include("*.png", "README.md")
            }
            project.copy {
                from(project.file("deploy/nginx"))
                into(dest.resolve("deploy/nginx"))
                include("*.template", "README.md")
                exclude("generated/**")
            }
            project.copy {
                from(project.file("deploy/mariadb"))
                into(dest.resolve("deploy/mariadb"))
                exclude("**/generated/**")
                // .env is gitignored; ship example only
                exclude(".env")
            }
            project.copy {
                from(project.file("deploy/postgres"))
                into(dest.resolve("deploy/postgres"))
                exclude("**/generated/**")
                exclude(".env")
            }
            // Optional Folia jar cache if present on builder host.
            // Prefer shipping yap-folia when built; stock folia-* remains fallback.
            val libDir = project.file("lib")
            if (libDir.isDirectory) {
                project.copy {
                    from(libDir)
                    into(dest.resolve("lib"))
                    include("yap-folia-*.jar", "folia-*.jar")
                }
            }
        }

        fun writeLinuxWrappers(dest: File) {
            listOf("start", "stop", "status", "gui", "nginx-setup").forEach { name ->
                val wrapper = dest.resolve("$name.sh")
                wrapper.writeText(
                    """
                    #!/usr/bin/env bash
                    set -eu
                    ROOT="${'$'}(CDPATH= cd -- "${'$'}(dirname -- "${'$'}0")" && pwd)"
                    exec bash "${'$'}ROOT/scripts/$name.sh" "${'$'}@"
                    """.trimIndent() + "\n"
                )
                wrapper.setExecutable(true)
            }
            dest.resolve("start-prod.sh").writeText(
                """
                #!/usr/bin/env bash
                set -eu
                ROOT="${'$'}(CDPATH= cd -- "${'$'}(dirname -- "${'$'}0")" && pwd)"
                exec bash "${'$'}ROOT/scripts/start-prod.sh" "${'$'}@"
                """.trimIndent() + "\n"
            )
            dest.resolve("start-prod.sh").setExecutable(true)
            dest.resolve("scripts").listFiles()
                ?.filter { it.name.endsWith(".sh") || it.name == "yapctl" }
                ?.forEach { it.setExecutable(true) }
        }

        fun writeWindowsCmdWrappers(dest: File) {
            val map = mapOf(
                "start.cmd" to "Start.ps1",
                "stop.cmd" to "Stop.ps1",
                "status.cmd" to "Status.ps1",
                "gui.cmd" to "Gui.ps1",
                "start-prod.cmd" to "Start-Prod.ps1",
                "nginx-setup.cmd" to "Nginx-Setup.ps1",
                "start-mariadb.cmd" to "Start-MariaDB.ps1",
                "stop-mariadb.cmd" to "Stop-MariaDB.ps1",
                "configure-playerdata.cmd" to "Configure-PlayerData.ps1",
                "configure-db.cmd" to "Configure-Db.ps1",
            )
            map.forEach { (cmdName, ps1) ->
                dest.resolve(cmdName).writeText(
                    """
                    @echo off
                    setlocal
                    cd /d "%~dp0"
                    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\%ps1%" %*
                    """.trimIndent() + "\r\n"
                )
            }
        }

        val sharedNotes = """
            Shared contents (both platforms)
            --------------------------------
            LICENSE                 GNU GPLv3 (YaPcore first-party) — docs/start/LICENSING.md
            yapcore.jar
            yap-link.jar          native network proxy (see docs/network/YAP_LINK_NATIVE.md)
            link-data/            Link config + plugins (link.properties, plugins/*.jar)
            plugins/  all first-party jars (CORE+NETWORK+GAMEPLAY: skills, stacker, disasters, …)
                      Optional: tebex.jar (GPLv3) via ./scripts/fetch-tebex.sh — Hub store
                      Optional: grim.jar.disabled (GPLv3) — fetched on seed-defaults; enable via grim-ac.sh
            modules/  CORE + GAMEPLAY fine-tune modules
                      (gradle installFineTuneModules · docs/plugins/MODULES_AND_API.md)
            resourcepacks/yapcore-default.zip
            config/
            docs/
            lib/  (yap-folia-*.jar when built on host; else stock folia-*.jar)

            Web dashboard: http://127.0.0.1:8080/
            Token: config/server.properties → web-dashboard-token

            Requires Java 25+ on PATH (or JAVA_HOME).
            Product path: YaP-Folia recommended (./scripts/build-yap-folia.sh +
            folia-jar-source=build). Stock Fill: fetch-folia / folia-jar-source=fetch.
            """.trimIndent()

        // --- linux ---
        val linux = root.resolve("linux")
        if (linux.exists()) linux.deleteRecursively()
        copyCommon(linux)
        project.copy {
            from(project.file("scripts"))
            into(linux.resolve("scripts"))
            include(*linuxScripts.toTypedArray())
        }
        project.copy {
            from(project.file("scripts/db"))
            into(linux.resolve("scripts/db"))
            include("*.sh")
        }
        writeLinuxWrappers(linux)
        listOf(
            "start-mariadb",
            "stop-mariadb",
            "start-postgres",
            "ensure-postgres",
            "status-postgres",
            "configure-playerdata",
            "configure-db",
            "ensure-db",
        ).forEach { name ->
            val script = when (name) {
                "configure-playerdata" -> "configure-playerdata.sh"
                "configure-db" -> "configure-db.sh"
                "ensure-db" -> "ensure-db.sh"
                else -> "$name.sh"
            }
            val wrapper = linux.resolve("$name.sh")
            wrapper.writeText(
                """
                #!/usr/bin/env bash
                set -eu
                ROOT="${'$'}(CDPATH= cd -- "${'$'}(dirname -- "${'$'}0")" && pwd)"
                exec bash "${'$'}ROOT/scripts/db/$script" "${'$'}@"
                """.trimIndent() + "\n"
            )
            wrapper.setExecutable(true)
        }
        linux.resolve("scripts/db").listFiles()
            ?.filter { it.name.endsWith(".sh") }
            ?.forEach { it.setExecutable(true) }
        linux.resolve("RELEASE.txt").writeText(
            """
            YaPcore — Linux release
            =======================

            Launch
            ------
              chmod +x *.sh scripts/*.sh scripts/db/*.sh scripts/yapctl
              ./scripts/seed-defaults.sh   # first boot configs (safe if already present)
              ./configure-db.sh --server-id lobby   # MariaDB + JDBC (recommended)
              # or: ./ensure-postgres.sh --server-id lobby
              # or: ./configure-db.sh --engine sqlite --server-id lobby
              ./start.sh --fg
              ./gui.sh
              ./stop.sh
              ./status.sh
              ./start-prod.sh --fg

            Defaults
            --------
              config/defaults/ is copied into place on first start (never overwrites).
              See docs/start/DEFAULTS.md

            Database (YaPDB — MariaDB default; Postgres / SQLite also supported)
            -------------------------------------------------------------------
              ./start-mariadb.sh && ./configure-db.sh --server-id lobby
              ./ensure-postgres.sh --server-id lobby
              ./configure-db.sh --engine sqlite --server-id lobby
              # multi-backend: ./configure-db.sh --host <db-ip> --server-id survival
              See docs/data/YAPDB.md · MARIADB.md · POSTGRES.md · SQLITE.md

            Folia (product game authority)
            ------------------------------
              ./scripts/build-yap-folia.sh
              ./scripts/fetch-folia.sh

            nginx edge
            ----------
              ./nginx-setup.sh --dry-run
              sudo ./nginx-setup.sh
              sudo ./nginx-setup.sh --install-pkg

            $sharedNotes
            """.trimIndent() + "\n"
        )

        // --- windows ---
        val windows = root.resolve("windows")
        if (windows.exists()) windows.deleteRecursively()
        copyCommon(windows)
        project.copy {
            from(project.file("scripts/windows"))
            into(windows.resolve("scripts"))
            include("*.ps1", "README.md")
        }
        writeWindowsCmdWrappers(windows)
        windows.resolve("RELEASE.txt").writeText(
            """
            YaPcore — Windows release
            =========================

            Launch
            ------
              start.cmd -Fg
              gui.cmd
              stop.cmd / status.cmd / start-prod.cmd

            MariaDB (YaPPlayerData — single or multi-backend)
            -------------------------------------------------
              start-mariadb.cmd
              configure-playerdata.cmd
              configure-db.cmd
              rem multi-backend: Configure-PlayerData.ps1 -HostAddress <db-ip> -ServerId survival
              stop-mariadb.cmd
              See docs\MARIADB.md

            Folia (product game authority)
            ------------------------------
              See docs\WINDOWS.md — fetch Folia jar into lib\

            nginx edge
            ----------
              nginx-setup.cmd -DryRun
              set NGINX_HOME=C:\nginx
              nginx-setup.cmd
              (nginx must include stream module — see docs\WINDOWS.md)

            $sharedNotes
            """.trimIndent() + "\n"
        )

        root.resolve("README.txt").writeText(
            """
            YaPcore release
            ===============

            Pick your OS folder (each is a full self-contained server tree):

              linux/     → bash: start, nginx-setup, start-mariadb, start-postgres, configure-db, ensure-db, ensure-postgres
              windows/   → cmd:  start, nginx-setup, start-mariadb, configure-db, configure-playerdata

            Both include deploy/nginx, deploy/mariadb, and deploy/postgres.
            See linux/RELEASE.txt, windows/RELEASE.txt, docs/start/RELEASES.md, docs/data/YAPDB.md.

            Standalone zips (also in build/dist/):
              yap-network-suite.zip   YaP Link + link plugins
              yap-gameplay-suite.zip  skills/stacker/knobs/disasters (+ modules)
            Full set: gradle assembleAllReleases
            """.trimIndent() + "\n"
        )

        println("Release package → ${root.absolutePath}")
        println("  linux/   ${linux.absolutePath}")
        println("  windows/ ${windows.absolutePath}")
    }
}
