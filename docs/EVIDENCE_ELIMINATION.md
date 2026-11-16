# Consumer/producer simplification

`EvidenceElimination` removes intermediate prefixes and repeated work when
an operation immediately consumes an iteration's output. It is the first shared source-optimization pass,
used by both supercompilers and the native graph-compilation pipeline. The four
symbol encodings also apply it to the entry expression and routine bodies
before lowering symbols to bits or bytes.

The pass moves `TailsUnion` and pure-prefix `Unwrap` into a producing iteration,
then cancels the part of each emitted prefix that the consumer removes. Prefix
cancellation handles associated concatenations and partial constant factors.
`TailsUnion` removes exactly one item: iteration heads have that exact length;
an upper length bound alone does not suffice. The units are those of the
current dialect, so eliminating a symbolic head before its four-byte encoding
does not accidentally remove just one byte.

An iteration whose body has no free dependence on its head or tail is reduced
to an iteration over `Range(Subtraction(source, {epsilon}), 0, 1)`. Subtraction
happens before selection, so an epsilon terminal cannot hide a headed branch.
The body runs at most once. It is removed entirely only when a headed source
is proved. Reconstructing a head and its complete tail group, including under
an invariant prefix, instead reuses the source with epsilon removed.

Capture checks use `Matching.freeRefs`, `Matching.freeMentions`, and
`Matching.subst`. Moving an external prefix under a colliding binder triggers
capture-avoiding renaming. Ignored `_` binders neither capture external values
nor confer head-length facts on them. Opaque callbacks and unresolved calls
are excluded from rewrites that would change their evaluation multiplicity.

The consumer and producer are simplified together. Distribution over a union
is accepted only if the resulting expression is smaller after cancellation,
deduplication, and common-prefix factoring. Unchanged producer branches remain
shared. Traversal, projection, purity, and cost analyses memoize expression
nodes; the cost comparison saturates rather than overflowing. An arbitrary
iteration is never distributed over its source union, since its body can
observe the combined tail group for a shared head.

No language cases or runtime metadata were added. Native lowering recognizes
the headed-existence guard directly and removes its redundant range before
emitting `NativeHasHead`. This retains the existing proof and bulk operation
for length-prefixed symbol removal, including the OEIS workload.

## Validation and measurement

The focused suite checks all 16,384 pairs of spaces over two symbols with
maximum path length two against an independent set oracle. It also checks
capture, nested shadowing, ignored binders, zero/one/two-item prefixes, bounded
lengths, union growth, shared nodes, opaque evaluation counts, all four symbol
encodings, and the counterexample to source-union distribution.

`EvidenceEliminationBenchmark.scala` compares the previous source pass list
with the new one on a nested prefix-production and removal example. Both variants
receive the same preconstructed inputs. The previous optimizer already
eliminates the inner reconstruction iterator; it still constructs the outer
evidence prefixes before dropping them. The new optimizer also removes those
prefixes and caps the remaining outer iteration at one surviving path.

Each measurement is a median of five warmed samples. Input construction,
compilation and result enumeration are outside the timed region. PathMap's
native timing includes exact result counting. OpenFst's includes realization
of the lazy automaton; its inspection/count runs afterward. Scala timings
cover execution without a separate count. Every variant is checked against
the expected complete output set. Native variants compile their optimized
execution graphs directly, so
the baseline cannot accidentally receive the new source pass again. The
PathMap experiment uses byte heads; OpenFst and Scala use symbol heads.
The report includes all six Scala evaluator entry points (`execZ` currently
aliases `evalZ`) and both native transpilers. Ratios compare each evaluator
with its own baseline, rather than comparing encodings across backends.
Output construction and retained roots still have a cost: these measurements
do not assert constant physical space or time for every backend.

Reproduce with:

```sh
scala-cli run src/main/scala src/test/scala --server=false --scala 3.8.1 \
  --source 3.3 --dependency org.scalameta::munit:1.2.1 \
  --dependency org.scala-lang.modules::scala-collection-contrib:0.3.0 \
  --main-class morkl.evidenceEliminationBenchmarkReport \
  -- output/evidence-elimination/benchmark true
```

## h4d results

Median milliseconds for the 256-head case (five warmed samples):

| Evaluator | Previous passes | With evidence elimination | Speedup |
|---|---:|---:|---:|
| eval | 20.372278 | 0.042653 | 477.63× |
| evalTrie | 0.242549 | 0.001248 | 194.35× |
| evalZ | 2276.829185 | 0.120078 | 18961.25× |
| exec | 28.529664 | 0.045302 | 629.77× |
| execT | 0.283124 | 0.001333 | 212.40× |
| execZ | 2277.546491 | 0.134048 | 16990.53× |
| pathmap | 5.949080 | 0.026997 | 220.36× |
| openfst-symbols | 0.363602 | 0.017458 | 20.83× |

