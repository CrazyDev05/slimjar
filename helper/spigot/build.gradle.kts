group = "${rootProject.group}.helper"

repositories {
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    compileOnly(libs.annotations)
    compileOnly(libs.spigot)

    api(project(":runtime"))
}