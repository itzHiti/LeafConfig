description = "LeafConfig YAML backend: schema discovery, codecs, merge, persistence"

dependencies {
    api(project(":leafconfig-api"))
    implementation(libs.snakeyaml.engine)
}

tasks.test {
    // Forward -Dleafconfig.golden.record=true from the Gradle command line to the test JVM.
    systemProperty("leafconfig.golden.record", System.getProperty("leafconfig.golden.record", "false"))
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
