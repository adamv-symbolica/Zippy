# Standalone Rust cornerstone programs

The review directory is [`output/pathmap-adiabatic/cornerstones/`](../../output/pathmap-adiabatic/cornerstones/README.md).
It contains **144 complete Rust programs** for **36 cases**: 22 cornerstone
variants and 14 supplementary large cases, each compiled with interned bytes,
terminated strings, length-prefixed strings, and Fibonacci bits injected into bytes.

Each case includes its symbolic source, four `.rs` translation units, optimized
executor graph dumps, exact wire inputs, a codeword dictionary, and an independent
Scala trie result in `expected.json`. Generated loops use borrowed cursor paths
and adiabatic cursor descent where the graph proves it. Output unions share a
write zipper. The dictionary is for inspection and checking; runtime spaces
contain only encoded byte paths and unit terminals.

The pinned PathMap runtime, PR #172 restriction fix, structural-equality and
checked-count patches, Cargo.lock and CPU configuration are included in `runtime/`.
The expanded README contains compilation commands. All **144 sources compiled,
executed, and matched the independent Scala trie exactly** on the h4d.
[Export evidence](../validation/pathmap-adiabatic/standalone-export-checks.json)
records every source hash and compilation level.

The archive also contains [15 OEIS source files](../../output/pathmap-adiabatic/oeis/sources/)
(fourteen stage programs and their comparison harness) and
[18 cursor-reuse ablation sources](../../output/pathmap-adiabatic/after/reuse/cursor-reuse/).
The [per-example results](../pathmap-adiabatic-benchmarks/README.md),
[before/after measurements](../pathmap-adiabatic-benchmarks/performance.md), and
[OEIS report](../OEIS_BENCHMARK.md) describe their execution.

Restore the committed [portable source archive](cornerstone-rust-review.tar.gz):

```sh
mkdir -p output
tar -xzf docs/compiled-cornerstones-rust/cornerstone-rust-review.tar.gz -C output
```

[manifest.json](manifest.json) records the 144 cornerstone source hashes,
optimization levels and graph sizes. [archive.json](archive.json) records the
archive checksum and source totals. The measured compiler/runtime is `fdf15dd`.
