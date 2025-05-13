import dev.yumi.gradle.licenser.YumiLicenserGradlePlugin

plugins {
    alias(libs.plugins.licenser)
    java
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
        testImplementation(rootProject.libs.jupiter.api)
        testRuntimeOnly(rootProject.libs.jupiter.engine)
    }
}
