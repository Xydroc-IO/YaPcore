plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "YaPcore"
include("phase3-plugin")
include("bench-plugin")
include("compat-smoke-plugin")
