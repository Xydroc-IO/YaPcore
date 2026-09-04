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
    implementation(project(":yap-db-api"))

    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("com.mysql:mysql-connector-j:9.1.0")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
}

tasks.shadowJar {
    archiveFileName.set("yap-db.jar")
    archiveClassifier.set("")
    relocate("com.zaxxer.hikari", "com.yapcore.db.libs.hikari")
    relocate("com.mysql", "com.yapcore.db.libs.mysql")
    relocate("org.postgresql", "com.yapcore.db.libs.postgresql")
    // Do not relocate org.sqlite — native libs + JDBC URL resolution break under relocation
    // Keep com.yapcore.db API unrelocated so other plugins can compile/link against it
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
