plugins {
    java
}

group = "com.yapcore"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

tasks.jar {
    archiveFileName.set("yap-spatial-tick.jar")
}

// Copy into YaPcore resources for Phase3PaperRuntime to install
tasks.register<Copy>("installIntoResources") {
    dependsOn(tasks.jar)
    from(tasks.jar.get().archiveFile)
    into(rootProject.layout.projectDirectory.dir("src/main/resources/phase3"))
}
