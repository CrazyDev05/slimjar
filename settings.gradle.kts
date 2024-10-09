rootProject.name = "slimjar"

include("slimjar", "slimjar-external", "gradle-plugin", "loader-agent")

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://papermc.io/repo/repository/maven-public/")
    }
}
