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
    compileOnly(project(":yap-db-api"))
    compileOnly(project(":yap-perms-api"))
    compileOnly(project(":yap-moderation-api"))

    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("com.mysql:mysql-connector-j:9.1.0")

    testImplementation("io.papermc.paper:paper-api:$paperApi")
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveFileName.set("yap-essentials.jar")
    archiveClassifier.set("")
    relocate("com.zaxxer.hikari", "com.yapcore.essentials.libs.hikari")
    relocate("com.mysql", "com.yapcore.essentials.libs.mysql")
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
