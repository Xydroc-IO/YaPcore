plugins {
    java
    application
}

group = "com.yapcore"
version = "0.0.0.2"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.yapcore.fleet.agent.FleetAgentMain")
}

tasks.jar {
    archiveFileName.set("yap-fleet-agent.jar")
    manifest {
        attributes(
            "Main-Class" to "com.yapcore.fleet.agent.FleetAgentMain",
            "Implementation-Title" to "yap-fleet-agent",
            "Implementation-Version" to version,
            "Implementation-Vendor" to "YapLabs"
        )
    }
    from({
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Copy>("installAgent") {
    dependsOn(tasks.jar)
    from(tasks.jar.get().archiveFile)
    into(rootProject.layout.projectDirectory.dir("server/lib"))
}
