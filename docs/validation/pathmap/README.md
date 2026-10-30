# Rust PathMap validation

The final implementation at `2248a98` passed on the Symba h4d xlarge on
2026-10-30. The node used 192 AMD EPYC 9B45 cores; benchmark jobs shared the
machine with validation. Its five-hour lease ended and the node is
[terminated](node-termination.json).

| Check | Result |
| --- | --- |
| Full Scala suite, including valued tests | 328 passed |
| Compiled Rust differential comparisons | 93,708 passed; zero failures or unfinished cases |
| Symbolic executions through each of three byte encodings | 30,877 per encoding |
| Direct digit executions | 597 bit and 480 byte comparisons |
| Independent Rust model tests | 3 passed, including 1,200 random set pairs and compact 2^80 products |
| Clippy with warnings denied / rustfmt | Passed |
| OpenFst regression tests | 17 passed; six overlap the full Scala suite |
| Standalone cornerstone Rust programs | 108 compiled and checked exactly |
| Benchmark jobs | All 51 passed |
| Native Rust benchmark matrix | 166 workloads × three encodings = 498 rows |
| Rust native microbenchmarks | 132 rows |
| Original evaluator reports | All 14 executed |

The benchmark matrix checks 489 rows exactly. Nine large composition rows
compare exact cardinality against the Scala trie to avoid enumerating hundreds
of millions of paths. Every cornerstone result is checked exactly.

- [Full-suite and native audit result](native-tests.json)
- [All benchmark jobs](benchmark-summary.json) and [coverage](benchmark-coverage.json)
- [Per-example results: 398 measured evaluator rows](../../pathmap-benchmarks/README.md)
- [108 standalone export checks](cornerstone-export-checks.json)
- [Native model, formatting and regression checks](checks/summary.json)
- [Toolchains, dependency pins and all 106 source hashes](environment.json)
- [All original evaluator reports](existing-reports/)
- [Additional bit/byte reference timings](cornerstone-radix/summary.json) and [zipper timings](cornerstone-zipper/summary.json)

The C++ comparison includes all 498 native rows and 132 microbenchmark rows on
the same node. Its microbenchmark setup initially lacked the CMake dependency
prefix; that job passed after correcting the environment. Both the
[initial result](openfst-comparison/initial-summary.json) and
[successful retry](openfst-comparison/micro-retry.json) are retained alongside
the [completed comparison](openfst-comparison/summary.json). The current
OpenFst regression check is separate from the historical exhaustive OpenFst
audit in `../openfst-graph/`.

The [raw evidence archive](../../../output/pathmap-validation/pathmap-complete-evidence.tar.gz)
contains the complete replay ledger, logs, benchmark shards, reference runs,
export compilation logs, and the validated source snapshot. Every source in
that snapshot matches its recorded SHA-256 hash. Its
[checksum and contents](evidence-archive.json) are committed; the full archive
is retained in the ignored local `output/` directory. The
[standalone Rust source archive](../../compiled-cornerstones-rust/README.md)
is committed in Git.
