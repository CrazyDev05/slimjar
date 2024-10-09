import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Workaround for (https://youtrack.jetbrains.com/issue/KTIJ-19369)
@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    `maven-publish`
    `java-gradle-plugin`
    id("com.github.johnrengelman.shadow") version "8.1.1"
    kotlin("jvm") version "1.9.0"
}

repositories {
    maven("https://plugins.gradle.org/m2/")
}

val shadowImplementation: Configuration by configurations.creating
configurations["compileOnly"].extendsFrom(shadowImplementation)
configurations["testImplementation"].extendsFrom(shadowImplementation)

dependencies {

    shadowImplementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
    shadowImplementation(project(":slimjar"))
    shadowImplementation("com.google.code.gson:gson:2.10")
    shadowImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0")

    compileOnly(gradleApi())
    compileOnly(gradleKotlinDsl())
    compileOnly("com.github.johnrengelman:shadow:8.1.1")

    testImplementation("com.github.johnrengelman:shadow:8.1.1")
    testImplementation("org.assertj:assertj-core:3.23.1")
    testImplementation(gradleApi())
    testImplementation(gradleKotlinDsl())
    testImplementation(gradleTestKit())

    // For grade log4j checker.
    configurations.onEach {
        it.exclude(group = "org.apache.logging.log4j", module = "log4j-core")
        it.exclude(group = "org.apache.logging.log4j", module = "log4j-api")
        it.exclude(group = "org.apache.logging.log4j", module = "log4j-slf4j-impl")
    }
}

kotlin {
    explicitApi()
}

// Required for plugin substitution to work in sample projects.
artifacts {
    add("runtimeOnly", tasks.shadowJar)
}

val ensureDependenciesAreInlined by tasks.registering {
    description = "Ensures all declared dependencies are inlined into shadowed jar"
    group = HelpTasksPlugin.HELP_GROUP
    dependsOn(tasks.shadowJar)

    doLast {
        val nonInlinedDependencies = mutableListOf<String>()
        zipTree(tasks.shadowJar.flatMap { it.archiveFile }).visit {
            if (!isDirectory) return@visit

            val path = relativePath
            if (
                !path.startsWith("META-INF") &&
                path.lastName.endsWith(".class") &&
                !path.pathString.startsWith("io.github.slimjar".replace(".", "/"))
            ) nonInlinedDependencies.add(path.pathString)
        }

        if (nonInlinedDependencies.isEmpty()) return@doLast
        throw GradleException("Found non inlined dependencies: $nonInlinedDependencies")
    }
}

tasks {
    named("check") {
        dependsOn(ensureDependenciesAreInlined)
        dependsOn(validatePlugins)
    }

    // Disabling default jar task as it is overridden by shadowJar
    named("jar") {
        enabled = false
    }

    whenTaskAdded {
        if (name == "publishPluginJar" || name == "generateMetadataFileForPluginMavenPublication") {
            dependsOn(named("shadowJar"))
        }
    }

    withType<GenerateModuleMetadata> {
        enabled = false
    }

    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
            languageVersion = "1.7"
            freeCompilerArgs = freeCompilerArgs + "-opt-in=kotlin.RequiresOptIn"
        }
    }

    shadowJar {
        archiveClassifier.set("")
        configurations = listOf(shadowImplementation)

        mapOf(
            "io.github.slimjar" to "",
            "me.lucko.jarrelocator" to ".jarrelocator",
            "com.google.gson" to ".gson",
            "kotlin" to ".kotlin",
            "org.intellij" to ".intellij",
            "org.jetbrains" to ".jetbrains"
        ).forEach { relocate(it.key, "io.github.slimjar${it.value}") }
    }

    test.get().useJUnitPlatform()
}

// Work around publishing shadow jars
afterEvaluate {
    publishing {
        publications {
            withType<MavenPublication> {
                if (name == "pluginMaven") {
                    setArtifacts(listOf(tasks.shadowJar.get()))
                }
            }
        }
    }
}

@Suppress("UnstableApiUsage")
gradlePlugin {
    website.set("https://github.com/CrazyDev05/slimjar")
    vcsUrl.set("https://github.com/CrazyDev05/slimjar")

    plugins {
        create("slimjar") {
            id = group.toString()
            displayName = "SlimJar"
            description = "JVM Runtime Dependency Management."
            implementationClass = "io.github.slimjar.SlimJarPlugin"
            tags.set(listOf("runtime dependency", "relocation"))
        }
    }
}
