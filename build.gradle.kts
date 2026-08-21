import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask

plugins {
    java
    application
    id("com.gradleup.shadow") version "9.6.1"
    id("com.github.spotbugs") version "6.5.10"
    id("org.pastalab.fray.gradle") version "0.9.0"
}

group = "com.yapcore"
version = "1.0.0.0"

val frayVersion = "0.9.0"
val jcstressVersion = "0.16"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://libraries.minecraft.net")
    maven("https://repo.papermc.io/repository/maven-public/")
}

sourceSets {
    create("jcstress") {
        java.srcDir("src/jcstress/java")
        compileClasspath += sourceSets["main"].output
        runtimeClasspath += output + compileClasspath
    }
}

configurations {
    named("jcstressImplementation") {
        extendsFrom(configurations["implementation"])
    }
}

dependencies {
    implementation("io.netty:netty-all:4.1.115.Final")
    implementation("io.netty:netty-transport-native-epoll:4.1.115.Final:linux-x86_64")
    implementation("io.netty:netty-transport-native-epoll:4.1.115.Final:linux-aarch_64")
    implementation("io.netty:netty-transport-native-kqueue:4.1.115.Final:osx-x86_64")
    implementation("io.netty:netty-transport-native-kqueue:4.1.115.Final:osx-aarch_64")
    implementation("com.github.luben:zstd-jni:1.5.6-6")
    implementation("com.formdev:flatlaf:3.5.4")
    implementation("org.yaml:snakeyaml:2.3")
    // Paper-compatible text / audience API for plugins & modules
    implementation("net.kyori:adventure-api:4.17.0")
    implementation("net.kyori:adventure-text-serializer-legacy:4.17.0")
    implementation("net.kyori:adventure-text-serializer-plain:4.17.0")
    implementation("net.kyori:adventure-text-minimessage:4.17.0")
    // Mojang Brigadier — Paper-compatible command graphs
    implementation("com.mojang:brigadier:1.3.10")
    // POSIX chdir for Phase 2 Paper embed (Paperclip uses process cwd)
    implementation("net.java.dev.jna:jna:5.17.0")
    // Protocol catalog JSON (item/block/entity band tables)
    implementation("com.google.code.gson:gson:2.11.0")
    // Plugin back-compat (1.20–1.21 → 26.2) light ASM rewrite
    implementation("org.ow2.asm:asm:9.7.1")
    implementation("org.ow2.asm:asm-commons:9.7.1")

    // JCIP / SpotBugs concurrency annotations (compile-time only)
    compileOnly("com.github.stephenc.jcip:jcip-annotations:1.0-1")
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.9.3")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.pastalab.fray:fray-junit:$frayVersion")

    add("jcstressImplementation", "org.openjdk.jcstress:jcstress-core:$jcstressVersion")
    add("jcstressAnnotationProcessor", "org.openjdk.jcstress:jcstress-core:$jcstressVersion")
    add("jcstressImplementation", sourceSets["main"].output)
}

application {
    mainClass.set("com.yapcore.Main")
}

spotbugs {
    ignoreFailures = true
    showProgress = true
    effort = Effort.MAX
    reportLevel = Confidence.DEFAULT
    // Focus on the multi-threaded hot path first
    onlyAnalyze = listOf(
        "com.yaplabs.yapengine.sync.-",
        "com.yaplabs.yapengine.sequencing.-",
        "com.yaplabs.yapengine.core.spatial.-"
    )
}

tasks.withType<SpotBugsTask>().configureEach {
    reports.create("html") {
        required.set(true)
    }
    reports.create("xml") {
        required.set(true)
    }
}

tasks.register<JavaExec>("runYapEngine") {
    group = "application"
    description = "Run YapEngine 16-thread architecture demo"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.yaplabs.yapengine.Main")
}

tasks.register<JavaExec>("runTestLab") {
    group = "application"
    description = "Open YaPcore Test Lab GUI (suites + live console)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.yapcore.gui.TestLab")
    systemProperty("yapcore.home", project.projectDir.absolutePath)
    standardInput = System.`in`
}

tasks.test {
    useJUnitPlatform {
        excludeTags("soak")
    }
    // Keep the fast suite free of Fray schedule exploration
    filter {
        excludeTestsMatching("*FrayTest")
    }
}

tasks.withType<Test>().configureEach {
    if (name == "frayTest") {
        group = "verification"
        description = "Deterministic concurrency tests via CMU Fray"
        filter {
            includeTestsMatching("*FrayTest")
        }
    }
}

