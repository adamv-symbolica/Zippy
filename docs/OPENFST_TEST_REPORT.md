# Optimized-graph / delayed OpenFst validation — 2026-10-29

The symbolic, bit and byte C++ backends pass all Scala tests and benchmark
jobs on an owned ephemeral Symba h4d xlarge node. The exhaustive native
differential audit remains incomplete: **248,109 comparisons passed, one
generated C++ unit hit the compilation timeout, and 64 comparisons remained
unfinished at the audit deadline. No semantic mismatch was reported.** The compiler
emits typed locals and native loops from optimized executor graphs; the runtime
uses delayed OpenFst acceptors. Reference evaluators supply the oracle.

## Full-suite results

[Machine-readable results, source hashes and environment](validation/openfst-graph/)
retain the complete scope. Raw ledgers and logs are preserved in the separate
[evidence archive index](validation/openfst-graph/evidence-archive.json).

| Check | Result |
|---|---:|
| Complete Scala suite with native regressions | 329 passed |
| Native differential comparisons | 248,109 passed / 248,174 scheduled; 1 compile timeout, 64 unfinished |
| Symbolic source frames scheduled in eight native directions | 30,886 |
| Additional direct bit/byte frames | 1,086 |
| Reference translation comparisons | 216,132; 0 failures |
| Reference source frames checked in seven directions | 30,876 |
| Source suite without native-only cases | 318 passed |

The three pre-existing ignored tests remain ignored: `Unification: double
transform`, `UnionFind: find`, and `Imperative: push out`. No new tests are ignored.

The audit schedules every captured symbolic frame through native symbols and
all seven translation routes; the incomplete cases above prevent a full-pass claim. Successful direct bit/byte executions are captured too.
The corpus includes examples, routines, abstract-analysis witnesses, trie tests,
optimizer checks and the dependent zipper fuzzer. Collection/replay occur
outside cost-counting and benchmark timing scopes.

The framework records two failure conditions: the C++ compilation timeout
(case 128510, a 9,745,496-byte generated unit) and the overall audit deadline.
The failed source and build log are retained in the raw evidence archive.
The configured audit limit was 4,800 seconds and the native compiler limit
was 1,800 seconds. Further validation is required for these 65 comparisons.

## Native model and exported programs

The independent `std::set` oracle passes **9,408 checks in each configuration**:

- GCC Release with upstream OpenFst 1.8.4;
- Clang ASan/UBSan with leak detection, including OpenFst and Abseil rebuilt
  with the same instrumentation;
- GCC Release with the legacy distro OpenFst 1.7.9 layout.

The model checks algebra, ordered loops, fixpoints, closures, range extremes,
Head, arbitrary/lexical codebooks and bounded Fibonacci selection, including
malformed and out-of-codebook words. It also checks delayed construction,
shared residuals, unproductive branches, persistent prefix edits and unchanged
old cursors. A range/Head regression rejects zero-cardinality residuals; its
negative control fails against the previous runtime. All 132 native
microbenchmarks also pass under ASan/UBSan.

All **108 standalone cornerstone translation units** compile and execute with
their exported input fixtures. Decoded results agree exactly across dialects
for all 36 cases. The benchmark matrix independently compares every exported
case/dialect exactly with the trie oracle. See
[export checks](validation/openfst-graph/cornerstone-export-checks.json),
[generated sources](compiled-cornerstones/README.md) and
[per-example measurements](cornerstone-benchmarks/README.md).

Native compiler regressions cover direct optimized graph/callee input, inline
iteration/fold/fixpoint control flow, lexical shadowing, ignored captures,
opaque functions and empty guards, lossless dotted/empty/UTF-16 constants,
stationary and mutual recursion, all 256 byte labels, deep routines, and range
selection from a compact `2^80`-path language. Fold framing tags are removed
before native emission and never enter bit/byte path values.

The three source `evalZ` cases previously omitted by the standard report now
have exact additional measurements. All **290 evaluator rows** in the
cornerstone comparison are measured, with no omitted runtime cells.
Python's eight proof-tool tests and all three root egglog models pass too.

## Reproduction

Pinned OpenFst revision: `5c887c63a882d608ec3010dba60ab25d57684b02`;
Abseil `20260817.0`. The node has 192 CPUs and approximately 1,488 GiB RAM.
Scala uses Scala CLI 1.18.0, Scala 3.8.1 with source compatibility 3.3,
munit 1.2.1 and scala-collection-contrib 0.3.0.

```sh
python3 tools/test_openfst.py --workers 64 --heap 384g --output /tmp/native-tests
python3 tools/test_radix_translations.py --workers 32 --heap 192g --output /tmp/reference-tests
```

The runners reject failed, missing, incomplete or unfinished checks and retain
per-case ledgers. C++ normally uses `-O2`; cold units above two million
characters use `-O0` unless overridden, with the runtime always built in
Release mode. Normal native stack allowance is 256 MiB; the benchmark runner
uses 1,024 MiB for deep translated folds.

These finite tests do not constitute an unbounded proof. Opaque Scala closures
use the explicit callback bridge unless native function bindings are supplied.
All cornerstone programs have zero callbacks. Benchmark timing protocols and
remaining encoded-operation costs are documented in
[the benchmark report](OPENFST_BENCHMARKS.md).
