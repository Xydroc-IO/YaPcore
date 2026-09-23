plugins {
    java
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
    maven("https://repo.opencollab.dev/maven-snapshots/")
    maven("https://repo.opencollab.dev/main")
}

dependencies {
    implementation(project(":yap-protocol"))
    implementation("io.netty:netty-all:4.1.115.Final")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.cloudburstmc.protocol:bedrock-codec:3.0.0.Beta13-SNAPSHOT")
    implementation("org.cloudburstmc.math:immutable:2.0")
    implementation("org.cloudburstmc:nbt:3.0.5.Final")
    implementation("org.xerial.snappy:snappy-java:1.1.10.7")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    archiveFileName.set("yap-link-bedrock.jar")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
