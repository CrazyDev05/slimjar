import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import com.vanniktech.maven.publish.MavenPublishPlugin
import dev.yumi.gradle.licenser.YumiLicenserGradlePlugin

plugins {
    alias(libs.plugins.licenser)
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.publish.maven) apply false
    java
}

project(":runtime") {
    apply<MavenPublishPlugin>()
}

project(":gradle-plugin") {
    apply<MavenPublishPlugin>()
}

subprojects {
    apply<JavaLibraryPlugin>()
    apply<YumiLicenserGradlePlugin>()

    repositories {
        mavenCentral()
    }

    license {
        rule(rootProject.file("LICENSE"))
        include("**/*.java", "**/*.kt")
    }

    dependencies {
        testImplementation(rootProject.libs.jupiter)
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    tasks.test {
        useJUnitPlatform()
    }

    extensions.findByType<MavenPublishBaseExtension>()?.apply {
        publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
        signAllPublications()

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
