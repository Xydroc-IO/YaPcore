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
}

dependencies {
    compileOnly(project(":yap-link-api"))
}

tasks.jar {
    archiveFileName.set("yap-link-chat-bridge.jar")
}

tasks.register<Copy>("installIntoLinkPlugins") {
    dependsOn(tasks.jar)
    from(tasks.jar.get().archiveFile)
    into(rootProject.file("link-data/plugins"))
}
