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
- Allocation profiling (`-prof gc`) was not part of this baseline.

Regressions are investigated, not rejected on a single noisy run. Re-run with
more forks (`fork.set(3)`) before drawing conclusions.

## Comparison 0.1.0 and 0.3.2-SNAPSHOT, 2026-09-25

Same machine, JDK, Gradle and JMH as above; same parameters (1 fork, 3 × 10 s
warmup, 5 × 10 s measurement, `avgt`), now with `-prof gc`, which the build
enables by default. Absolute times differ from the 2026-09-21 baseline because
machine load differs between days, so each pair was measured back to back:
`v0.1.0` from a git worktree, then the current code. The benchmark source is
identical in both. KB/op is `gc.alloc.rate.norm` divided by 1024.

| Benchmark | entries | 0.1.0 µs/op | 0.3.2-SNAPSHOT µs/op | 0.1.0 KB/op | 0.3.2-SNAPSHOT KB/op |
|---|---|---|---|---|---|
| `coldDiscoveryAndLoad` | 1 | 570 ± 33 | 624 ± 129 | 80.3 | 82.1 |
| `coldDiscoveryAndLoad` | 30 | 760 ± 59 | 970 ± 446 | 451.5 | 454.2 |
| `coldDiscoveryAndLoad` | 300 | 2542 ± 99 | 3098 ± 913 | 4028.5 | 3916.2 |
| `noopReload` | 1 | 118 ± 59 | 106 ± 15 | 58.5 | 59.2 |
| `noopReload` | 30 | 368 ± 296 | 301 ± 42 | 432.9 | 431.0 |
| `noopReload` | 300 | 2469 ± 402 | 2306 ± 369 | 3926.0 | 4051.6 |
| `mergeAndWriteReload` | 1 | 5099 ± 1647 | 3133 ± 1421 | 75.5 | 76.4 |
| `mergeAndWriteReload` | 30 | 4585 ± 1173 | 3038 ± 1314 | 612.6 | 615.2 |
| `mergeAndWriteReload` | 300 | 28701 ± 6730 | 6152 ± 964 | 5636.1 | 5769.8 |

Reading the numbers:

- The first run of this comparison showed `coldDiscoveryAndLoad` about
  690–850 µs/op slower in the current code with tight error bars (for 30
  entries 1034 ± 25 against 1725 ± 16), while allocations stayed within
  ±12 %. The cause was two `Path.toRealPath()` calls per `ConfigManager.load`,
  added in 0.3.0 to key handles by file; one call costs about 184 µs on this
  machine (measured separately, 5 000 calls on a temp file). The fix reuses the
  real path `SafePaths` already computes for its symbolic link check. The cold
  rows above are the re-run after the fix.
- After the fix the cold intervals overlap. The current code's intervals are
  wide, so one fork cannot show whether a smaller difference remains; that
  there is none cannot be verified from this evidence.
- `noopReload` and allocations per operation are unchanged within noise across
  migrations, renames, `@Secret` and collection merging (0.2.0 to 0.3.1).
  Reload does not go through `ConfigManager.load`, so the fix does not affect
  these rows.
- `mergeAndWriteReload` is dominated by file writes; the 0.1.0 series has very
  wide errors and the 300-entry gap is not attributed to code changes.

## Artifact sizes

From `*/build/libs`; 0.1.0 after `./gradlew build :leafconfig-example:shadowJar`,
0.3.2-SNAPSHOT after the `jar` tasks of the published modules and
`:leafconfig-example:shadowJar` on 2026-09-25.

| Artifact | 0.1.0 bytes | 0.3.2-SNAPSHOT bytes |
|---|---|---|
| `leafconfig-api` | 23 589 | 37 209 |
| `leafconfig-yaml` | 95 180 | 121 102 |
| `leafconfig-paper` | 8 537 | 8 537 |
| `snakeyaml-engine-3.1.1` (runtime dependency) | 307 329 | 307 329 |
| `leafconfig-example` (shaded, relocated) | 459 489 | 500 617 |
