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
}

tasks.jar {
    archiveFileName.set("yap-link-api.jar")
}
