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
    implementation(project(":yap-sched"))
    implementation(project(":yap-bedrock-ui-api"))
    compileOnly(project(":yap-mmo-api"))
    compileOnly(project(":floodgate-plugin"))
}

tasks.jar {
    archiveFileName.set("yap-bedrock-ui.jar")
}

tasks.register<Copy>("installIntoPlugins") {
    dependsOn(tasks.jar)
    from(tasks.jar.get().archiveFile)
    into(rootProject.layout.projectDirectory.dir("plugins"))
}
