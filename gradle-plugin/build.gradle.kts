plugins {
    `java-gradle-plugin`
    alias(libs.plugins.shadow)
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.publish.gradle)
}

repositories {
    gradlePluginPortal()
}

val shadowImplementation: Configuration by configurations.creating
val compileAndTest: Configuration by configurations.creating
configurations.apply {
    compileAndTest.extendsFrom(shadowImplementation)
    compileOnly { extendsFrom(compileAndTest) }
    testImplementation { extendsFrom(compileAndTest) }
}

dependencies {
    shadowImplementation(project(":runtime"))
    shadowImplementation(libs.gson)
    shadowImplementation(libs.kotlin.coroutines)

    compileAndTest(gradleApi())
    compileAndTest(gradleKotlinDsl())
    compileAndTest(libs.gradle.shadow)
    compileAndTest(libs.gradle.kotlin.jvm)

    testImplementation("org.assertj:assertj-core:3.23.1")
    testImplementation(gradleTestKit())

    // For grade log4j checker.
    configurations.configureEach {
        exclude(group = "org.apache.logging.log4j", module = "log4j-core")
        exclude(group = "org.apache.logging.log4j", module = "log4j-api")
        exclude(group = "org.apache.logging.log4j", module = "log4j-slf4j-impl")
    }
}

tasks {
    val ensureDependenciesAreInlined by registering {
        description = "Ensures all declared dependencies are inlined into shadowed jar"
        group = "verification"
        dependsOn(shadowJar)

        doLast {
            val nonInlinedDependencies = mutableListOf<String>()
            zipTree(shadowJar.flatMap { it.archiveFile }).visit {
                if (isDirectory) return@visit

                val path = relativePath
                if (
                    !path.startsWith("META-INF") &&
                    path.lastName.endsWith(".class") &&
                    !path.pathString.startsWith("io/github/slimjar")
                ) nonInlinedDependencies.add(path.pathString)
            }

            if (nonInlinedDependencies.isEmpty()) return@doLast
            throw GradleException("Found non inlined dependencies: $nonInlinedDependencies")
        }
    }

    // Disabling default jar task as it is overridden by shadowJar
    jar { enabled = false }

    test { enabled = false }

    check { dependsOn(ensureDependenciesAreInlined, validatePlugins) }

    shadowJar {
        archiveClassifier.set("")
        configurations = listOf(shadowImplementation)

        exclude("kotlin/**")

        listOf(
            "com.google.gson",
            "com.google.errorprone",
            "kotlinx",
            "org.intellij",
            "org.jetbrains.annotations",
        ).map { it to it.split('.').last() }.forEach { (original, last) ->
            relocate(original, "io.github.slimjar.libs.$last")
        }
    }

    whenTaskAdded {
        if (name == "generateMetadataFileForPluginMavenPublication")
            dependsOn(shadowJar)
    }

    publishPlugins {
        dependsOn(shadowJar)
    }

    withType<GenerateModuleMetadata> { enabled = false }
}

// Required for plugin substitution to work in sample projects.
artifacts {
    add("runtimeOnly", tasks.shadowJar)
}

// Work around publishing shadow jars
afterEvaluate {
    publishing.publications
        .withType<MavenPublication>()
        .filter { it.name == "pluginMaven" }
        .forEach { publication -> publication.setArtifacts(listOf(tasks.shadowJar)) }
}

gradlePlugin {
    website.set("https://github.com/CrazyDev05/slimjar")
    vcsUrl.set("https://github.com/CrazyDev05/slimjar")

    plugins {
        create("slimjar") {
            id = group.toString()
            displayName = "SlimJar"
            description = "JVM Runtime Dependency Management."
            implementationClass = "io.github.slimjar.SlimJarPlugin"
            tags = listOf("runtime dependency", "relocation")
        }
    }

    testSourceSets(sourceSets.test.get())
}
