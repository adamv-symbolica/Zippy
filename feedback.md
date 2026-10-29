# Review of `.review` at `4037d13`

The completion claim still does not hold. I reviewed `git diff 5afad8a HEAD`, the follow-up commit `f2c42c7`, the surrounding symbol evaluator and interner, and the submitted tests. I did not change implementation files. `ZippyCodedCheck` passes **12/12 tests**, including its 3,000 corpus frames. Separate probes nevertheless reproduce unsupported requested translations and stack overflows on small valid programs.

The follow-up fixes are substantial: the generic AST mirrors this checkout's symbol constructors; string encoding now handles every Java UTF-16 string without losing order; fresh names are reserved; ignored binders and fold shadowing have regressions; decoder inputs are evaluated once; byte keys have defensive construction/extraction; and fixed-width annotations are no longer converted twice. Preserve these improvements. A byte zipper is optional in the original request, so its absence is not a mandatory gap.

Runnable probes and full output are preserved in `/home/ubuntu/polemic-notes/zippy-coded-review-next/`:

```bash
cd /home/ubuntu/workspace/.review
PROBES=/home/ubuntu/polemic-notes/zippy-coded-review-next
# ReviewNext.scala / morkl.ReviewNext: ordered-code rejection, fold and long-head sweeps
# DeepNext.scala / morkl.DeepNext: actual original eval, JVM flags, stack traces, bound construction
# NativeByteFold.scala / morkl.NativeByteFold: native byte Fold -> bit-language expansion
# Substitute the chosen source and main below.
env _JAVA_OPTIONS='-Xmx8g -Xss64m' scala-cli run src/main/scala \
  "$PROBES/DeepNext.scala" --server=false --scala 3.8.1 --source 3.3 \
  --dependency org.scala-lang.modules::scala-collection-contrib:0.3.0 \
  --main-class morkl.DeepNext
```

`results.log`, `deep.log`, `deep2.log`, `byte-fold.log`, and `suite.log` contain the results. Timings below are diagnostic single runs, not statistically controlled benchmark claims. The deep probe prints JVM input arguments and confirms `-Xss64m` was applied.

## P1 — Complete ordered operations for the actual intern-ID codecs

**Evidence.** `ZippyCoded.scala:650–654, 674–676, 688–690` rejects nontrivial `Range` and every `Fold` whenever `orderPreserving` is false. Both `FibInterner` and `InternerId4` set it false (`:520, :571`). This is an explicit omission of constructors from two requested translation routes, rather than an implementation of those routes.

Minimized executed counterexample:

```scala
import morkl.*
import morkl.Codes.*
val source: SymSpace = ZSpace.Range(
  ZSpace.Literal(Set(Vector("z"), Vector("a"))), 0, 1)
assert(ZEval.syms()(source) == Set(Vector("a")))
CodedTranslation(InternerId4, source) // UnsupportedOperationException
CodedTranslation(FibInterner(Interner.size + 10), source) // same failure
```

Expected: translated programs evaluating to the encoding of `{"a"}`. Observed: compilation throws a named error directing the caller to a different dictionary/string codec. The violated invariant is availability of the requested symbol-to-interned-bit/byte translations for the complete symbol constructor set. Rejecting wrong order is better than silently returning the wrong value, but it is not completion.

**Requested change.** Keep stable `Interner` IDs, and implement the missing ordering lowering using existing operations and routines. For ordered programs, accept an explicit frozen admissible-symbol/interner snapshot; compile its symbol order into a reusable ordinary target recoder/comparator or other order projection. Do not change ID bytes to sorted dictionary ranks and call that the stable-ID route. Distinguish a closed ordering contract from unrestricted future interning: arbitrary future symbol names cannot have their lexical order inferred from an integer alone. The literal example above already supplies a finite known vocabulary and must work.

