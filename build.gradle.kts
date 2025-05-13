import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import dev.yumi.gradle.licenser.YumiLicenserGradlePlugin

plugins {
    alias(libs.plugins.licenser)
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.publish.maven) apply false
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

    extensions.findByType<MavenPublishBaseExtension>()?.apply {
        publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, true)
        signAllPublications()
    }
}
