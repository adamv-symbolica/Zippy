# Review of `.review` at `4f8fc08`

The completion claim does not hold yet. I inspected the history (`2448e9d`, `f2c42c7`, and especially `eb39441`), the diff from `5afad8a`, the original symbol evaluator/interner, the generated compiler, and the acceptance suite. I left implementation files unchanged. **All 17 submitted `ZippyCodedCheck` tests pass**, including 3,000 corpus frames and the small-stack witnesses. Independent production-path probes nevertheless reproduce a wrong result, quadratic Fold execution, and a 12 GiB heap exhaustion on one 128 KiB string symbol.

## Contract audit: what is implemented and what is qualified

- The five path constructors and 21 space constructors in this checkout have corresponding generic cases and symbol conversions. Bits and bytes have the requested BigInt-set and immutable byte-array-set reference representations. Head is expressed by the existing Iteration, and its head type limits the alphabet to two or 256 atoms.
- All seven requested encoding/injection directions exist. Literal interner IDs remain stable; sorted dictionaries are separately named. CESU-8 with in-band control escaping handles Java strings, including lone surrogates. Generated pure programs use the existing operations and routines, without added grounded callbacks or symbol-boundary metadata.
- Fibonacci bounds are now computed directly and produce actual state/depth routine DAGs. The trampoline fixes the previously reported stack failures. The suite is now registered in `scripts/gates.py`.
- Stable-ID `Range` and accumulator-sensitive `Fold` require `.ordered(vocab)`; the unrestricted codec still rejects them. This is a real finite-vocabulary restriction, not unrestricted ordered execution on arbitrary future interned names. The static projection is a reasonable way to supply the missing order without changing ID bytes, but its input contract needs enforcement (P2 below).
- The byte zipper is absent. The original request says it *can* have this backend, so I do not classify its absence as a mandatory omission. Do not spend the next round on an optional zipper while the required reference path has the failures below.

Preserve the working generic AST, stable IDs, total string encoding, hygienic lifting, shared decoder, evaluate-once sources, direct Fibonacci bound, small-stack tests and acceptance registration.

## Reproducing this review

Scratch sources and complete logs are preserved in:

`/home/ubuntu/polemic-notes/zippy-coded-review-r4/`

From `.review`, the common command is:

```bash
PROBES=/home/ubuntu/polemic-notes/zippy-coded-review-r4
env _JAVA_OPTIONS='-Xmx12g -Xss8m' scala-cli run src/main/scala \
  "$PROBES/Review.scala" --server=false --scala 3.8.1 --source 3.3 \
  --dependency org.scala-lang.modules::scala-collection-contrib:0.3.0 \
  --main-class morkl.ReviewR4
```

No argument runs the callback counterexample; append `-- fold` or `-- long` for the scaling probes. Substitute `Extra.scala` and `morkl.ReviewExtraR4` for the deterministic copy counter (no argument), `-- single-long`, `-- contract`, or `-- retention`. The copy counter delegates to the actual `ByteRepr` methods; it changes no representation or compiler behavior. All reported Fold results were checked against the **original `eval(ZSym.to(program))`**, not merely the new vector evaluator.

## P1 — Preserve Fold initialization, updates, and ordering when optimizing ignored/constant accumulators

**Reproduced wrong result.** `ZippyCoded.scala:778–781` treats an accumulator named `_` as permission to erase `initial` and `update` completely. Existing Grounded operators accept arbitrary Scala callbacks; ignoring a binding does not make those expressions unevaluated. The original evaluator initializes once and evaluates the update after every body, including when the accumulator binder is ignored.

The executed witness is:

```scala
var phase = 0
val h = PathRef("h"); val t = SpaceMention("t")
val src: SymSpace = ZSpace.Fold(
  ZSpace.Literal(Set(Vector("a"), Vector("b"))),
  ZPath.GroundedPP(ZPath.Constant(Vector.empty), p => { phase = 1; p }),
  PathRef("_"), h, t,
  ZSpace.GroundedPS(ZPath.Deref(h), _ => Set(Vector(s"phase$phase"))),
  ZPath.GroundedPP(ZPath.Deref(h), p => { phase += 1; p }))
```

Resetting `phase = 0` before each execution gives:

| Executor | Decoded result | Final phase |
| --- | --- | ---: |
| Original `eval` | `{phase1, phase2}` | 3 |
| `ZEval.syms` | `{phase1, phase2}` | 3 |
| Translation with `StringBytes` | `{phase0}` | 0 |
| Translation with `Dictionary4` | `{phase0}` | 0 |
| Translation with `InternerId4` | `{phase0}` | 0 |

The dictionary contains every symbol the witness can produce, so this is not an admissibility failure. The violated invariant is constructor/behavior preservation for supported grounded programs. This is a callback-sensitive defect; it is not evidence that the shortcut gives wrong denotations for pure, total functions.

**Smallest safe repair.** Apply the order-free shortcut only when its purity/totality preconditions are established. Otherwise preserve Fold evaluation using ordinary routine arguments and the ordered lowering. The neighboring unchanged-accumulator shortcut also replaces sorted Fold body order with Iteration order; callback-sensitive bodies must not be treated as order-independent without a contract. Preserve initialization even for empty/headless input. Do not add an encoding-specific language operation.

