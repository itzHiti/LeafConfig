description = "LeafConfig Paper integration and Paper/Adventure codecs"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    api(project(":leafconfig-yaml"))
    compileOnly(libs.paper.api)
    testImplementation(libs.paper.api)
}
