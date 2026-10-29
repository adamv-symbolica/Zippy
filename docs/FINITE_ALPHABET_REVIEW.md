# Finite-alphabet review follow-up

Historical round-two report; superseded by [round three](FINITE_ALPHABET_REVIEW_R3.md).

This addresses `feedback.md` and retains the techniques in `learnings.md`.
The review's referenced scratch probes were absent in this checkout, so its
E1–E5 inputs were reconstructed. `WordEncodingBenchmarks.scala` is the durable
probe; the regression and corpus suites exercise the production compiler and
evaluator, rather than a separate model.

## Changes

| Review item | Result |
| --- | --- |
| P1: growing order-recoding tables | Both interned encodings assign codes in symbol order. Interned `Range` compiles directly to target `Range`, with no helper routines or recoding. |
| P2: fold rescanning/stack overflow | Unchanged accumulators lower to one symbol iteration. Other folds select the least full path and decode only its head. Reference evaluation uses a trampoline. General changing-accumulator folds still scan the remaining set and can take quadratic work. |
| P2: nested tails intersection | Two lifted routines evaluate the source and its union of tails once. Depth 1–6 tests use a complete binary path set whose intersections remain nonempty, and assert exactly one evaluation of the instrumented input. |
| P3: conditional composition | Empty-or-singleton dispatch uses `Iteration` over the filtered test. Inactive continuations are never entered, including on the eager graph executor. Scanner call counts are bounded by codeword length plus two. |
| P4.1: unrolling | Enabled by default for small alphabets: at most 32 Fibonacci symbols inline their code trie and constant heads without routines. Bounded strings up to 32 bytes inline nested digit iterations, with one lifted body. Large alphabets and unbounded strings retain recursive scanners and bounds. |
| P4.2: byte zipper | Explicitly scoped as a set-scanning read cursor, not an evaluation backend. A byte-trie zipper evaluator is not implemented. |
| P4.3: string contract | Escaping is documented as an extension to plain UTF-8 plus NUL, needed to support embedded NUL. UTF-16 order repair is retained to preserve source semantics; the benchmark includes an ASCII-only native-order control. |
| P4.4: corpus and scale | 1,000 repository-fuzzer programs × two compile-once random inputs × eight routes (the seven translations and a composition). `Range` sizes 1,000/4,000/20,000; fold sizes 50/200/800/1,000/10,000. |
| P5: representation and walkers | Byte operations use unboxed arrays and unsigned comparisons; bit comparison aligns integer prefixes. The byte wrapper has no exposed backing array, case-class product or aliasing copy API. One binder-aware analysis supplies both free references and all reserved names. |

The generic AST, explicit exhaustive symbol converters, leading delimiter bit,
and algebra-only codeword traversal are retained. Exact length hints are
checked separately from maximum bounds on actual calls and dereferences in
the corpus. The byte immutability test uses 24 keys so it exercises a hashed
set rather than Scala's small-set representation.

Order-projection helpers remain for configurable symbol injections whose
names reverse digit order, and for open UTF-8 strings that must retain JVM
UTF-16 ordering. Neither helper is used by the intern-ID encodings. Custom
injection lookup uses balanced membership dispatch instead of a linear union
of one guard per symbol.

## Measurements

All times are from the supplied CPU-small host. The initial reproduction used
an 8 GiB heap and a 64 MiB JVM stack. The revised reference ran with an 8 MiB
stack. Initial observations are single samples; the durable report uses two
warmups and the median of five evaluations, excluding encoding/compilation.
They are operational evidence, not an asymptotic proof or a portable timing
threshold. Regression budgets are deliberately loose (30 seconds per region).

| Probe | Initial reproduction (ms) | Revised median (ms) |
| --- | ---: | ---: |
| Range, 1,000 paths, Fibonacci | 1,954.1 | 0.062 |
| Range, 1,000 paths, interned four-byte | stack overflow | 0.284 |
| Range, 4,000 paths, Fibonacci / interned four-byte | not attempted after overflow | 0.163 / 0.220 |
| Range, 20,000 paths, Fibonacci / interned four-byte | not attempted after overflow | 0.689 / 1.085 |
| Source symbol Range, 20,000 paths | — | 8.696 |
| Fold, 50 groups, interned four-byte / UTF-8 | 195.1 / stack overflow | 0.432 / 0.996 |
| Fold, 200 groups, interned four-byte / UTF-8 | 3,016.5 / stack overflow | 0.854 / 2.367 |
| Fold, 800 groups, interned four-byte / UTF-8 | stack overflow / stack overflow | 2.738 / 6.887 |
| Fold, 10,000 groups, interned four-byte / UTF-8 | — | 29.684 / 105.151 |
| Native byte Range, 20,000 paths × 32 bytes | 382.1 | 0.683 |
| Native byte composition, 300 × 300 | 220.5 | 23.171 |

For E3, the observed growth exponent from 200 to 800 groups was 0.84 for
interned four-byte encoding and 0.77 for UTF-8 (log time ratio / log 4).
These small-run values are affected by JVM optimization and allocation; the
structural regression is that each head is decoded once rather than every
remaining head being decoded at every step.

E1's 16-symbol Fibonacci iteration took 2.767 ms with the recursive scanner
and 1.077 ms with automatic code-trie inlining. Its generated routine count
falls from three to zero. At 4,000 symbols automatic mode deliberately keeps
the recursive scanner rather than expanding its state/depth call graph.
Sequential timing rows for that case are susceptible to JVM warmup and are
not evidence of a different algorithm.

E4's nonempty intersections at depths 1–6 took 2.313, 1.225, 1.150, 1.184,
1.294 and 1.250 ms respectively. At each depth the instrumented source was
evaluated seven times for seven total executions, rather than multiplying
with depth or group count.

The ASCII-only string `Range` control took 227.990 ms with UTF-16 order
repair and 0.728 ms with native byte order. This exposes the repair's real
cost. It remains necessary for open Unicode inputs if source ordering must
be preserved; the control is valid only because this particular input is
ASCII. No claim of equal cost for the open string route is made.

The unchanged-accumulator fold probe is E3 from the review. It is not evidence
that arbitrary sequential folds are linear: a separate changing-accumulator
1,000-group regression checks correctness and stack safety. Its remaining
set scans are explicitly documented above.

`execT` executes both recursive and bounded scanners after injection into
symbols. `execZ` executes bounded scanners with acyclic routines. General
recursive scanner functions are rejected by the existing zipper compiler as
outside its union-saturating fixed-point fragment; this is a backend
capability limit, not a fallback to the word evaluator.

## Reproduction

```bash
env _JAVA_OPTIONS='-Xmx8g -Xss8m' \
scala-cli test src/main/scala src/test/scala --server=false \
  --scala 3.8.1 --source 3.3 \
  --dependency org.scalameta::munit:1.2.1 \
  --dependency org.scala-lang.modules::scala-collection-contrib:0.3.0 \
  --test-only 'morkl.WordEncoding*Test'

env _JAVA_OPTIONS='-Xmx8g -Xss8m' \
scala-cli run src/main/scala src/test/scala/WordEncodingBenchmarks.scala \
  --server=false --scala 3.8.1 --source 3.3 \
  --dependency org.scala-lang.modules::scala-collection-contrib:0.3.0 \
  --main-class morkl.wordEncodingReviewReport
```
