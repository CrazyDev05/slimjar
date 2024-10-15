// Workaround for (https://youtrack.jetbrains.com/issue/KTIJ-19369)
@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    `maven-publish`
}

dependencies {
    testImplementation("me.lucko:jar-relocator:1.7")
    testImplementation("com.google.code.gson:gson:2.10")
    testImplementation("org.mockito:mockito-core:4.10.0")
    testImplementation("org.mockito:mockito-inline:4.10.0")
    testImplementation("cglib:cglib:3.3.0")
}

publishing {
    publications.create("maven", MavenPublication::class) {
        from(components["java"])
        groupId = group.toString()
        artifactId = "slimjar"
        version = project.version.toString()
        pom {
            name.set("SlimJar")
            description.set("A simple and robust runtime dependency manager for JVM languages.")
            url.set("https://github.com/CrazyDev05/slimjar")
            licenses {
                license {
                    name.set("The MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
            developers {
                developer {
                    id.set("vshnv")
                    name.set("Vaishnav Anil")
                    email.set("vaishnavanil7th@gmail.com")
                    roles.set(listOf("Project starter"))
                }
                developer {
                    id.set("ipsk")
                    name.set("Mateus Moreira")
                    roles.set(listOf("Previous Maintainer"))
                }
                developer {
                    id.set("Racci")
                    name.set("James Draycott")
                    email.set("racci@racci.dev")
                    roles.set(listOf("Previous Maintainer"))
                }
                developer {
                    id.set("CrazyDev05")
                    name.set("Julian Krings")
                    roles.set(listOf("MAINTAINER"))
                }
            }
            scm {
                connection.set("https://github.com/CrazyDev05/slimjar")
                developerConnection.set("https://github.com/CrazyDev05/slimjar.git")
                url.set("https://github.com/CrazyDev05/slimjar")
            }
        }
    }
}

tasks.jar {
    dependsOn(project(":loader-agent").tasks.jar)
    doFirst {
        copy {
            from(project(":loader-agent").tasks.getByName("jar").outputs.files.singleFile)
            into(layout.buildDirectory.file("resources/main/"))
            include("*.jar")
            rename("(.*)\\.jar", "loader-agent.isolated-jar")
        }
    }
}
