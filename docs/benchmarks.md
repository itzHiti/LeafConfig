# Benchmarks

LeafConfig makes no comparative performance claims. The numbers below are a
baseline for spotting regressions in this code base only.

## Suite

`leafconfig-benchmarks/src/jmh/java/dev/leafconfig/benchmarks/LoadBenchmark.java`,
run with `./gradlew :leafconfig-benchmarks:jmh`. The configuration has six
top-level keys plus a `Map<String, Server>` with `entries` nested sections of
three keys each, so the document has roughly `3 * entries + 6` keys.

| Benchmark | What it measures |
|---|---|
| `coldDiscoveryAndLoad` | new `ConfigManager`, reflection schema discovery, one load of an unchanged file (parse, decode, validate, no write) |
| `noopReload` | `reload()` on a warm handle for an unchanged file: parse, decode, validate, merge check, no write |
| `mergeAndWriteReload` | write a file missing the six top-level keys, then `reload()`: parse, decode, validate, merge, atomic write |

## Baseline, 2026-09-21

Environment: JDK 21.0.12.1 (Corretto, OpenJDK 64-Bit Server VM 21.0.12.1+9-LTS),
Gradle 9.7.1, JMH 1.37, Windows 11, Intel Core i5-12500H, files on a local
disk in the system temp directory. Parameters: 1 fork, 3 warmup iterations of
10 s, 5 measurement iterations of 10 s, mode `avgt`, unit µs/op. Source:
`leafconfig-benchmarks/build/results/jmh/results.json` from that run.

| Benchmark | entries (keys) | avg µs/op | ± error (99.9%) |
|---|---|---|---|
| `coldDiscoveryAndLoad` | 1 (9) | 2056 | 6018 |
| `coldDiscoveryAndLoad` | 30 (96) | 1618 | 1376 |
| `coldDiscoveryAndLoad` | 300 (906) | 4109 | 5082 |
| `noopReload` | 1 (9) | 147 | 14 |
| `noopReload` | 30 (96) | 510 | 254 |
| `noopReload` | 300 (906) | 3505 | 435 |
| `mergeAndWriteReload` | 1 (9) | 3251 | 2296 |
| `mergeAndWriteReload` | 30 (96) | 3073 | 666 |
| `mergeAndWriteReload` | 300 (906) | 8999 | 2567 |

Reading the numbers:

- Error bars on the cold and write benchmarks exceed the mean; those runs are
  dominated by file-system latency on this machine (single fork, Windows temp
  directory, no I/O isolation). Treat them as order-of-magnitude only.
- `noopReload` is the most stable series and scales roughly linearly with key
  count (about 3.5 ms for ~900 keys).
- Allocation profiling (`-prof gc`) has not been run yet.

Regressions are investigated, not rejected on a single noisy run. Re-run with
more forks (`fork.set(3)`) before drawing conclusions.

## Artifact sizes, version 0.1.0

From `*/build/libs` after `./gradlew build :leafconfig-example:shadowJar`:

| Artifact | Bytes |
|---|---|
| `leafconfig-api-0.1.0.jar` | 23 589 |
| `leafconfig-yaml-0.1.0.jar` | 95 180 |
| `leafconfig-paper-0.1.0.jar` | 8 537 |
| `snakeyaml-engine-3.1.1.jar` (runtime dependency) | 307 329 |
| `leafconfig-example-0.1.0.jar` (shaded, relocated) | 459 489 |
