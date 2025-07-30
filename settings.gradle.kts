rootProject.name = "slimjar"

include("runtime", "gradle-plugin", "loader-agent", "jar-relocator")

pluginManagement.repositories {
    mavenCentral()
    gradlePluginPortal()
}
