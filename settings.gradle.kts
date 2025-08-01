rootProject.name = "slimjar"

include("runtime", ":helper:spigot", "gradle-plugin", "loader-agent", "jar-relocator")

pluginManagement.repositories {
    mavenCentral()
    gradlePluginPortal()
}
