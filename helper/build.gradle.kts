subprojects {
    group = "${rootProject.group}.helper"
    apply<com.vanniktech.maven.publish.MavenPublishPlugin>()

    dependencies {
        compileOnly(rootProject.libs.annotations)
        api(project(":runtime"))
    }
}