All 48 measurements (16, 64, and 256 heads) and the generated
[Rust/C++ sources](validation/evidence-elimination/benchmark/) are retained.
Each source was also compiled separately and checked with headed,
epsilon-only, and mixed epsilon/headed inputs.
The benchmark process was pinned to h4d CPUs 144–151; replay used CPUs 0–143.

Validation on an AMD EPYC 9B45 h4d node used Java 21, Scala 3.8.1 and
Rust/Cargo 1.99.1, with PathMap and OpenFst enabled. All 369 tests and
3,119,688 reference translation checks passed; see the [test summary](validation/evidence-elimination/result.json)
and [independent ledger verification](validation/evidence-elimination/ledger-verification.json).
The corpus included an additional untracked divergence suite, so a clean
checkout has fewer cases. Translation replay checks the reference evaluators;
native backend correctness is covered separately by the compiled tests.

## Cornerstone regression check

The 2026-11-16 h4d comparison runs `6ef74ef` against `018bcdb`: all 22 primary
variants and all 14 supplementary large fixtures. All **434 paired evaluator
measurements pass exact-output checks**. The primary matrix covers `eval`,
`evalTrie`, `evalZ`, `exec`, `execT`, bit/byte set references, four PathMap
encodings and three OpenFst dialects. The supplementary matrix covers its
existing trie/zipper evaluators and all seven native variants. `execZ` delegates
to `evalZ` and is not counted twice. Previously omitted source puzzle and
8-queens zipper cases are included.

Four substantial native regressions reproduce in both revision orders:

| Game of Life variant | Backend / encoding | Before ms | After ms | Slowdown |
|---|---|---:|---:|---:|
| Source 24×24 | PathMap / Fibonacci bits injected into bytes | 144.055 | 339.728 | 2.36× |
| Source 24×24 | OpenFst / Fibonacci bits | 178.895 | 421.023 | 2.35× |
| Compile-pass 24×24 | PathMap / Fibonacci bits injected into bytes | 61.977 | 103.561 | 1.67× |
| Compile-pass 24×24 | OpenFst / Fibonacci bits | 142.303 | 231.613 | 1.63× |

These are medians of three process medians, including two isolated repeats
with opposite revision orders. Each native process uses the existing five
adaptive timing samples. Rust's optimization caps remain unchanged: level 0
for the source bit program and level 1 for its compile-pass variant. The
symbolic source graph also grows from 2,667 to 3,393 nodes. The exact cause of
the native slowdown remains to be isolated; source size alone does not explain
it, since the compile-pass native sources shrink.

Reference measurements use independent JVMs, pinned eight-CPU sets, a 192 GiB
heap, at least three seconds of warmup and five adaptive batches targeting 50 ms each.
Scala and PathMap timings include result cardinality; OpenFst includes lazy
realization and counts afterward. Translation, input encoding, compilation,
exact-output comparison and decoding are outside execution timing. Native
session setup can reuse compiler caches and is recorded separately. One-node
literal graphs are flagged in the measurements; their submicrosecond ratios
are not treated as performance regressions.

Shorter reference runs showed JVM compilation transitions and GC variation;
the retained replays include both favorable and unfavorable samples. A large
two-hop trie candidate falls from 1.80× to 1.02× after a ten-second warmup.
The graph-query and Datalog byte-reference candidates also fail to reproduce
as slowdowns in isolation. Smaller reference differences should be interpreted
with that variation in mind.

The [per-example comparison](validation/cornerstone-regressions/comparison.csv)
contains every evaluator's before/after time. [Individual measurements](validation/cornerstone-regressions/measurements.csv)
retain graph sizes, source bytes, session setup times and available raw samples;
[replays](validation/cornerstone-regressions/repeats.csv) and the
[strict summary](validation/cornerstone-regressions/summary.json) record the
confirmation evidence. All four native performance regressions remain present
in `018bcdb`.

Reproduce with clean before/after checkouts on the same Linux node:

```sh
python3 tools/check_cornerstone_regressions.py --before BEFORE --after AFTER \
  --output output/cornerstone-regressions/programs --workers 6 --warmup-ms 3000
python3 tools/check_cornerstone_regressions.py --before BEFORE --after AFTER \
  --output output/cornerstone-regressions/large --family large --workers 4 \
  --warmup-ms 3000
```
