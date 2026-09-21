# Benchmarks

No benchmarks have been run yet. LeafConfig makes no performance claims.

Planned: a JMH suite kept separate from the unit tests
recording cold and cached schema discovery, small/medium/large file load, no-op
reload, one-value reload, merge of new defaults, allocation rate and artifact
size. Every published number will state JDK, Gradle version, fixture size,
warmup, forks and the measured metric. Regressions are investigated, not
rejected on a single noisy run.
