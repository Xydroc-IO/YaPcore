plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.yapcore"
version = "0.6.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    val paperApi = providers.gradleProperty("paperApiVersion").getOrElse("26.2.build.112-stable")
    compileOnly("io.papermc.paper:paper-api:$paperApi")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("com.mysql:mysql-connector-j:9.1.0")
    implementation("at.favre.lib:bcrypt:0.10.2")

    testImplementation("io.papermc.paper:paper-api:$paperApi")
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveFileName.set("yap-playerdata.jar")
    archiveClassifier.set("")
    relocate("com.zaxxer.hikari", "com.yapcore.playerdata.libs.hikari")
    relocate("com.mysql", "com.yapcore.playerdata.libs.mysql")
    relocate("at.favre.lib", "com.yapcore.playerdata.libs.favre")
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
