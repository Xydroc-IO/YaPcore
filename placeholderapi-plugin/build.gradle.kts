plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
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
    testImplementation("io.papermc.paper:paper-api:$paperApi")

    // Bundled into yap-placeholderapi.jar for getAdventure() / Metrics parity
    implementation("net.kyori:adventure-platform-bukkit:4.3.4")
    implementation("org.bstats:bstats-bukkit:3.1.0")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveFileName.set("yap-placeholderapi.jar")
    archiveClassifier.set("")
    relocate("org.bstats", "me.clip.placeholderapi.libs.bstats")
    mergeServiceFiles()
}

tasks.jar {
    enabled = true
    archiveClassifier.set("dev")
}

tasks.register<Copy>("installIntoPlugins") {
    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar.get().archiveFile)
    into(rootProject.layout.projectDirectory.dir("plugins"))
}
