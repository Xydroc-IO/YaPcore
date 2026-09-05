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
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    val paperApi = providers.gradleProperty("paperApiVersion").getOrElse("26.2.build.112-stable")
    compileOnly("io.papermc.paper:paper-api:$paperApi")
    // Slash types; consumers also need JDA compileOnly. Runtime JDA comes from yap-discord.jar.
    compileOnly("net.dv8tion:JDA:5.6.1") {
        exclude(module = "opus-java")
        exclude(module = "tink")
    }
}

tasks.jar {
    archiveFileName.set("yap-discord-api.jar")
}