**Validation.** Remove the exclusion in `ZippyCodedCheck.scala:84–90` that omits actual interner codecs precisely when a program contains `Range`/`Fold`. Run ordered differential tests through these codecs, including opposite interning/lexical order, accumulator-sensitive folds, negative ranges, mutual routines and ordered expressions inside grounded path arguments. Compile once for a supplied snapshot, execute several admissible input frames, and assert unchanged four-byte ID equality with `Interner.intern` before and after unrelated interning. Benchmark the actual stable-ID route at 1,000/4,000/20,000 paths. No new grounded callback may be introduced into a pure program.

## P1 — Make the reference execution stack-safe; translated Fold and one long head currently crash

**Evidence A: Fold.** `ReviewNext.scala` runs

```scala
val h = PathRef("h"); val a = PathRef("a"); val t = SpaceMention("t")
val values = (0 until 200).map(i => Vector(f"s$i%06d")).toSet
val source: SymSpace = ZSpace.Fold(
  ZSpace.Literal(values), ZPath.Constant(Vector.empty), a, h, t,
  ZSpace.Singleton(ZPath.Deref(h)), ZPath.Deref(a))
val (main, rs) = CodedTranslation(Codes.Dictionary4(values.flatten.toVector), source)
ZEval.bytes(rs)(main)
```

Expected: all 200 one-symbol paths, encoded as four-byte words. Observed: `StackOverflowError`. `DeepNext.scala` separately confirms the original `eval(ZSym.to(source))` returns 200 paths. Across the executed sweep, dictionary-coded Fold failed at 200, 800 and 4,000 groups; string-coded Fold already failed at 50 groups. Source symbol evaluation completed all those sizes. This is ordinary input, with an unchanged accumulator and no source recursion.

**Evidence B: one string head.** The failure is not confined to folds:

```scala
val h = PathRef("h")
val source: SymSpace = ZSpace.Iteration(
  ZSpace.Literal(Set(Vector("a" * 512))), h, SpaceMention("t"),
  ZSpace.Singleton(ZPath.Deref(h)))
val (main, rs) = CodedTranslation(Codes.StringBytes, source)
assert(eval(ZSym.to(source)).paths.size == 1)
ZEval.bytes(rs)(main) // StackOverflowError
```

The isolated sweep completes original `eval` through 512 characters; the translated 512-character case overflows after about 3,200 AST dispatches, even with a 64 MiB JVM stack. Shorter failure thresholds vary with JVM warmup, so 512 is a reproduced witness, not a portable threshold. The result should be the singleton encoded head of length 513 bytes. Both examples violate preservation of terminating source computations on modest finite inputs.

**Evidence C: the mandatory bytes-to-bits route fails at the native alphabet limit.** `NativeByteFold.scala` uses:

```scala
val source: ByteSpace = ZSpace.Fold(
  ZSpace.Literal((0 until 256).map(i => Vector(i.toByte)).toSet),
  ZPath.Constant(Vector.empty), a, h, t,
  ZSpace.Singleton(ZPath.Deref(h)), ZPath.Deref(a))
assert(ZEval.bytes()(source).size == 256)
val (main, rs) = CodedTranslation(Codes.BytesToBits, source)
ZEval.bits(rs)(main) // StackOverflowError, including with -Xss64m
```

Expected: 256 eight-bit paths. Observed: the native byte fold returns 256, and its bit translation overflows. Thus this is not only a stress case for an unbounded symbol alphabet or very long text: it affects an ordinary direct byte-language program using all 256 permitted heads.

**Cause and smallest useful repair.** `ZEval.apply` and `path` (`:351–417`) recurse on the JVM stack. Generated fold loops recurse once per source head (`:712–729`); an unbounded string scanner recurses once per byte (`:779–787`). Bounded decoder DAGs do not bound the number of fold groups. Use one trampoline or explicit evaluation stack for the shared evaluator, including path evaluation, argument evaluation and lifted calls; do not merely increase `-Xss` or add per-code workarounds.