**Validation.** Permanently add the witness through all applicable codecs, plus callback traces for empty input, initial/body/update sequencing, ignored head/rest binders, and unchanged accumulators with order-sensitive bodies. Also test a grounded initializer/update that throws: erasing an ignored binding must not erase its evaluation. If the project intends to exclude impure/partial Grounded callbacks, make that a separately enforced API contract rather than silently applying that assumption to the existing constructor.

## P1 — Replace the two quadratic general-Fold lowerings with one indexed head-sequencing algorithm

**Reproduced performance defect.** The measured program is deliberately small-output:

```scala
Fold(Mention(x), Constant(Vector.empty), a, h, t,
  Singleton(Concat(Deref(a), Deref(h))), Deref(h))
```

Its input has `n` distinct one-symbol paths. The accumulator is just the previous head; each output contains at most two symbols, so total output size is O(n). Encoding and compilation are outside timing. Numbers are median milliseconds of three samples after two warmups, on the production `CodedTranslation` / `ZEval.bytes` path, with exact result parity:

| n | Original symbol `eval` | Dictionary4 | StringBytes | Stable InternerId4 + vocabulary |
| ---: | ---: | ---: | ---: | ---: |
| 500 | 3.332 | 68.348 | 127.328 | 79.956 |
| 1,000 | 2.527 | 327.793 | 467.474 | 338.768 |
| 2,000 | 4.362 | 1,335.218 | 2,009.311 | 1,377.485 |
| 4,000 | 9.610 | 5,335.654 | 8,461.369 | 5,573.896 |

At 4,000 groups this is **555× / 880× / 580×** source time. Doubling from 1,000 through 4,000 gives almost fourfold target time. The docs honestly acknowledge quadratic stable-ID Fold, but the problem also affects the order-preserving dictionary and string routes. Being documented does not make it the intended high-performance executable encoding.

**Cause.** `fold` (`:814–832`) repeatedly selects a minimum from the remaining input, unwraps it, and filters away a whole head group. `projectedFold` (`:839–860`) improves matters by extracting/recoding heads once, but still scans the remaining heads for each minimum and scans the original input for each `Unwrap`. `ZEval` indexes only literal sets (`:362–379`), not the input frame supplied through `Mention`. Replacing a sort with `v.min` still scans O(n) on every step.

**Repair and simplification.** Use one lowering for both code kinds: evaluate the input once; extract complete heads once; project only those heads when ordering repair is needed; sequence over an ordered persistent head set; unwrap each group from the fixed original input through a reusable prefix index. Ordered reference operations must maintain/reuse that index rather than rebuilding it for each remainder. Keep all symbol boundaries in ordinary generated code. Delete the older source-language `fold` loop and reuse the shared implementation instead of maintaining two almost-identical sequencing algorithms.

**Validation.** Add this exact bounded-output program, with frames passed through `Mention`, to acceptance. Sweep 1,000/4,000/16,000 groups over dictionary, string, stable-ID byte and Fibonacci routes. Require parity and a measured growth exponent near n log n (for example ≤1.3), and set a concrete slowdown budget against original `eval` (20× is a useful target). Count prefix comparisons/elements visited and allocation as well as AST dispatch. The target's dispatch count is already linear in this failing probe: dictionary steps rise from 27,010 to 108,010 while time grows sixteenfold. `steps` alone cannot detect this defect.

## P1 — Stack safety must not retain quadratic byte-array state: one 128 KiB head exhausts 12 GiB

**Reproduced in a fresh process.** `Extra.scala -- single-long` evaluates:

```scala
val src: SymSpace = ZSpace.Iteration(ZSpace.Mention(x), h, t,
  ZSpace.Singleton(ZPath.Deref(h)))
val input = Set(Vector("a" * 131072))
```

Original `eval` returned the same singleton in approximately **50 ms**. Its encoded byte path has length **131,073**. A single target execution then raised `OutOfMemoryError: Java heap space` with `-Xmx12g`; the trace ends in `ByteRepr.concat` (`ZippyCoded.scala:311`). This fresh-process witness rules out accumulation across the length sweep. The larger sweep separately measured about **4.17 seconds** at 65,536 characters before failing at 131,072.

A delegating `PathRepr` counter makes the copying problem deterministic:

| Source characters | Bytes copied by concat/uncons/drop | AST steps |
| ---: | ---: | ---: |
| 1,024 | 1,051,650 | 11,278 |
| 2,048 | 4,200,450 | 22,542 |
| 4,096 | 16,789,506 | 45,070 |
| 16,384 | 268,484,610 | 180,238 |

`ByteRepr.uncons` copies each remaining suffix; the scanner builds a new accumulated prefix on every byte. The trampoline also retains enough intermediate state to exhaust the heap. The exact retention chain still needs profiling; the quadratic copying and the heap failure are executed facts. A successful 512 KiB-stack test is not evidence of bounded heap use.

