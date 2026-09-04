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
    implementation("org.ow2.asm:asm:9.9.1")
    implementation("org.ow2.asm:asm-commons:9.9.1")

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
    description = "Run YapEngine chassis demo (edge/I/O; Folia owns game tick on product path)"
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
    maxHeapSize = "2g"
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

tasks.register<JavaExec>("scanFirstPartyFoliaCompat") {
    group = "verification"
    description = "ASM scan first-party plugin jars for legacy BukkitScheduler + folia-supported"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.yapcore.tools.FoliaPluginBytecodeScan")
    val distCore = layout.buildDirectory.dir("dist/yap-plugins/core-network")
    val distGameplay = layout.buildDirectory.dir("dist/yap-plugins/gameplay")
    val pluginsDir = layout.projectDirectory.dir("plugins")
    doFirst {
        val argsList = mutableListOf<String>()
        if (distCore.get().asFile.isDirectory) {
            argsList += distCore.get().asFile.absolutePath
        }
        if (distGameplay.get().asFile.isDirectory) {
            argsList += distGameplay.get().asFile.absolutePath
        }
        if (argsList.isEmpty()) {
            argsList += pluginsDir.asFile.absolutePath
        }
        args = argsList
    }
}

tasks.register("checkMsptRegressionFixtures") {
    group = "verification"
    description = "Run compare-folia.py against tracked MSPT fixtures + official fullcite cite"
    doLast {
        val root = project.projectDir
        val compare = root.resolve("scripts/bench/check-mspt-regression.sh")
        val stock = root.resolve("src/test/resources/mspt/stock-folia-heavypop.json")
        val yap = root.resolve("src/test/resources/mspt/yap-folia-heavypop.json")
        val regress = root.resolve("src/test/resources/mspt/yap-folia-heavypop-regress.json")
        val citeStock = root.resolve("src/test/resources/mspt/cite-fullcite-stock.json")
        val citeYap = root.resolve("src/test/resources/mspt/cite-fullcite-yapcore.json")
        fun run(vararg args: String): Int {
            val pb = ProcessBuilder(*args).directory(root).inheritIO()
            return pb.start().waitFor()
        }
        val ok = run(compare.absolutePath, stock.absolutePath, yap.absolutePath)
        if (ok != 0) {
            throw GradleException("expected pass fixture to exit 0, got $ok")
        }
        val fail = run(compare.absolutePath, stock.absolutePath, regress.absolutePath)
        if (fail != 1) {
            throw GradleException("expected regression fixture to exit 1, got $fail")
        }
        if (citeStock.isFile && citeYap.isFile) {
            val env = mapOf(
                "YAP_MSPT_STRICT_CITEABLE" to "1",
                "YAP_MSPT_REQUIRE_CITEABLE" to "1",
            )
            val pb = ProcessBuilder(compare.absolutePath, citeStock.absolutePath, citeYap.absolutePath)
                .directory(root)
                .inheritIO()
            pb.environment().putAll(env)
            val citeRc = pb.start().waitFor()
            if (citeRc != 0) {
                throw GradleException("official fullcite cite must pass citeable gate, got $citeRc")
            }
            logger.lifecycle("Official fullcite cite gate OK")
        }
        logger.lifecycle("MSPT fixture gates OK (pass + expected fail)")
    }
}

tasks.register<Exec>("checkDomainLineLimits") {
    group = "verification"
    description = "Fail if any chassis/first-party Java source exceeds 500 lines"
    workingDir = project.projectDir
    commandLine("bash", "scripts/check-domain-line-limits.sh")
}

tasks.named("check") {
    dependsOn("checkDomainLineLimits")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    systemProperty("yapcore.home", project.projectDir.absolutePath)
    jvmArgs("-Xms512m", "-Xmx2048m")
}


apply(from = "gradle/yap-license.gradle.kts")
apply(from = "gradle/yap-product.gradle.kts")
apply(from = "gradle/yap-release.gradle.kts")
apply(from = "gradle/yap-packaging.gradle.kts")

// Phase 3 bridge plugin removed — Folia is the product game authority
tasks.named("processResources") {
    // no-op
}

tasks.shadowJar {
    dependsOn(
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
        attributes["Implementation-Vendor"] = "YapLabs"
        attributes["Bundle-License"] = "GPL-3.0-or-later"
        attributes["Specification-Vendor"] = "https://www.gnu.org/licenses/gpl-3.0.html"
    }
    finalizedBy(tasks.named("distJar"))
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

tasks.named("runYapEngine") {
    mustRunAfter(tasks.named("distJar"))
}