Also eliminate sequencing when the accumulator update is genuinely unchanged: evaluate the initial accumulator once and lower to one symbol iteration, preserving shadowing and ignored-binder behavior. For the remaining folds, the current least-path selection is better than rebuilding all heads, but `ZEval.Range` still sorts the entire remaining set on every step. At minimum specialize a normalized one-element minimum slice to a minimum scan; measure remaining filtering/removal work rather than claiming that this makes every fold linear.

**Validation.** Add the two witnesses as permanent tests and run the real compiler/evaluator with a normal bounded stack. Sweep folds at 50/200/800/4,000/10,000 groups with unchanged and changing accumulators, empty input, epsilon, and grouped tails. Include a native byte-language Fold over all 256 byte heads followed by `BytesToBits`, not just the existing all-byte `Head` test. Sweep a single string symbol at 128/512/4,096/65,536 characters. Require result parity and no stack overflow; record elapsed time, peak memory and evaluator work. Retain callback counts and pure inputs to distinguish actual source duplication from interpreter overhead.

## P2 — Compute the interner's maximum code length from the bound, not by enumerating its ID space

**Evidence.** `FibInterner.maxCodeLength` at `ZippyCoded.scala:519` computes

```scala
Some((1 to idBound).iterator.map(fibonacci(_).length).max)
```

The bound is an admissible ID range, not a requirement that this many symbols have been allocated. Constructing a codec for one small program therefore enumerates and allocates Fibonacci vectors for every possible ID below its declared limit. Executed construction times were approximately 29 ms at 10,000, 92 ms at 100,000, and **959 ms at 1,000,000**. The resulting bounds are only 20, 25 and 30 bits. A near-full positive-Int bound was not executed; its enormous construction time is a risk supported by this loop, not an additional reproduced timeout.

**Requested change.** Fibonacci code length is monotone in its positive integer argument. Compute this maximum directly from `fibonacci(idBound).length`, or a scalar Fibonacci-threshold loop. This deletes a linear-in-ID-space enumeration while retaining the same automaton and depth specialization. Do not enumerate the global interner or build a second dictionary merely to obtain a logarithmic bound.

**Validation.** Compare the direct result against the old exhaustive maximum for small bounds and every nearby Fibonacci threshold. Construct codecs with bounds 1, 64, 65, 1,024, 1,000,000 and `Int.MaxValue` without filling the interner. Construction should be logarithmic, and generated decoder size should remain proportional to states × maximum code length and respect `depthBudget`.

## P2 — Turn acceptance into evidence for the full contract and real input scale

**Evidence.** The green suite improved Unicode and lexical-scope coverage, but `rndSpace` still produces fewer than eight paths with at most three symbols (`ZippyCodedCheck.scala:15`), from a fixed alphabet of short strings. The corpus's 3,000 frames therefore never approach the executed fold/long-head failures. Actual stable-ID routes are excluded for ordered programs. The depth-specialization test checks routine count and tiny execution, not a large fold or long head. `scripts/gates.py`'s explicit `GATE_SUITES` does not contain `morkl.ZippyCodedCheck`.

**Requested change and validation.** Keep the current corpus, but add the minimized failures and independent size/shape sweeps above. Make a constructor × requested-codec coverage matrix assert that every mandatory pair ran under its declared contract; a skipped pair must not count as translation coverage. Exercise direct native bit/byte constructors as well as symbol-derived programs. Keep all 256 byte values, exact annotation checks, pure-output checks, stable-ID interoperability and compile-once frames. Register the suite with the repository's declared acceptance entry point, and run a controlled failing assertion to verify that the gate actually reports failure. Add a small durable benchmark entry point and a short user-facing contract/limitations section, rather than relying solely on comments and uncommitted scratch output.

The valuable intended outcome is a complete, predictable executable bit/byte language for existing interned data, with real recursion bounds. A collection of correctly named codecs plus dictionary-only ordered execution falls short of that outcome. No new language operator or hidden runtime symbol-boundary metadata is needed for the repairs. Keep the shared AST, representations, automaton compiler and source oracle; fix the common execution/ordering concepts rather than adding another layer of special-case codecs.
