repositories.maven("https://repo.papermc.io/repository/maven-public/")
dependencies {
    compileOnly(libs.paper)
    api(project(":helper:spigot"))
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))