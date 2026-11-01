# Adiabatic cursor reuse, length prefixes and OEIS validation

Validated on 2026-11-01 on an owned ephemeral Symba h4d xlarge: 192 AMD EPYC
9B45 CPUs and 1,488 GiB RAM. The measured compiler/runtime is `fdf15dd`.
The reference-only replay used `0eb357f`; the final changes after that snapshot
only adjust native Rust bulk operations. The C++ matrix used `76e2166`; the
108 current C++ exports are byte-identical to the prior review bundle and were
compiled and checked again. [Environment](environment.json),
[source hashes](source-manifest.json), and [node verification](source-verification.json), [final executable-input check](final-source-verification.json), and [report/archive verification](report-verification.json)
identify the inputs. All 195 recorded files matched the measured node snapshot.

| Check | Result |
| --- | --- |
| Full Scala suite with native PathMap tests and valued tests enabled | 333 passed |
| Reference translation comparisons | 247,048 passed: 30,881 frames through all eight routes |
| Compiled PathMap differential comparisons | 124,652 passed; zero failures or unfinished cases |
| Symbol frames per compiled encoding | 30,881 each: interned, terminated, length-prefixed, Fibonacci bits |
| Direct digit comparisons | 597 bit and 531 byte cases |
| Independent Rust model | 4 passed; 1,200 random set pairs and compact 2^80 products |
| Vendored upstream unit tests | 1,070 passed; two pre-existing ignored tests |
| Python tooling tests | 8 passed |
| Root egglog models | formal, zipper and zipper-descend passed |
| Clippy / rustfmt | passed; warnings denied for the runtime and its tests |
| Rust standalone cornerstones | 144 compiled, executed, and checked exactly |
| C++ standalone cornerstones | 108 compiled, executed, and checked against the independent Scala trie fixtures |
| Rust benchmark jobs | All 52 passed: 14 original reports, 36 native shards, microbenchmarks and reuse ablation |
| C++ benchmark jobs | All 37 passed: 36 native shards and microbenchmarks |
| Rust native matrix | 166 workloads × four encodings = 664 rows |
| C++ native matrix | 166 workloads × three dialects = 498 rows |
| Native microbenchmarks | 180 Rust and 132 C++ rows |
| Controlled cursor reuse comparisons | 18 rows, five samples each |
| Additional cornerstone evaluators | 44 digit-reference and three previously omitted evalZ measurements |
| Per-example measurements | 434; no missing cells |
| Full OEIS reproduction | 375,842 records; 150 timing rows; exact key-set agreement |

The Rust matrix checks 652 rows exactly. Twelve large composition rows check
exact cardinality without enumerating hundreds of millions of paths. The C++
matrix similarly checks 489 rows exactly and nine by cardinality. All 252 native
cornerstone exports were checked against independent Scala trie results.
The C++ export checker first compared all three decoded dialects; their result
digests were then matched to the independent fixtures in the Rust bundle.

The native and reference replay ledgers were independently checked for complete,
nonduplicated comparisons. Source evaluations that intentionally throw are not
successful frames; the reference replay reported zero unexpected source errors.
No active tests were selected out by the full-suite runners. Upstream ignored
tests are `experimental::tree_serialization::tests::tree_serde_2` and
`prefix_zipper::tests::prefix_zipper_fork_at_focus`; both files are unchanged from
the baseline.

- [Native suite and audit](native-tests.json), [audit summary](audit-summary.json), [independent ledger check](replay-ledger-check.json), [compressed ledger](tests-checks.jsonl.gz), [full test log](tests.log.gz)
- [Eight-route reference replay](radix-tests.json), [summary](radix-summary.json), [ledger check](radix-ledger-check.json), [compressed ledger](radix-checks.jsonl.gz), [test log](radix.log.gz)
- [Complete benchmark coverage](benchmark-coverage.json), [final Rust run](after/summary.json), [baseline](before/summary.json), [C++ run](openfst/summary.json), [original evaluator reports](after/existing/)
- [Rust export checks](standalone-export-checks.json), [C++ export checks](cpp-standalone-export-checks.json), [C++ independent-oracle check](cpp-oracle-checks.json)
- [Independent model](model.log), [upstream tests](upstream.log), [Clippy](clippy.log), [rustfmt](rustfmt.log), [Python tests](python-tests.log)
- [Controlled reuse rows](cursor-reuse.csv), [reference evaluators](references/summary.json), [additional evalZ runs](zipper/summary.json)
- [OEIS samples and metadata](oeis/summary.json), [single-worker regression checks](isolated-regressions/summary.json)
- [Per-example tables](../../pathmap-adiabatic-benchmarks/README.md), [before/after matrix and limitations](../../pathmap-adiabatic-benchmarks/performance.md), [OEIS report](../../OEIS_BENCHMARK.md)

Native timings include graph execution and exact result cardinality. Compilation,
initial input construction, protocol IO and output enumeration are outside timing.
The original evaluator reports retain their own warmup policies. The before/after
matrix is a throughput run, with eight baseline workers and four final workers;
validation and exports used other cores. The controlled reuse ablation uses the
same runtime and counting implementation on both sides. Separate sequential,
CPU-pinned repetitions check the two largest apparent regressions. The interned
80-node Datalog case remains approximately 1.92× slower; the other apparent large
regression did not reproduce in the sequential checks. No result rows were replaced.

Development checks caught and fixed an extracted-region mutability error and a
quadratic ordered length-prefix fold. Direct per-byte head writes and per-branch
tail accumulation were replaced with adaptive native bulk operations after their
benchmarks regressed. An initial C++ setup lacked Boost headers and then reused a
CMake cache from another source directory; both were corrected. A reused test
output directory was rejected and the final audit reran in a fresh directory.
Native microbenchmarks now use a private Cargo target directory to avoid stale
artifacts after source transfers.

No additional PathMap correctness defect was found. The new checked cardinality
method is a local performance extension with an exact overflow fallback;
[dependency provenance](../../../src/main/rust/pathmap/vendor/pathmap/UPSTREAM.json)
records both local patches. Earlier ProductZipper and native DAG-union limitations
remain described in [the backend notes](../../PATHMAP_BACKEND.md).

Reproduce from the repository root (use fresh output directories):

```sh
python3 tools/test_pathmap.py --output output/pathmap-tests --workers 24 --batch 64 --heap 96g
_JAVA_OPTIONS='-Dmorkl.pathmap.test=true' python3 tools/test_radix_translations.py \
  --output output/radix-tests --workers 48 --heap 256g
python3 tools/benchmark_pathmap.py --output output/pathmap-benchmarks --workers 4 --shards 4
python3 tools/benchmark_openfst.py --output output/openfst-benchmarks --workers 4 --shards 4 --native-only
python3 tools/benchmark_oeis.py --data /path/to/oeis_stripped.txt --output output/oeis --samples 5
```

The committed [Rust archive](../../compiled-cornerstones-rust/README.md) restores
144 cornerstone programs, the OEIS programs/harness and the ablation sources.
The [C++ archive](../../compiled-cornerstones/README.md) restores all 108 C++
programs. Raw logs, inputs, generated code and source snapshots are retained
locally in `output/pathmap-adiabatic/`. The large OEIS input and build caches are
not committed. The [node termination record](node-termination.json) is retained
after artifact retrieval.
