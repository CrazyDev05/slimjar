rootProject.name = "slimjar"

include("runtime", "gradle-plugin", "loader-agent", "jar-relocator")
include(":helper:spigot", ":helper:velocity")

pluginManagement.repositories {
    mavenCentral()
    gradlePluginPortal()
}
