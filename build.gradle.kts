import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import net.ltgt.gradle.errorprone.errorprone

plugins {
    alias(libs.plugins.errorprone) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.spotless)
}

// Library modules published to Maven Central; the example plugin and benchmarks are not.
val publishedModules = setOf("leafconfig-api", "leafconfig-yaml", "leafconfig-paper")

allprojects {
    group = "io.github.itzhiti"
    version = "0.3.2-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

spotless {
    java {
        target("*/src/**/*.java")
        // Snippet holders repeat documentation code verbatim; DocumentationSnippetsTest keeps
        // them identical to README and docs, so the formatter must not reflow them.
        targetExclude("leafconfig-example/src/test/java/dev/leafconfig/example/snippets/**")
        googleJavaFormat(libs.versions.google.java.format.get())
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts")
        endWithNewline()
        trimTrailingWhitespace()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "jacoco")
    apply(plugin = "net.ltgt.errorprone")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    if (name in publishedModules) {
        apply(plugin = "com.vanniktech.maven.publish")
        extensions.configure<MavenPublishBaseExtension> {
            // Staging only: the release is triggered by hand in the Central Portal after review.
            publishToMavenCentral(automaticRelease = false)
            // Signing needs the in-memory key from CI secrets; local builds without it still
            // assemble and publish to mavenLocal unsigned.
            if (providers.gradleProperty("signingInMemoryKey").isPresent) {
                signAllPublications()
            }
            configure(JavaLibrary(javadocJar = JavadocJar.Javadoc(), sourcesJar = true))
            coordinates(group.toString(), name, version.toString())
            pom {
                name.set(project.name)
                description.set(provider { project.description ?: project.name })
                url.set("https://github.com/itzHiti/LeafConfig")
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("itzHiti")
                        name.set("itzHiti")
                        url.set("https://github.com/itzHiti")
                    }
                }
                scm {
                    url.set("https://github.com/itzHiti/LeafConfig")
                    connection.set("scm:git:https://github.com/itzHiti/LeafConfig.git")
                    developerConnection.set("scm:git:ssh://git@github.com/itzHiti/LeafConfig.git")
                }
            }
        }
    } else {
        extensions.configure<JavaPluginExtension> {
            withSourcesJar()
            withJavadocJar()
        }
    }

    extensions.configure<JacocoPluginExtension> {
        toolVersion = rootProject.libs.versions.jacoco.get()
    }

    dependencies {
        "errorprone"(rootProject.libs.errorprone.core)
        "testImplementation"(platform(rootProject.libs.junit.bom))
        "testImplementation"(rootProject.libs.junit.jupiter)
        "testImplementation"(rootProject.libs.assertj.core)
        "testRuntimeOnly"(rootProject.libs.junit.platform.launcher)
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
        options.errorprone.disableWarningsInGeneratedCode.set(true)
    }

    tasks.named<JavaCompile>("compileTestJava") {
        // Test configuration models are read reflectively; the check cannot see that.
        options.errorprone.disable("UnusedVariable")
    }

    tasks.withType<Javadoc>().configureEach {
        options.encoding = "UTF-8"
        (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:all,-missing", true)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        systemProperty("file.encoding", "UTF-8")
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        reports.xml.required.set(true)
    }

    tasks.named("check") {
        dependsOn(tasks.withType<Javadoc>())
    }
}
