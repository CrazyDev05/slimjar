import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.licenser)
    alias(libs.plugins.publish.maven)
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.asm.core)
    compileOnly(libs.asm.commons)
}

license {
    rule(file("LICENSE_HEADER"))
    include("**/*.java", "**/*.kt")
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    pom {
        name.set("SlimJar Jar-Relocator")
        description.set("A library to relocate packages in a jar file")
        url.set("https://github.com/CrazyDev22/slimjar")
        licenses {
            license {
                name.set("The Apache License 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }
        developers {
            developer {
                name.set("Luck")
                email.set("git@lucko.me")
                roles.set(listOf("Project starter"))
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