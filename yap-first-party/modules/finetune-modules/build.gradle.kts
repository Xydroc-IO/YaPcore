plugins {
    java
}

group = "com.yapcore"
version = "1.0.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(rootProject)
    val paperApi = providers.gradleProperty("paperApiVersion").getOrElse("26.2.build.112-stable")
    compileOnly("io.papermc.paper:paper-api:$paperApi")
}

data class FineTuneSpec(
    val id: String,
    val moduleName: String,
    val mainClass: String,
    val provides: List<String>,
    val requires: List<String> = emptyList(),
    val description: String,
)

val fineTuneModules = listOf(
    FineTuneSpec(
        "playerdata", "YaPPlayerDataModule",
        "com.yapcore.finetune.PlayerDataTuneModule",
        listOf("playerdata"),
        description = "Fine-tune YaPPlayerData — economy, features.*, auth, sync, claims",
    ),
    FineTuneSpec(
        "economy", "YaPEconomyModule",
        "com.yapcore.finetune.EconomyTuneModule",
        listOf("economy"), listOf("playerdata"),
        "Money profile on top of playerdata (economy.enabled + money features)",
    ),
    FineTuneSpec(
        "packs", "YaPPacksModule",
        "com.yapcore.finetune.PacksTuneModule",
        listOf("packs"),
        description = "Fine-tune resource packs (server.properties + YaPPacks)",
    ),
    FineTuneSpec(
        "highpop", "YaPHighpopModule",
        "com.yapcore.finetune.HighpopTuneModule",
        listOf("highpop"),
        description = "Point operators at config/templates/highpop (+ optional EAR)",
    ),
    FineTuneSpec(
        "ops-dashboard", "YaPOpsDashboardModule",
        "com.yapcore.finetune.OpsDashboardTuneModule",
        listOf("web-dashboard"),
        description = "Fine-tune web dashboard bind/token/localhost knobs",
    ),
    FineTuneSpec(
        "stacker", "YaPStackerModule",
        "com.yapcore.finetune.StackerTuneModule",
        listOf("stacker"),
        description = "Fine-tune packaging for yap-stacker (GAMEPLAY)",
    ),
    FineTuneSpec(
        "gameplay-knobs", "YaPGameplayKnobsModule",
        "com.yapcore.finetune.GameplayKnobsTuneModule",
        listOf("gameplay-knobs"),
        description = "Fine-tune packaging for yap-gameplay-knobs encyclopedia",
    ),
    FineTuneSpec(
        "pregen", "YaPPregenModule",
        "com.yapcore.finetune.PregenTuneModule",
        listOf("pregen"),
        description = "Fine-tune packaging for yap-pregen",
    ),
    FineTuneSpec(
        "chat", "YaPChatModule",
        "com.yapcore.finetune.ChatTuneModule",
        listOf("chat"), listOf("yapdb", "perms"),
        description = "Fine-tune full YaP chat (channels, PM, filter, unsigned fix)",
    ),
    FineTuneSpec(
        "floodgate", "YaPFloodgateModule",
        "com.yapcore.finetune.FloodgateTuneModule",
        listOf("floodgate"),
        description = "Fine-tune packaging for Velocity Bedrock identity",
    ),
    FineTuneSpec(
        "db", "YaPDbModule",
        "com.yapcore.finetune.DbTuneModule",
        listOf("yapdb"),
        description = "Fine-tune packaging for shared YaPDB pool",
    ),
    FineTuneSpec(
        "perms", "YaPPermsModule",
        "com.yapcore.finetune.PermsTuneModule",
        listOf("perms"), listOf("yapdb"),
        description = "Fine-tune native YaPPerms groups/tracks/prefixes",
    ),
    FineTuneSpec(
        "moderation", "YaPModerationModule",
        "com.yapcore.finetune.ModerationTuneModule",
        listOf("moderation"), listOf("yapdb", "perms"),
        description = "Fine-tune native moderation (ban/mute/warn/kick/history)",
    ),
    FineTuneSpec(
        "essentials", "YaPEssentialsModule",
        "com.yapcore.finetune.EssentialsTuneModule",
        listOf("essentials"), listOf("playerdata", "perms"),
        description = "Fine-tune Essentials-class QoL commands",
    ),
    FineTuneSpec(
        "protect", "YaPProtectModule",
        "com.yapcore.finetune.ProtectTuneModule",
        listOf("protect"), listOf("yapdb", "moderation"),
        description = "Fine-tune block/entity audit logging and rollback",
    ),
    FineTuneSpec(
        "world", "YaPWorldModule",
        "com.yapcore.finetune.WorldTuneModule",
        listOf("world"), listOf("perms", "pregen"),
        description = "Fine-tune world manager, selection, and schematics",
    ),
    FineTuneSpec(
        "tab", "YaPTabModule",
        "com.yapcore.finetune.TabTuneModule",
        listOf("tab"),
        description = "Fine-tune TAB header/footer, sidebar, boss bar, and Link sync",
    ),
    FineTuneSpec(
        "discord", "YaPDiscordModule",
        "com.yapcore.finetune.DiscordTuneModule",
        listOf("discord"),
        description = "Fine-tune Discord webhooks, chat relay, and inbound bridge",
    ),
)

