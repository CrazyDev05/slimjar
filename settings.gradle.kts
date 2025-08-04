pluginManagement.repositories {
    mavenCentral()
    gradlePluginPortal()
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "slimjar"

include("runtime", "gradle-plugin", "loader-agent", "jar-relocator")
include(":helper:spigot", ":helper:velocity", ":helper:paper")
