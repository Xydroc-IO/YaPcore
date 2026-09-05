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
    // Soft-deps alone don't load API classes when YaPModeration failed to enable — ship a copy.
    implementation(project(":yap-moderation-api"))
    implementation(project(":yap-chat-api"))
    implementation(project(":yap-discord-api"))
    compileOnly(project(":yap-db-api"))
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    implementation("net.dv8tion:JDA:5.6.1") {
        exclude(module = "opus-java")
        exclude(module = "tink")
    }

    testImplementation("org.xerial:sqlite-jdbc:3.47.1.0")
    testImplementation(project(":yap-discord-api"))
    testImplementation("net.dv8tion:JDA:5.6.1") {
        exclude(module = "opus-java")
        exclude(module = "tink")
    }
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveFileName.set("yap-discord.jar")
    archiveClassifier.set("")
    relocate("org.sqlite", "com.yapcore.discord.libs.sqlite")
    mergeServiceFiles()
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
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
