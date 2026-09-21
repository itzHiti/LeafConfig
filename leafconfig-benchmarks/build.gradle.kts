import net.ltgt.gradle.errorprone.errorprone

plugins {
    alias(libs.plugins.jmh)
}

description = "JMH benchmarks; not part of check, run with ./gradlew :leafconfig-benchmarks:jmh"

dependencies {
    jmh(project(":leafconfig-yaml"))
}

jmh {
    jmhVersion.set(libs.versions.jmh.core.get())
    warmupIterations.set(3)
    iterations.set(5)
    fork.set(1)
    benchmarkMode.set(listOf("avgt"))
    timeUnit.set("us")
    resultFormat.set("JSON")
}

tasks.named<JavaCompile>("compileJmhJava") {
    // Benchmark configuration models are read reflectively.
    options.errorprone.disable("UnusedVariable")
}

tasks.withType<Javadoc>().configureEach { enabled = false }
tasks.named<Jar>("javadocJar") { enabled = false }
tasks.named<Jar>("sourcesJar") { enabled = false }
