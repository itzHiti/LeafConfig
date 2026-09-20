plugins {
    alias(libs.plugins.shadow)
}

description = "Minimal Paper plugin demonstrating LeafConfig"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation(project(":leafconfig-paper"))
    compileOnly(libs.paper.api)
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("dev.leafconfig", "dev.leafconfig.example.libs.leafconfig")
    relocate("org.snakeyaml.engine", "dev.leafconfig.example.libs.snakeyaml")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.withType<Javadoc>().configureEach { enabled = false }
tasks.named<Jar>("javadocJar") { enabled = false }
tasks.named<Jar>("sourcesJar") { enabled = false }

dependencies {
    testImplementation(libs.paper.api)
}