tasks.named<Jar>("jar") {
    enabled = false
}

val packagingJarTasks = fineTuneModules.map { spec ->
    val genDir = layout.buildDirectory.dir("generated-module-yml/${spec.id}")
    val genYml = tasks.register("genModuleYml_${spec.id.replace('-', '_')}") {
        outputs.dir(genDir)
        doLast {
            val dir = genDir.get().asFile
            dir.mkdirs()
            // Flow-style lists — trimIndent + block lists desync indent (SnakeYAML
            // then sees a bare "- foo" at column 0 and throws document-start errors).
            val providesFlow = spec.provides.joinToString(", ", "[", "]")
            val requiresFlow = spec.requires.joinToString(", ", "[", "]")
            dir.resolve("module.yml").writeText(
                """
                name: ${spec.moduleName}
                main: ${spec.mainClass}
                version: ${project.version}
                api: yap-module-1
                author: YapLabs
                description: ${spec.description}
                provides: $providesFlow
                requires: $requiresFlow
                """.trimIndent() + "\n"
            )
        }
    }

    tasks.register<Jar>("jar_${spec.id.replace('-', '_')}") {
        dependsOn(tasks.classes, genYml)
        archiveFileName.set("yap-${spec.id}-module.jar")
        from(sourceSets.main.get().output)
        from(genDir)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

tasks.register("buildAllFineTuneModules") {
    group = "build"
    description = "Build every fine-tune packaging module jar"
    dependsOn(packagingJarTasks)
}

val coreIds = setOf(
    "playerdata", "economy", "packs", "highpop", "ops-dashboard",
    "pregen", "chat", "floodgate", "db", "perms", "moderation", "essentials",
    "protect", "world", "tab", "discord",
)
val gameplayIds = setOf("stacker", "gameplay-knobs")

fun Copy.copySpecs(ids: Set<String>) {
    packagingJarTasks.forEachIndexed { index, jarTask ->
        val spec = fineTuneModules[index]
        if (spec.id in ids) {
            from(jarTask.map { it.archiveFile })
        }
    }
}

tasks.register<Copy>("installCoreIntoModules") {
    group = "distribution"
    description = "Install CORE fine-tune modules into modules/"
    dependsOn("buildAllFineTuneModules")
    into(rootProject.layout.projectDirectory.dir("modules"))
    copySpecs(coreIds)
}

tasks.register<Copy>("installGameplayIntoModules") {
    group = "distribution"
    description = "Install GAMEPLAY fine-tune modules (stacker, knobs) into modules/"
    dependsOn("buildAllFineTuneModules")
    into(rootProject.layout.projectDirectory.dir("modules"))
    copySpecs(gameplayIds)
}

tasks.register<Copy>("installIntoModules") {
    group = "distribution"
    description = "Install all fine-tune packaging modules into modules/"
    dependsOn("installCoreIntoModules", "installGameplayIntoModules")
}
