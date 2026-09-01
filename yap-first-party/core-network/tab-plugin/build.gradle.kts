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
    implementation(project(":yap-tab-api"))
    compileOnly(project(":yap-perms-api"))
    compileOnly(project(":yap-mmo-api"))
    implementation("net.megavex:scoreboard-library-api:2.8.2")
    implementation("net.megavex:scoreboard-library-implementation:2.8.2")
}

tasks.shadowJar {
    archiveFileName.set("yap-tab.jar")
    archiveClassifier.set("")
    relocate("net.megavex", "com.yapcore.tab.libs.megavex")
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
