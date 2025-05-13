rootProject.name = "slimjar"

include("runtime", "gradle-plugin", "loader-agent")

pluginManagement.repositories {
    mavenCentral()
    gradlePluginPortal()
}
