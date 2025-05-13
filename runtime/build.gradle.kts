dependencies {
    compileOnly(libs.annotations)
    
    testImplementation(libs.annotations)
    testImplementation(libs.jar.relocator)
    testImplementation(libs.gson)
    testImplementation(libs.mockito.core)
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
