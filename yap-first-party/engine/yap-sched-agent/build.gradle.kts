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
    implementation("org.ow2.asm:asm:9.9.1")
    implementation("org.ow2.asm:asm-commons:9.9.1")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    archiveFileName.set("yap-sched-agent.jar")
    manifest {
        attributes(
            "Premain-Class" to "com.yapcore.sched.agent.SchedCompatAgent",
            "Agent-Class" to "com.yapcore.sched.agent.SchedCompatAgent",
            "Can-Redefine-Classes" to "true",
            "Can-Retransform-Classes" to "true",
            "Implementation-Title" to "yap-sched-agent",
            "Implementation-Version" to version,
            "Implementation-Vendor" to "YapLabs",
            "Bundle-License" to "GPL-3.0-or-later"
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
