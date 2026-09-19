description = "LeafConfig YAML backend: schema discovery, codecs, merge, persistence"

dependencies {
    api(project(":leafconfig-api"))
    implementation(libs.snakeyaml.engine)
}
