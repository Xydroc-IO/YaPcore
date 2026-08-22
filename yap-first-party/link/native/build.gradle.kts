plugins {
    java
    application
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
}

dependencies {
    implementation(project(":yap-protocol"))
    implementation(project(":yap-link-api"))
    implementation("io.netty:netty-all:4.1.115.Final")
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation(project(":yap-link-plugin-chat-bridge"))
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.yapcore.link.LinkMain")
}

tasks.jar {
    archiveFileName.set("yap-link.jar")
    manifest {
        attributes["Main-Class"] = "com.yapcore.link.LinkMain"
    }
}

tasks.shadowJar {
    archiveFileName.set("yap-link.jar")
    mergeServiceFiles()
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.register<Copy>("installIntoDist") {
    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar.get().archiveFile)
    into(rootProject.layout.projectDirectory.dir("dist"))
}

tasks.register<Copy>("installLinkPlugins") {
    dependsOn(
        ":yap-link-plugin-chat-bridge:installIntoLinkPlugins",
        ":yap-link-plugin-mod-sync:installIntoLinkPlugins",
        ":yap-link-plugin-server-selector:installIntoLinkPlugins",
    )
}
