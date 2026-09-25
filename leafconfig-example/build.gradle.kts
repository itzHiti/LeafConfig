plugins {
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
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
    // The plugin itself lives under dev.leafconfig.example and must keep its name for plugin.yml.
    relocate("dev.leafconfig", "dev.leafconfig.example.libs.leafconfig") {
        exclude("dev.leafconfig.example.**")
    }
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

// Published LeafConfig version that the no-shade variant asks Paper to download.
val publishedLeafConfigVersion = "0.3.1"
val minecraftVersion = libs.versions.paper.api.get().substringBefore("-")

// No-shade variant: only the plugin's own classes; plugin.yml `libraries:` makes Paper resolve
// leafconfig-paper and its dependencies from Maven Central at startup.
val librariesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("libraries")
    from(sourceSets.main.get().output) { exclude("plugin.yml") }
    val props = mapOf("version" to project.version.toString(), "leafconfigVersion" to publishedLeafConfigVersion)
    inputs.properties(props)
    from("src/libraries") { expand(props) }
}

// Manual smoke tests only (download a Paper server, need network):
//   ./gradlew :leafconfig-example:runServer            shaded + relocated jar
//   ./gradlew :leafconfig-example:runServerLibraries   no-shade jar, library from Maven Central
tasks.runServer {
    minecraftVersion(minecraftVersion)
    jvmArgs("-Dcom.mojang.eula.agree=true")
}

tasks.register<xyz.jpenilla.runpaper.task.RunServer>("runServerLibraries") {
    group = "run paper"
    description = "Runs a Paper server with the no-shade example plugin"
    version.set(minecraftVersion)
    pluginJars.from(librariesJar)
    runDirectory.set(layout.projectDirectory.dir("run-libraries"))
    jvmArgs("-Dcom.mojang.eula.agree=true")
    // Paper resolves `libraries:` from a Google mirror of Maven Central that lags behind repo1.
    // -Pleafconfig.centralRepository=https://repo1.maven.org/maven2 points it elsewhere for testing.
    providers.gradleProperty("leafconfig.centralRepository").orNull?.let {
        jvmArgs("-Dorg.bukkit.plugin.java.LibraryLoader.centralURL=$it")
    }
}
