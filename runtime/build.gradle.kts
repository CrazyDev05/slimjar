val mockitoAgent = configurations.create("mockitoAgent")

dependencies {
    compileOnly(libs.annotations)
    
    testImplementation(libs.annotations)
    testImplementation(libs.jar.relocator)
    testImplementation(libs.mockito.core)
    mockitoAgent(libs.mockito.core) { isTransitive = false }
}

val templateSource = layout.projectDirectory.dir("src/main/templates")
val templateDest = layout.buildDirectory.dir("generated/sources/templates")
sourceSets.main { java.srcDir(templateDest) }

tasks {
    jar {
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


    compileJava {
        doFirst { generateTemplates() }
    }

    test {
        jvmArgs("-javaagent:${mockitoAgent.asPath}")
    }
}

fun generateTemplates() = copy {
    from(templateSource)
    into(templateDest)
    rename { "io/github/slimjar/$it" }
    expand(
        "version" to project.version,
        "relocator" to libs.versions.jar.relocator.get(),
        "asm" to libs.versions.asm.get()
    )
}

generateTemplates()