tasks.register<Test>("soakTest") {
    group = "verification"
    description = "Long-running soak / retention tests (tagged @Tag(\"soak\"))"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("soak")
    }
    // Default short soak for CI; override with -Dyap.soak.seconds=86400
    systemProperty("yap.soak.seconds", System.getProperty("yap.soak.seconds", "30"))
    systemProperty("yap.soak.bots", System.getProperty("yap.soak.bots", "32"))
}

tasks.register<JavaExec>("jcstress") {
    group = "verification"
    description = "Run JCStress suite for AtomicLeaseManager / lock-free primitives"
    dependsOn(tasks.named("compileJcstressJava"))
    classpath = sourceSets["jcstress"].runtimeClasspath
    mainClass.set("org.openjdk.jcstress.Main")
    args(
        "-t", "com.yaplabs.yapengine.sync..*",
        "-v",
        "-f", "1"
    )
    workingDir = layout.buildDirectory.dir("jcstress-results").get().asFile
    doFirst {
        workingDir.mkdirs()
    }
}

tasks.register<JavaExec>("boundaryStress") {
    group = "verification"
    description = "In-process boundary handoff stress harness (headless bot stand-in)"
    classpath = sourceSets["main"].runtimeClasspath + sourceSets["test"].runtimeClasspath
    dependsOn(tasks.named("testClasses"))
    mainClass.set("com.yaplabs.yapengine.stress.BoundaryStressMain")
    systemProperty("yap.stress.bots", System.getProperty("yap.stress.bots", "100"))
    systemProperty("yap.stress.seconds", System.getProperty("yap.stress.seconds", "60"))
    systemProperty("yap.stress.handoffs", System.getProperty("yap.stress.handoffs", "50000"))

    val jfr = System.getProperty("yap.stress.jfr", "")
    if (jfr.isNotBlank()) {
        jvmArgs(
            "-XX:StartFlightRecording=disk=true,dumponexit=true,filename=$jfr,settings=profile"
        )
    }
}

tasks.register<JavaExec>("endurance") {
    group = "verification"
    description = "Months-long readiness soak — samples LIVE/leases/heap and writes HTML report"
    classpath = sourceSets["main"].runtimeClasspath + sourceSets["test"].runtimeClasspath
    dependsOn(tasks.named("testClasses"))
    mainClass.set("com.yaplabs.yapengine.endurance.EnduranceHarness")
    systemProperty("yapcore.home", project.projectDir.absolutePath)
    systemProperty("yap.endurance.bots", System.getProperty("yap.endurance.bots", "64"))
    systemProperty("yap.endurance.seconds", System.getProperty("yap.endurance.seconds", "120"))
    systemProperty("yap.endurance.idleSeconds", System.getProperty("yap.endurance.idleSeconds", "15"))
    systemProperty("yap.endurance.sampleMs", System.getProperty("yap.endurance.sampleMs", "5000"))
    systemProperty(
        "yap.endurance.reportDir",
        layout.projectDirectory.dir("logs/endurance").asFile.absolutePath
    )
    val jfr = System.getProperty("yap.endurance.jfr", "")
    if (jfr.isNotBlank()) {
        jvmArgs(
            "-XX:StartFlightRecording=disk=true,dumponexit=true,filename=$jfr,settings=profile"
        )
    }
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    systemProperty("yapcore.home", project.projectDir.absolutePath)
    jvmArgs("-Xms512m", "-Xmx2048m")
}

