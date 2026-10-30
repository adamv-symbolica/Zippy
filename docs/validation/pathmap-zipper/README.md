# PathMap zipper and equality validation

The optimized backend was validated on a Symba h4d xlarge on 2026-10-30:
192 AMD EPYC 9B45 cores, Rust 1.99.1, Scala 3.8.1 and JDK 21.
The node was [terminated after artifact retrieval](node-termination.json).
[Environment](environment-final.json) and [benchmark source hashes](source-manifest.json)
identify the measured implementation. The [final source manifest](release-source-manifest.json)
and [node verification](release-source-verification.json) record the later cleanup.
The final cleanup only removed ignored
annotations, documented intentional concrete iterator methods, removed redundant
test conversions, and formatted a test assertion. The final source at `262a38c` passed the full replay, model, Clippy,
108 standalone exports, and all 51 benchmark jobs. The published per-example
tables use the earlier complete `b58ea64` benchmark run; the final-source
benchmark rerun is retained alongside it.

| Check | Result |
| --- | --- |
| Full Scala suite, including valued tests | 329 passed |
| Compiled native differential comparisons | 93,712 passed; zero failures or unfinished cases |
| Symbolic executions through each of three byte encodings | 30,877 per encoding |
| Direct digit executions | 597 bit and 484 byte comparisons |
| Independent Rust model | 3 tests; 1,200 random set pairs and compact 2^80 products |
| Upstream PathMap unit tests | 1,060 passed; one pre-existing ignored test |
| PR #172 differential tests | 6 passed |
| OpenFst regression tests | 17 passed; six overlap the full Scala suite |
| Generated optimization level 0 | 11 dedicated Rust tests passed |
| Standalone cornerstone programs | 108 compiled, executed, and checked exactly |
| Benchmark jobs | All 51 passed |
| Rust native matrix | 166 workloads × three encodings = 498 rows |
| Rust native microbenchmarks | 180 rows, including 48 paired equality measurements |
| Original evaluator reports | All 14 executed |
| Per-example evaluator measurements | 398; no missing cells |

The benchmark matrix checks 489 rows exactly. Nine large composition rows
compare exact cardinality against the Scala trie, avoiding enumeration of
hundreds of millions of paths. Every cornerstone result is checked exactly.

- [Full-suite and native audit](native-tests.json), [audit summary](audit-summary.json), [independent ledger check](replay-ledger-check.json), and [compressed replay ledger](differential-checks.jsonl.gz)
- [Published benchmark run](benchmark-summary.json), [coverage](benchmark-coverage.json), and [final-source rerun](release-benchmarks/README.md)
- [Per-example evaluator results](../../pathmap-benchmarks/README.md)
- [108 standalone program checks](standalone-export-checks.json)
- [Native model and regression counts](checks-summary.json), [final model log](release-model.log), and [upstream log](kernel-upstream.log)
- [Clippy](clippy.log) and [rustfmt](rustfmt.log)
- [GOL helper inlining audit](inline-audit.json)
- [All original evaluator reports](existing-reports/)
- [44 digit-reference runs](references/summary.json), [three extra evalZ runs](zipper-measured/summary.json), and [108 current C++ comparison rows](openfst/summary.json)

The previous complete baseline, including its exhaustive C++ benchmark matrix,
remains in [../pathmap](../pathmap/README.md). The current C++ comparison reruns
all 36 cornerstone and large cases across three dialects. The historical
exhaustive OpenFst test audit remains in [../openfst-graph](../openfst-graph/README.md).

Equality now avoids temporary result tries, with a shared-root/native structural
certificate and an exact read-zipper fallback. Fixpoints use `join_into` status.
[Paired timings](acceptor-micro.csv) include regressions: independently built
equal maps can be slower than two differences. See the
[performance notes](../../pathmap-benchmarks/performance.md) for the tradeoff.

No additional upstream correctness bug was found. `ProductZipper` does not have
the required concatenation semantics for valued prefixes;
[the reproducer output](product-semantics.log) records the distinction. Native
union repeats work on some compact shared DAG operands, limiting very large
suffix closures; [measurements](dag-union.csv) retain that performance limit.

The committed [standalone source archive](../../compiled-cornerstones-rust/README.md)
contains the complete runtime, dependency pins and local patches. The complete
raw evidence archive is retained locally at
[`output/pathmap-zipper/complete-evidence.tar.gz`](../../../output/pathmap-zipper/complete-evidence.tar.gz);
[evidence-archive.json](evidence-archive.json) records its checksum and contents.
