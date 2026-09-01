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
    implementation(project(":yap-mmo-api"))
    implementation(project(":yap-bedrock-ui-api"))
    compileOnly(project(":yap-abilities-api"))
    compileOnly(project(":yap-npcs-api"))

    testImplementation("io.papermc.paper:paper-api:$paperApi")
    testImplementation(project(":yap-mmo-api"))
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveFileName.set("yap-mmo-bedrock.jar")
}

tasks.register<Copy>("installIntoPlugins") {
    dependsOn(tasks.jar)
    from(tasks.jar.get().archiveFile)
    into(rootProject.layout.projectDirectory.dir("plugins"))
}
