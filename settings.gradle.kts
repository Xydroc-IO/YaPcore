plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "YaPcore"
include("phase3-plugin")
include("bench-plugin")
include("compat-smoke-plugin")
include("gameplay-knobs-plugin")
include("vehicles-plugin")
include("vehicles-module")
include("placeholderapi-plugin")
include("plugin-compat-plugin")
include("pregen-plugin")
include("stacker-plugin")
include("playerdata-plugin")
include("yap-db-api")
include("yap-db-plugin")
include("packs-plugin")
include("chat-plugin")
include("floodgate-plugin")
include("yap-vehicle-addon")
project(":yap-vehicle-addon").projectDir = file("examples/yap-vehicle-addon")
