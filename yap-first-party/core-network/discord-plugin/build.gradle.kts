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
    val paperApi = providers.gradleProperty("paperApiVersion").getOrElse("26.2.build.112-stable")
    compileOnly("io.papermc.paper:paper-api:$paperApi")
    implementation(project(":yap-sched"))
    // Soft-deps alone don't load API classes when YaPModeration failed to enable — ship a copy.
    implementation(project(":yap-moderation-api"))
    implementation(project(":yap-chat-api"))
}

tasks.jar {
    from({
        configurations.runtimeClasspath.get()
            .filter { f ->
                val n = f.name
                n.contains("yap-sched")
                        || n.contains("yap-moderation-api")
                        || n.contains("yap-chat-api")
            }
            .map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("yap-discord.jar")
}

tasks.register<Copy>("installIntoPlugins") {
    dependsOn(tasks.jar)
    from(tasks.jar.get().archiveFile)
    into(rootProject.layout.projectDirectory.dir("plugins"))
}
