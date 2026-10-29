> Historical eager-runtime results, before the optimized-graph/lazy-automaton change.

# OpenFst backend validation — 2026-10-29

The symbol, bit, and byte C++ backends were validated on an owned, ephemeral
Symba `cpu-h4d-xlarge` node. The compiler emits ordinary C++ functions and the
runtime stores spaces as OpenFst acceptors. Reference evaluators provide the
expected answers; native execution computes every checked result.

## Full-suite coverage

Final machine-readable results are in [the validation directory]().
The full per-case ledgers and logs are preserved in a separate compressed
evidence archive; its checksum is recorded with the validation summaries.

| Check | Result |
|---|---:|
| Complete Scala suite with native regressions | 320 passed |
| Native differential replay | 248,098/248,098; 0 failures |
| Symbolic source frames, each checked in 8 native directions | 30,878 |
| Direct bit/byte native frames | 1,074 |
| Reference translation replay | 216,083; 0 failures |
| Reference source frames, each checked in 7 directions | 30,869 |

Three pre-existing ignored tests remain ignored: `Unification: double transform`,
`UnionFind: find`, and `Imperative: push out`. No new tests are ignored.


The native replay includes the symbolic backend and all seven translation
routes for every captured symbolic frame, plus direct bit and byte executions.
It covers the repository's reference examples, routines, abstract-analysis
witnesses, optimizer checks, trie tests, and dependent zipper fuzzer. Collection
and replay run outside cost-counter and benchmark timing scopes.

Native-specific regressions cover all operations, all 256 byte values, epsilon
versus byte zero, Unicode and unpaired-surrogate ordering, dynamic symbols,
grounded calls in every input/output shape, lexical shadowing, mutual and
stationary recursion, deep generated routines, and exact range slicing from a
compact `2^80`-path acceptor. A 4,097-codeword fold checks direct prefix-selector
lowering and unused helper pruning.

## Independent native and export checks

The independent C++ `std::set<vector<int>>` oracle passes **8,800 checks** in
each configuration:

- GCC release against upstream OpenFst 1.8.4 and its matching Abseil closure;
- Clang with AddressSanitizer, UndefinedBehaviorSanitizer, and leak detection,
  with OpenFst and Abseil themselves rebuilt under the same sanitizers;
- the legacy distro OpenFst 1.7.9 header/library layout.

The model generates random finite languages and checks algebra, ordered folds,
iteration, fixpoint, closures, range extremes, Head, arbitrary and lexical
codebook selection, and bounded Fibonacci selection on exact and prefix
queries, including malformed codewords and unknown codes.

An exported generated C++ source was built independently through CMake and run
without a JVM. Its native grounded-function binding returns exactly
`{("tail"), ("other")}`; the host callback is deliberately unusable in the source
regression. Python's eight proof-tool tests and the three root egglog models
(`formal.egg`, `zipper.egg`, `zipper-descend.egg`) also pass.

## Environment and reproduction

Pinned OpenFst revision:
`5c887c63a882d608ec3010dba60ab25d57684b02`; Abseil tag `20260817.0`.
The node provides 192 CPUs and approximately 1,488 GiB RAM. Scala tests use
Scala CLI 1.18.0, Scala 3.8.1 with source compatibility 3.3, munit 1.2.1, and
scala-collection-contrib 0.3.0. Generated C++ normally uses `-O2`; cold units
above two million characters
use `-O0` unless overridden, while optimized cached artifacts are retained.
The shared runtime uses CMake Release optimization. The full native replay uses 96 workers and a
384 GiB JVM heap. The normal native stack budget is 256 MiB; the deepest
benchmark folds use a 1,024 MiB budget.

```bash
python3 tools/test_radix_translations.py --heap 256g --workers 48 \
  --output /tmp/reference-translations
python3 tools/test_openfst.py --heap 384g --workers 96 \
  --output /tmp/native-translations
```

The runners reject failed, missing, incomplete, or unfinished comparisons.
They preserve per-case ledgers. Native audit batches are bounded by both case
count and source size. Preparation and native execution use separate worker
pools so oversized chunks from one capture batch can compile independently. See [backend setup](../../OPENFST_BACKEND.md) for building the
pinned dependencies and running CTest.

## Timing

Final exhaustive validation exceeded the requested five-hour window. The
implementation commits, test logs, and environment record retain the actual
timestamps; the main delay was compilation of expanded units reaching roughly
100 MB. The final compiler supports a separate cold-large-unit optimization
level, and the audit schedules independent chunks in parallel.

## Scope

These are finite test and differential results, not an unbounded proof of the
backend. Opaque JVM closures use the explicit callback bridge unless native
C++ bindings are supplied; no reference space evaluator substitutes for native
operations. Benchmark results and measurement limitations are documented
[separately](BENCHMARK_REPORT.md).
