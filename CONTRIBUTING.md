# Contributing

This document is the engineering standard for this repository and applies to
humans and tools alike.

## Building

Requires JDK 21. Always use the wrapper:

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

## Pull requests

- Conventional commits: `feat(yaml): ...`, `fix(paper): ...`, `test(api): ...`.
- Describe the user-visible problem, the chosen behaviour and tradeoffs, tests
  added, compatibility impact, and YAML before/after when serialization changes.
- Every public API change needs Javadoc, tests, a README/docs update and a
  changelog entry.
- Do not add a dependency without documenting the problem it solves, why the JDK or
  existing dependencies are insufficient, its transitive dependencies, artifact-size
  impact, license and Paper class-loader implications.