// ---------------------------------------------------------------------------
// Product defaults — CORE+NETWORK (default) vs GAMEPLAY (opt-in)
//   gradle installProductDefaults
//   gradle installGameplayDefaults
//   gradle assembleRelease -PyapGameplay=true
// ---------------------------------------------------------------------------

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
    if (findProject(":playerdata-plugin") != null) {
        dependsOn(":playerdata-plugin:installIntoPlugins")
    }
    if (findProject(":packs-plugin") != null) {
        dependsOn(":packs-plugin:installIntoPlugins")
    }
    if (findProject(":chat-plugin") != null) {
        dependsOn(":chat-plugin:installIntoPlugins")
    }
    if (findProject(":floodgate-plugin") != null) {
        dependsOn(":floodgate-plugin:installIntoPlugins")
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
        ":playerdata-plugin:shadowJar",
        ":packs-plugin:jar",
        ":chat-plugin:jar",
        ":floodgate-plugin:jar",
        ":vehicles-plugin:jar",
        ":vehicles-module:jar",
        ":gameplay-knobs-plugin:jar",
        ":stacker-plugin:jar",
        ":phase3-plugin:jar",
        ":yap-db-api:jar",
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
        val engineDir = dest.resolve("engine")
        val modulesCore = dest.resolve("modules/core")
        val modulesGameplay = dest.resolve("modules/gameplay")
        listOf(coreDir, gameplayDir, apiDir, engineDir, modulesCore, modulesGameplay).forEach { it.mkdirs() }

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
        copyNamed(jarOf(":playerdata-plugin", "shadowJar"), coreDir)
        copyNamed(jarOf(":packs-plugin"), coreDir)
        copyNamed(jarOf(":chat-plugin"), coreDir)
        copyNamed(jarOf(":floodgate-plugin"), coreDir)

        copyNamed(jarOf(":vehicles-plugin"), gameplayDir)
        copyNamed(jarOf(":gameplay-knobs-plugin"), gameplayDir)
        copyNamed(jarOf(":stacker-plugin"), gameplayDir)

        copyNamed(jarOf(":yap-db-api"), apiDir)
        copyNamed(jarOf(":phase3-plugin"), engineDir, "yap-spatial-tick.jar")

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
            api/              yap-db-api.jar
            engine/           yap-spatial-tick.jar

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

tasks.register<Exec>("prepareClientPack") {
    group = "distribution"
    description =
        "Build yapcore-default.zip (Faithful CORE; + vehicles when -PyapGameplay=true)"
    workingDir = project.projectDir
    environment("YAP_INCLUDE_VEHICLES", if (yapGameplayEnabled) "1" else "0")
    commandLine("bash", "scripts/build-default-resourcepack.sh")
    outputs.file(project.file("resourcepacks/yapcore-default.zip"))
    inputs.dir(project.file("resourcepacks/yap-vehicles")).optional()
    inputs.file(project.file("resourcepacks/yap-vehicles.zip")).optional()
    inputs.file(project.file("resourcepacks/faithful-64x.zip")).optional()
}

tasks.register("assembleRelease") {
    group = "distribution"
    description =
        "Release package (CORE+NETWORK). Use -PyapGameplay=true for vehicles/stacker/knobs."
    dependsOn(
        tasks.named("distJar"),
        "installProductDefaults",
        "prepareClientPack",
        "assemblePluginDist",
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
            "yap-playerdata.jar",
            "yap-packs.jar",
            "yap-chat.jar",
            "yap-floodgate.jar",
        )
        val gameplayPluginJars = listOf(
            "yap-vehicles.jar",
            "yap-gameplay-knobs.jar",
            "yap-stacker.jar",
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
            if (includeGameplay) {
                add("yap-vehicles.zip")
            }
        }
        val docFiles = listOf(
            "VEHICLES.md", "CLIENTS_AND_PACKS.md", "WEB_DASHBOARD.md", "PREGEN.md",
            "WINDOWS.md", "NGINX_AND_LOCALHOST.md", "PLAYERDATA.md", "MARIADB.md", "YAPDB.md",
            "STACKER.md", "PLUGINS.md", "MODULES_AND_API.md", "TUNE.md",
        )
        val linuxScripts = listOf(
            "lib.sh", "start.sh", "start-prod.sh", "stop.sh", "status.sh", "gui.sh",
            "nginx-setup.sh", "heap-dump.sh", "build-default-resourcepack.sh", "fetch-faithful-64x.sh",
            "vendor-paper.sh", "build-vendor-paper.sh", "apply-yap-paper-hooks.sh",
        )

        fun copyCommon(dest: File) {
            dest.mkdirs()
            jar.copyTo(dest.resolve("yapcore.jar"), overwrite = true)
            project.copy {
                from(project.file("config"))
                into(dest.resolve("config"))
            }
            project.copy {
                from(project.file("plugins"))
                into(dest.resolve("plugins"))
                include(*(pluginJars + "README.md").toTypedArray())
            }
            project.copy {
                from(project.file("modules"))
                into(dest.resolve("modules"))
                include("*.jar", "README.md")
                if (!includeGameplay) {
                    exclude(
                        "yap-vehicles-module.jar",
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
                include(*docFiles.toTypedArray())
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
            // Paper pin only (clone happens on the host)
            project.copy {
                from(project.file("vendor"))
                into(dest.resolve("vendor"))
                include("paper.pin", "yap-overlays/**", "README.md")
            }
            // Optional YaP Paperclip if present on builder host
            val libDir = project.file("lib")
            if (libDir.isDirectory) {
                project.copy {
                    from(libDir)
                    into(dest.resolve("lib"))
                    include("paper-*-yap.jar", "paper-*.jar")
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
            listOf("vendor-paper", "build-vendor-paper").forEach { name ->
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
            dest.resolve("scripts").listFiles()
                ?.filter { it.name.endsWith(".sh") }
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
                "vendor-paper.cmd" to "Vendor-Paper.ps1",
                "build-vendor-paper.cmd" to "Build-Vendor-Paper.ps1",
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
            yapcore.jar
            plugins/  CORE+NETWORK by default (db, playerdata, packs, chat, floodgate, …)
                      GAMEPLAY (vehicles, stacker, knobs): rebuild with -PyapGameplay=true
                      or run: gradle installGameplayDefaults
            modules/  CORE fine-tune packaging modules by default;
                      GAMEPLAY adds vehicles/stacker/knobs modules
                      (gradle installFineTuneModules · docs/MODULES_AND_API.md)
            resourcepacks/yapcore-default.zip
            config/
            docs/
            lib/  (YaP Paperclip if present on build host)

            Web dashboard: http://127.0.0.1:8080/
            Token: config/server.properties → web-dashboard-token

            Requires Java 25+ on PATH (or JAVA_HOME).
            Phase 3: build Paperclip on this host (vendor-paper + build-vendor-paper)
            or ship lib/paper-*-yap.jar from the builder. See docs/WINDOWS.md.
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
        listOf("start-mariadb", "stop-mariadb", "configure-playerdata", "configure-db").forEach { name ->
            val script = when (name) {
                "configure-playerdata" -> "configure-playerdata.sh"
                "configure-db" -> "configure-db.sh"
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
              chmod +x *.sh scripts/*.sh scripts/db/*.sh
              ./start.sh --fg
              ./gui.sh
              ./stop.sh
              ./status.sh
              ./start-prod.sh --fg

            MariaDB (YaPPlayerData — single or multi-backend)
            -------------------------------------------------
              ./start-mariadb.sh
              ./configure-playerdata.sh
              ./configure-db.sh
              # multi-backend: ./configure-db.sh --host <db-ip> --server-id survival
              ./stop-mariadb.sh
              See docs/MARIADB.md

            Paperclip (Phase 3)
            -------------------
              ./vendor-paper.sh
              ./build-vendor-paper.sh

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
        // Paper hooks script (bash) used by Build-Vendor-Paper.ps1 via Git Bash
        project.copy {
            from(project.file("scripts/apply-yap-paper-hooks.sh"))
            into(windows.resolve("scripts"))
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

            Paperclip (Phase 3) — native Windows
            ------------------------------------
              vendor-paper.cmd
              build-vendor-paper.cmd
              (needs Git + JDK 25+ + Git Bash)

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

              linux/     → bash: start, nginx-setup, start-mariadb, configure-db, configure-playerdata
              windows/   → cmd:  start, nginx-setup, start-mariadb, configure-db, configure-playerdata

            Both include deploy/nginx, deploy/mariadb, and vendor/paper.pin.
            See linux/RELEASE.txt, windows/RELEASE.txt, docs/WINDOWS.md, docs/MARIADB.md.
            """.trimIndent() + "\n"
        )

        println("Release package → ${root.absolutePath}")
        println("  linux/   ${linux.absolutePath}")
        println("  windows/ ${windows.absolutePath}")
    }
}

// Phase 3 bridge plugin must be on the classpath before packaging
tasks.named("processResources") {
    dependsOn(":phase3-plugin:installIntoResources")
}

tasks.shadowJar {
    dependsOn(
        ":phase3-plugin:installIntoResources",
        "installProductDefaults",
        "prepareClientPack",
    )
    archiveBaseName.set("yapcore")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "com.yapcore.Main"
        attributes["Implementation-Title"] = "YaPcore"
        attributes["Implementation-Version"] = project.version.toString()
    }
}

tasks.register<Copy>("distJar") {
    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar.flatMap { it.archiveFile })
    into(layout.buildDirectory.dir("dist"))
    rename { "yapcore.jar" }
    doLast {
        val jar = layout.buildDirectory.file("dist/yapcore.jar").get().asFile
        jar.copyTo(project.file("yapcore.jar"), overwrite = true)
    }
}

tasks.named("build") {
    dependsOn(tasks.shadowJar, tasks.named("distJar"), "assembleRelease")
}

tasks.named("shadowJar") {
    finalizedBy(tasks.named("distJar"))
}

tasks.named("runYapEngine") {
    mustRunAfter(tasks.named("distJar"))
}

tasks.register("verifyConcurrency") {
    group = "verification"
    description = "SpotBugs (sync packages) + unit tests + Fray interleaving tests"
    dependsOn("spotbugsMain", "test", "frayTest")
}

tasks.register<Exec>("verifyPaperApiCoverage") {
    group = "verification"
    description = "Assert embedded Paperclip paper-api matches published Paper 26.2 API"
    workingDir = rootProject.projectDir
    commandLine("bash", "scripts/verify-paper-api-coverage.sh")
}
