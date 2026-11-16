# Consumer/producer simplification

`EvidenceElimination` implements the improvements described in
`~/Zippy_a_missing_laws_0.md`. It is the first shared source-optimization pass,
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
with the new one on the notes' evidence-elimination example. Both variants
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
  -- output/missing-laws-0/benchmark true
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
[Rust/C++ sources](validation/missing-laws-0/benchmark/) are retained.
Each source was also compiled separately and checked with headed,
epsilon-only, and mixed epsilon/headed inputs.
The benchmark process was pinned to h4d CPUs 144–151; replay used CPUs 0–143.

The [validation record](validation/missing-laws-0/README.md) contains the
successful 369-test result, all eight translation counts, source fingerprints,
and the independent verification of the 3,119,688-check replay ledger.
