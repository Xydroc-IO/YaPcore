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
}

tasks.jar {
    archiveFileName.set("yap-mspt-bench.jar")
}

tasks.register<Jar>("popSimJar") {
    archiveFileName.set("yap-pop-sim.jar")
    from(sourceSets.main.get().output) {
        include("com/yapcore/popsim/**")
    }
    from("src/main/resources") {
        include("popsim-plugin.yml")
        rename { "plugin.yml" }
    }
}

tasks.named("build") {
    dependsOn("popSimJar")
}
