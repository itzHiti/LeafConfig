# Contributing

This document is the engineering standard for this repository and applies to
humans and tools alike.

## Building

Requires an installed JDK 21. `JAVA_HOME` may point at an older JDK:
`gradle/gradle-daemon-jvm.properties` makes Gradle run its daemon on Java 21,
which Spotless needs, and Gradle finds the JDK among installed ones (for
example `~/.jdks` or the standard install locations). Always use the wrapper:

```bash
./gradlew clean check                      # full quality gate: compile with -Werror, Error Prone,
                                           # Spotless, tests, JaCoCo gate, Javadoc
./gradlew spotlessApply                    # format
./gradlew :leafconfig-yaml:test            # narrowest loop while iterating
./gradlew :leafconfig-example:shadowJar    # example plugin jar
```

## Golden files

YAML merge behaviour is proven by fixtures under
`leafconfig-yaml/src/test/resources/golden/<case>/{input.yml,expected.yml}`.
When a change alters rendering, re-record with

```bash
./gradlew :leafconfig-yaml:test -Dleafconfig.golden.record=true
```

then review every changed `expected.yml` by hand and explain the difference in
the commit message. Never update snapshots blindly.

## Documentation snippets

Every `java` block in `README.md` and `docs/` is checked by
`DocumentationSnippetsTest` in `leafconfig-example`. Put the code in a compiled
region (`// snippet-start: name` ... `// snippet-end: name`, usually in
`leafconfig-example/src/test/java/dev/leafconfig/example/snippets`) and add
`<!-- snippet: name -->` on the line above the block. A block that shows a
generated file uses `<!-- snippet-file: path -->`; a deliberately incomplete
fragment uses `<!-- snippet: illustrative -->`.

## Benchmarks

`./gradlew :leafconfig-benchmarks:jmh` runs the JMH suite (not part of
`check`). Results land in `leafconfig-benchmarks/build/results/jmh/results.json`.
Record methodology and numbers in `docs/benchmarks.md`; never quote a number
without JDK, Gradle version, parameters and hardware.

## Paper smoke test

`./gradlew :leafconfig-example:runServer` downloads the pinned Paper version
and starts it with the example plugin installed (network required, EULA is
accepted by the task). It is manual and not part of CI.

## Releasing

1. Set `version` in `build.gradle.kts` to the release version (no `-SNAPSHOT`;
   the workflow refuses snapshots and tags that do not match the version) and
   update `CHANGELOG.md`.
2. Commit, tag `vX.Y.Z`, push the tag.
3. The `Release` workflow runs `clean check` and `publishToMavenCentral`, which
   uploads a signed staging deployment to the Sonatype Central Portal. Release
   it manually there after checking the artifacts.
4. Bump `version` to the next `-SNAPSHOT`.

Required repository secrets (environment `release`): `MAVEN_CENTRAL_USERNAME`,
`MAVEN_CENTRAL_PASSWORD` (Central Portal user token), `SIGNING_KEY_B64` (the
ASCII-armored private key, base64-encoded into a single line, for example with
`base64 -w0 private.asc` or PowerShell
`[Convert]::ToBase64String([IO.File]::ReadAllBytes("private.asc"))`),
`SIGNING_KEY_PASSWORD`. Pull-request workflows never
receive them. Locally, `./gradlew publishToMavenLocal` produces unsigned
artifacts for testing.

## Pull requests

- Conventional commits: `feat(yaml): ...`, `fix(paper): ...`, `test(api): ...`.
- Describe the user-visible problem, the chosen behaviour and tradeoffs, tests
  added, compatibility impact, and YAML before/after when serialization changes.
- Every public API change needs Javadoc, tests, a README/docs update and a
  changelog entry.
- Do not add a dependency without documenting the problem it solves, why the JDK or
  existing dependencies are insufficient, its transitive dependencies, artifact-size
  impact, license and Paper class-loader implications.