**Repair.** Profile and remove unnecessary pending contexts/continuations on singleton Iteration, empty Union and tail Call paths; do not simply raise the heap or cap valid string length. Reduce repeated byte copying using a generic immutable representation/index strategy, keeping public defensive-copy semantics and content equality. Sharing backing storage or optimizing ordinary evaluator operations must not introduce a hidden symbol-boundary table or new language feature. Preserve the shared trampoline and the existing stack witnesses.

**Validation.** Add the fresh one-head witness with a fixed heap, source parity, and allocation/peak-live-memory measurements. Sweep 1,024/4,096/16,384/65,536/131,072 characters. Require bounded practical memory for the last input (e.g. a 1 GiB acceptance subprocess), and prevent the current fourfold copied-byte growth per doubling. Track heap independently of stack and AST steps.

## P2 — Enforce the frozen vocabulary before silently losing data

**Executed boundary failure, explicitly outside the declared snapshot contract.** With `InternerId4.ordered(Vector("a"))`, compilation of `Range(Mention(x),0,1)` succeeds. The same codec's public `encPath(Vector("b"))` also succeeds, yielding valid stable-ID bytes. Passing that result as the input produces the **empty set**, while the source produces `{"b"}`. `docs/ZIPPY_CODED.md` acknowledges silent dropping. This is not a reproduced wrong result for an admissible snapshot input; it is an unsafe, unenforced public boundary on a narrowed version of the requested symbol translation.

A separate bound check also mutates global state on failure: after `val c = FibInterner(Interner.size)`, encoding one new symbol throws but increased `Interner.size` from 2 to 3 in the probe.

**Repair.** Have snapshot-aware public encoders reject symbols outside the snapshot, including callback-produced values crossing host adapters, while retaining the separate open stable-ID codec. Check membership before interning. Provide a non-mutating interner lookup for failed bounded encodes. Document that manually supplied target sets are subject to the same admissibility contract; do not claim unrestricted ordered support for future symbols.

**Validation.** Unknown input/callback symbols must fail clearly at the encoding boundary instead of being accepted then deleted by Range/Fold. Failed encodes must leave interner size and every existing code unchanged. Keep compile-once multi-frame tests within the frozen vocabulary and stable-ID equality before/after unrelated successful interning.

## P2 — Make acceptance measure the complete native contract and the failures above

The new suite registration and 12-chain coverage are useful. They do not establish scale or exhaustive native-language behavior:

- The general-Fold sweep explicitly skips changing accumulators above 4,000 (`ZippyCodedCheck.scala:421`) and asserts only equality, with no time/work/heap bound. Its changing program grows the accumulated prefix and emits all prefixes, so it inherently has quadratic output size; it does not isolate avoidable sequencing work.
- The corpus frames contain fewer than eight paths of at most three short symbols. Constructor coverage is credited from syntactic occurrence (`sub` / `coverage`), not measured execution of each constructor. Most columns originate with symbol encodings rather than arbitrary native bit/byte words.
- `sub` omits spaces nested inside `ZPath.GroundedSP`. `ZOps.size` (`ZippyCoded.scala:228`) omits all path syntax and those nested spaces too. Neither can be the complete AST walker for the required gates.
- `ZippyCodedBench` uses `ZEval.syms` as its baseline, reports single runs, and publishes heap *after* evaluation rather than peak live memory/allocation. Those numbers cannot substitute for the original `eval` comparison or diagnose the reproduced OOM.

**Change and validation.** Add the three P1 witnesses as durable acceptance checks, plus direct native Boolean/Byte constructor tests over epsilon, arbitrary lengths and all 256 byte values before injections. Cover both five path and 21 space constructors. Reuse one exhaustive, binder-aware space/path walk for names, constants, coverage and true AST size instead of adding another partial traversal. Gate size and actual bulk-operation work in addition to equality. Extend the benchmark to original `eval`, compilation/encoding outside timing, warm medians, bounded-output Fold, and peak memory. Run the registered suite and deliberately fail one of these new assertions to verify gate propagation. Keep mutation artifacts/scratch output outside the product tree.

## P3 — Bound evaluator caches and keep the next change smaller

`ZEval`'s two strong identity maps (`:362–363`) retain every literal and its sorted array for the lifetime of a reusable evaluator. The executed `retention` probe evaluates 1,000 discarded 64-path literal programs through one `ZEval.bytes()` and then observes **1,000 entries in each map**. This proves unbounded cross-program cache retention; it is not a reproduced OOM at that size.

Scope caches to a compiled program or otherwise bound/weakly retain discarded entry programs, preserving warm reuse of live routine literals. Validate that repeatedly discarding independent programs does not cause retained heap/index entries to grow without bound, while a repeatedly evaluated fixed program still reuses its tables. Prefer a clear cache lifetime and the single Fold/AST-walker refactor above over more codec-specific caches and special-case sequencing patches.

The most valuable unrealized result is predictable execution of the requested languages on real interned and string data. Stable-ID ordering now exists under a declared contract; the next round should make that contract safe and the common executor/lowering resource-bounded, rather than expand an optional backend or add more green tiny-input coverage around the same quadratic algorithms.
