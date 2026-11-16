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
The body runs at most once. A range whose cardinality bound is already at most
one keeps its original iteration, including the check that skips epsilon;
adding another selection would be redundant. The body is removed entirely only
when a headed source is proved. Reconstructing a head and its complete tail
group, including under an invariant prefix, instead reuses the source with
epsilon removed.

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

## Cornerstone regression cause and fix

The four Game of Life Fibonacci-bit regressions came from capping an invariant
iteration over `Range(S, k, k + 1)` with another
`Range(Subtraction(..., {epsilon}), 0, 1)`. The inner rank window already
contains at most one path. Fibonacci codewords do not preserve symbol order,
so the extra logical selection lowered to another ordered-selection/scanner
routine. Both native compilers inherited that work. The problem was in the
shared source pass, before encoding.

Revision `3fe2820` uses the existing overflow-safe
`RangeBounds.cardinalityUpperBound` to preserve every range bounded by one
path. The original iteration still skips epsilon. Wider and open ranges
retain their headed-existence cap. This covers positive, negative, mixed,
empty and extreme integer bounds without new runtime metadata or language
operations. Two new regression tests fail on the unfixed compiler and pass
with the fix: exhaustive range/epsilon semantics and a Fibonacci compiler
check requiring exactly one logical rank selector.

Controlled [ablations](validation/cornerstone-regressions/fix/ablations.csv)
compare the regressed pass, the narrow fix, disabling the invariant-iteration
rule, disabling the whole pass, and applying it only before or after encoding.
For both affected variants and both backends, the narrow fix emits
**byte-identical code to disabling the invariant-iteration rule**. Other
consumer/producer simplifications remain enabled. Applying the pass only
after encoding cannot repair a redundant cap already introduced when a fixture
was specialized. Rust optimization levels remain 0 for the source bit program
and 1 for its compile-pass variant. The symbolic source graph returns from
3,393 to 2,667 nodes.

Fresh h4d measurements compare regressed `02651f1` with fixed `3fe2820` in
both revision orders. These are medians of two process medians; each process
uses five adaptive native samples.

| Game of Life variant | Backend / encoding | Regressed ms | Fixed ms | Speedup |
|---|---|---:|---:|---:|
| Source 24×24 | PathMap / Fibonacci bits injected into bytes | 342.726 | 145.672 | 2.35× |
| Source 24×24 | OpenFst / Fibonacci bits | 422.436 | 181.780 | 2.32× |
| Compile-pass 24×24 | PathMap / Fibonacci bits injected into bytes | 103.800 | 62.013 | 1.67× |
| Compile-pass 24×24 | OpenFst / Fibonacci bits | 235.484 | 142.733 | 1.65× |

The full same-node comparison against the earlier `6ef74ef` baseline covers
all 22 primary variants and 14 supplementary large fixtures. All **434 paired
evaluator measurements pass exact-output checks**. The four fixed native
cells are within 1.5% of that baseline. Primary cases cover `eval`, `evalTrie`,
`evalZ`, `exec`, `execT`, bit/byte set references, four PathMap encodings and
three OpenFst dialects. Supplementary cases cover their trie/zipper references
and all seven native variants. `execZ` delegates to `evalZ` and is not counted
twice. Source puzzle and full 8-queens zipper cases are included.

The [per-example comparison](validation/cornerstone-regressions/fix/comparison.csv)
contains every evaluator's before/after time. [Individual measurements](validation/cornerstone-regressions/fix/measurements.csv)
retain graph sizes, generated bytes, session setup times and available raw
samples. [Replays](validation/cornerstone-regressions/fix/repeats.csv) include
opposite revision orders and identical-binary controls. Reference probes run
in separate JVMs pinned to eight CPUs, with at least three seconds of warmup;
candidate replays use ten seconds. The larger two-hop and product-restriction
trie candidates reverse direction between replays; the bit-reference puzzle
candidate also disappears on replay. Such JVM compilation and GC variation
is retained rather than selecting only favorable samples.
The temperature reference/`exec` candidate is sensitive to preceding fixture
compilation: constructing only that fixture brings both revisions to about
0.5 ms, within 4.7% on the first pair and 0.5% on the reverse pair. The
[fixture control](validation/cornerstone-regressions/fix/fixture-control.scala)
and all [control samples](validation/cornerstone-regressions/fix/fixture-control.csv)
are retained.

Scala and PathMap timings include result cardinality. OpenFst includes lazy
realization and counts afterward. Translation, encoding, compilation, decoding
and exact-output comparison are outside execution timing. Native session setup
can reuse compiler caches. Submicrosecond literal-graph ratios do not support
performance conclusions. The [previous report](validation/cornerstone-regressions/summary.json)
retains the original regression measurements.

On Java 21, Scala 3.8.1 and Rust/Cargo 1.99.1 with both native backends enabled:

- All **371 Scala/native tests pass**; [test log](validation/cornerstone-regressions/fix/tests.log.gz).
- All **3,189,440 reference translation checks pass**, covering 398,680 cases
  in each of eight directions. The [independent ledger verification](validation/cornerstone-regressions/fix/ledger-verification.json)
  checks uniqueness, completeness, failures and the full ledger checksum.
- All **252 regenerated standalone programs** (144 Rust and 108 C++) compile,
  execute and match independent Scala fixtures exactly; [oracle summary](validation/cornerstone-regressions/fix/standalone-oracle-checks.json).
  The [Rust](compiled-cornerstones-rust/README.md) and
  [C++](compiled-cornerstones/README.md) review archives contain the fixed code.

The corpus includes an additional untracked divergence suite, so a clean
checkout has fewer cases. The first audited suite completed its translation
ledger but hit an existing cold deep-trie `freeze` stack overflow with a
256 MiB stack. The unchanged baseline deep-trie suite and the final full
371-test run pass with 512 MiB. The [summary](validation/cornerstone-regressions/fix/summary.json)
records this distinction, source fingerprints, ablation identities and timings.

Reproduce with clean before/after checkouts on the same Linux node. Each
comparison compiles private source snapshots into immutable benchmark JARs,
so concurrent runs do not share mutable Scala build output.

```sh
python3 tools/check_cornerstone_regressions.py --before BEFORE --after AFTER \
  --output output/cornerstone-regressions/programs --workers 6 --warmup-ms 3000
python3 tools/check_cornerstone_regressions.py --before BEFORE --after AFTER \
  --output output/cornerstone-regressions/large --family large --workers 4 \
  --warmup-ms 3000
```
