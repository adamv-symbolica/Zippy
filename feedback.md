# Review: the alphabet implementation does not yet complete the task

Reviewed `/home/ubuntu/workspace/.review` at `8ebfa1522bdce6411f88b68003317e5f3cd5eb2d`, including implementation commit `cba91f8` and its four-file diff from `5afad8a`. I inspected the existing symbol evaluator, interner, substitution, optimizer, proof identity, and acceptance runner. The implementation checkout was not edited.

The implementation's **28 `AlphabetLanguagesCheck` tests pass**, including its 200-program corpus check. Nevertheless, independent executable cases reproduce wrong results, an unsound optimizer rewrite, a non-injective string encoding, mutable set keys, and substantial avoidable fold scaling. Completion should not be claimed until the following changes are made.

## 1. P1 — Translate annotations rather than copying false facts into the target

**Evidence:** `AlphabetTranslate.scala:191–196,313` returns `ZPath.Deref(pr)` and routine formals unchanged. A source `PathRef("p").known(1)` remains `known(1)` after four-byte encoding; the actual value now has length 4. This is an incorrect exact annotation, not just a missed optimization. Generated decoder parameters, meanwhile, are made with unannotated `PathRef`s at lines 166 and 186–187. `code.maxLen` generates named copies, but does not implement the requested propagation of path-length facts into recursion analysis.

**Executed counterexample:** translate `Singleton(Deref(PathRef("p").known(1)))` through `internedToBytes(Set("a"))`, then `bytesToSymbols`. Convert the result with `Symbols.toSpace`, apply native `TailsUnion`, and bind `p` to the four encoded symbols. Compare direct evaluation with evaluation after the existing `Lower.TailsUnion_Singleton` pass:

```
before = Set(List("000", "000", "000"))
after  = Set(List())
```

The original annotation was true. Translation made it false, and an existing compiler rule then changed the answer.

**Change:** transform exact lengths by fixed code width, on both declarations and uses. For variable-width codes, retain a sound interval/maximum rather than an exact source-symbol count. Give decoder prefixes and remaining recursion fuel sound bounds through the existing length-analysis environment or an explicitly supported annotation. Do not install a maximum as an exact length. Keep annotations out of runtime values.

**Validation:** execute the optimizer counterexample above; require identical results before and after every relevant length-sensitive pass. Cover fixed widths 1, 4, and 8, Fibonacci variable widths, routine parameters, captured references, and iteration/fold heads. Check inferred bounds against actual encoded lengths and check that the existing analysis can read the generated depth facts.

## 2. P1 — Replace ad hoc lifting/name generation with one hygienic binding discipline

Three independent scope failures reproduce. Sources are legal under the existing evaluator.

1. **Ignored binders become real parameters.** An outer routine binds path ref `_` to `z`; its body is `Iteration({a}, _, r, Singleton(Deref(_)))`. The existing evaluator ignores the iteration binder `_`, so the result is `{z}`. The translated Fibonacci program returns `{a}`. `FreeVars.space` subtracts `_` as though it bound a variable (`AlphabetLanguages.scala:70–75`), and the lifted body then receives `_` as a genuine routine formal (`AlphabetTranslate.scala:225–229`). A function call's parameter map and an ignored iteration binder have different semantics.
2. **Coincident fold names reverse shadowing.** `Fold({a}, initial=z, acc=h, symbol=h, rest=r, body=Singleton(h), update=h)` returns `{a}` in the symbol evaluator and `{z}` after Fibonacci translation. Source evaluation binds accumulator first and head second. The lifted routines declare `Vector(h.s, acc.s)` and receive `(head, accumulator)` (`AlphabetTranslate.scala:233–234`); duplicate names therefore leave the accumulator as the winner.
3. **Legal source names collide with generated names.** Use an outer free reference named `§fibonacci#<next translator number>P` inside an iteration body. That is also the scanner's prefix name (`AlphabetTranslate.scala:272–277`). Source evaluation succeeds, but the translated result contains an undecodable Fibonacci fragment. The comment that `§` never occurs in source names is neither enforced nor true of the AST. A global counter prevents some translator-to-translator collisions; it does not prevent capture by user names.

**Change:** alpha-rename binders and allocate generated routine/path/space names against the entire source name set. Preserve `_` as an ignored binder without binding a body formal to it. Preserve actual shadowing when source binder names coincide. Adapt/reuse the existing capture-avoidance approach in `Subst.scala` instead of adding more special prefixes or counter patches. Make name allocation deterministic for the same input.

**Validation:** promote all three cases to regressions, repeat for space `_` and fold `_`, and add nested/mutually recursive captures. Rename every source binder through a bank including generated-looking Unicode names and require evaluation invariance. Assert unique generated formals and closure of every generated routine. Translating the same pure program in one process and in fresh processes should yield equivalent deterministic structure.

## 3. P1 — Implement a total string-to-byte encoding and an honest decoder bound

`Codes.stringsToBytes` (`AlphabetTranslate.scala:84–103`) weakens arbitrary string symbols to a finite, specially ordered, NUL-free universe. Some restrictions are documented, but they are not exceptions in the request.

**Executed failures:**

- `val c = Codes.stringsToBytes(Set("\ud800")); c.dec(c.enc("\ud800"))` returns `Vector("?")`. An accepted symbol with UTF-16 code unit 55296 becomes code unit 63. JVM UTF-8 replacement makes this encoding non-injective.
- `stringsToBytes(Set("\u0000"))` throws. A JVM string containing NUL is a valid existing symbol.
- `stringsToBytes(Set("\ud83d\ude00", "\ue000"))` throws because UTF-8 and Java string order differ. The source program is otherwise valid.
- With `c = stringsToBytes(Set("a"))`, `c.enc("long")` **succeeds**, producing five bytes although `c.maxLen == 2`. Translate `Iteration({long}, h, r, Singleton(h))`: source result `{long}`, byte result **empty**. The compiler silently truncates an input the codec accepts. This exercises the API outside the declared bound, but the API neither rejects it nor offers a correct unbounded decoder; the original request did not require a closed finite string universe.

**Change:** choose an injective terminated encoding with escaping for NUL and a defined policy that preserves the complete existing string domain, including surrogates. Preserve source `Range`/`Fold` semantics without rejecting valid string universes merely to make every other operator homomorphic. Use a recursive decoder for unknown string lengths; specialize/unroll only under sound available bounds. If a caller supplies an actual length contract, enforce it at the encoding boundary instead of returning an apparently valid value that evaluation silently drops.

**Validation:** round-trip all 65,536 UTF-16 code units and randomized multi-unit strings, including empty symbols and embedded NUL. Test the supplementary/BMP ordering pair above in `Range` and `Fold`. Compile before providing open input frames; vary symbol lengths across frames and callback outputs without inspecting expected results first. Require either a correct result or an explicit boundary-contract failure, never silent loss.

## 4. P1 — Encode the existing interned IDs; do not replace interning with per-universe ranking

**Evidence:** the real stable interner is `Interner` in `IntTrie.scala:9–27`. Neither interned codec consults it. `symbolsToBits` sorts the strings and separately sorts the Fibonacci codewords (`AlphabetTranslate.scala:70–82`); `internedToBytes` encodes a sorted-string rank (`105–114`). These are finite dictionary encodings, not the requested encodings of already interned symbols.

**Executed example:** intern `z` then `a` in a fresh process. Actual IDs are `z=0, a=1`; the four-byte codec encodes `z=00000001, a=00000000`. Also, the same `a` encodes as Fibonacci `11` in universe `{a}` but `011` in `{a,z}`, with its existing interner ID unchanged.

**Change:** accept an interner/snapshot or explicit stable IDs. Encode ID+1 with Fibonacci coding and the actual ID with four fixed bytes. Separate symbol-boundary decoding from source ordering: rank conversion or another in-language ordering construction belongs at order-sensitive operations, not at every I/O value. Preserve existing ID stability without repeatedly materializing/sorting a universe of string contents.

**Validation:** deliberately intern in reverse lexical order, add unused symbols, compile multiple programs over the same table, and check that shared symbols keep identical encodings. Assert byte-for-byte equality with the four bytes of the real ID and Fibonacci(ID+1). Differentially check `Range` and `Fold` when ID order disagrees with source order. Measure conversion from the existing interned representation on long string symbols; demonstrate that the fast path works on IDs rather than rehashing string contents or rebuilding a sorted codebook per program.

## 5. P1 — Close the public mutable-array constructor of `ByteStr`

**Evidence:** `AlphabetLanguages.scala:118` makes the primary constructor public and retains its array. Only companion `apply` clones. Native byte spaces use this type as a hash-set key.

**Minimized executed counterexample:**

```scala
val a = Array[Byte](1)
val b = new ByteStr(a)
val s = Set(b, ByteStr(Array[Byte](2)), ByteStr(Array[Byte](3)),
  ByteStr(Array[Byte](4)), ByteStr(Array[Byte](5)))
a(0) = 99
assert(s.contains(b)) // fails: observed false
```

The object changed from `01` to `63`; its hash changed after insertion. Use at least five set elements: Scala's small-set specializations can hide the hash corruption.

**Change:** make the primary constructor private. Keep the copying public factory, and restrict ownership-taking construction to code that has freshly allocated the array. This is a small local fix, not a reason to add a second byte representation.

**Validation:** mutate both the original input array and an array returned by `bytes`; neither may change the value, equality, hash, or membership in a hash-backed set. Verify native operations preserve that invariant.

## 6. P2 — Replace the duplicate fold traversals with a shared source-group walker

**Evidence:** `FoldGen` (`AlphabetTranslate.scala:281–311`) has separate `out` and `acc` routine families. `out` traverses a subtree to emit outputs and then traverses it again through `accAfter` to recover its accumulator. The overlap repeats down every codeword level. It also injects opaque `GroundedSP` singleton extraction into an otherwise pure program (`the`, line 182).

I measured the actual generated program with **one source head, one output path, and an unchanged accumulator**. `L` is the ASCII symbol's length; its codeword has L+1 bytes. Source update count is always 1. `CountingBytes` delegates to the unmodified `ByteRepr` and counts head visits plus payload bytes allocated by representation construction/slicing/concatenation:

| L | target updates | generated routines | target head visits | allocated byte payload |
|---:|---:|---:|---:|---:|
| 8 | 9 | 38 | 252 | 1,766 |
| 16 | 17 | 70 | 884 | 8,502 |
| 32 | 33 | 134 | 3,300 | 49,238 |
| 64 | 65 | 262 | 12,740 | 325,782 |
| 128 | 129 | 518 | 50,052 | 2,347,286 |

Thus traversal approaches quadratic and payload allocation cubic in codeword length even with one head and a singleton result. Exploratory uninstrumented evaluation times in the same run were approximately 15/12/26/80/80 ms; these are not calibrated benchmark claims. The deterministic counts establish the duplication independently of JIT noise. Side-effect counts are instrumentation, not an assumption that callbacks must have side-effect-preserving semantics.

**Change:** use one reusable decoder/source-group enumerator that passes the decoded head path and tail space to a continuation. Thread the accumulator directly as an ordinary path argument, evaluating the update once per source group. Remove `FoldGen`'s second accumulator walk and its `the` extractor rather than caching or patching the two walks. The same group walker can simplify the bespoke `TailsIntersection` machinery and avoid its incomplete-codeword `junk` bookkeeping path (`249–267`).

`GroundedSP` is an existing AST case, so introducing it is not literally a new constructor. Nevertheless, pure folds now gain host callbacks and lose existing proof/compiler portability: `AgnosticPipeline.CanonicalId.of` explicitly rejects grounded nodes. Preserve ordinary function abstraction for pure translations instead of hiding this control operation in Scala. The incomplete `junk` marker also needs an explicit account under the original no-metadata constraint; correct final outputs alone do not establish that constraint.

**Validation:** rerun the measured family and report compile time, routine count, evaluation time, traversal, and allocation. Require updates to remain one per source head and prevent quadratic traversal caused by accumulator recomputation. Test high branching (all 256 bytes), long shared prefixes, and empty tail intersections. For pure inputs, require pure generated routines and successful `CanonicalId` construction after injecting back into symbols. Do not add a new extraction opcode or runtime metadata table to fix this.

## 7. P2 — Fix overflowing Fibonacci construction at the supported ID boundary

**Evidence:** `Codes.fibonacci` (`AlphabetTranslate.scala:63–68`) computes consecutive Fibonacci weights as `Int`s. For `Int.MaxValue`, `takeWhile(_ <= n)` can never reject any `Int`, including overflowed negative values. I executed this in a child JVM: it was still running after 3 seconds and had to be forcibly terminated. The parent explicitly waited for termination. This is an unbounded/resource-exhausting construction, not merely a slow large-universe test.

**Change:** compute weights in `Long` or `BigInt`, stop before overflow, and retain a finite result for every nonnegative interned `Int` ID (coding ID+1 may itself require widening). Do not materialize a universe with billions of strings to test the numeric primitive.

**Validation:** directly encode/decode ID boundaries around Fibonacci overflow and `Int.MaxValue`, with a subprocess timeout and bounded heap. Require a finite prefix-free code of the expected logarithmic size and successful round-trip. Once item 4 uses real IDs, test this through that path too.

## 8. P2 — Replace output-informed test setup with real compiler acceptance tests

**Evidence:** `AlphabetLanguagesCheck.scala:78–81` evaluates the source first, then adds `expect.flatten` to the codec universe. This supplies exactly the runtime output symbols/lengths a real compiler cannot know, masking the string-bound problem and finite-codebook assumptions. The 28 passing tests missed every scope/annotation/mutation case above. `scripts/gates.py:GATE_SUITES` does not include the new alphabet suite, so the repository's declared acceptance path does not cover it.

**Change:** add the counterexamples above as ordinary regressions, compile before computing expected results, and use only source constants plus declared input/interner contracts. Run the repository's actual recursive query/Datalog/puzzle programs through each encoding with externally supplied frames; compare both plain execution and the existing optimization/proof path. Register the alphabet acceptance suite with the existing gate mechanism rather than inventing a second runner. Link the public API and exact codec contracts from the repository guide.

**Validation:** a clean-checkout test command must run the registered suite and all reproduced regressions without pre-evaluating source programs for compilation facts. Mutating a decoder bound, copied annotation, wildcard scope, or byte-array owner must make the suite fail. Use reproducible randomized renaming/Unicode/open-frame tests in addition to the existing saved corpus.

## What is actually covered, and what should not become unrelated scope

The generic AST mirrors the cases of **this checkout's** `Space`/`Path`; the Mirror test is useful. Bit/byte native representations and the four simple alphabet injections are present. Boundary walking and finite-depth specialization are real implementations, not stubs. However, the two supposed interned encodings are rank-based replacements, arbitrary string support is weakened, and annotation integration is incorrect. The most valuable unrealized outcome is a reusable, hygienic, annotation-aware translation of actual existing programs and interned values, including the optimizer path, without an output oracle or opaque host control step.

A byte zipper is optional in the original wording; its absence is not a blocking finding. Likewise, do not add cases absent from this checkout's symbol language just because another branch may contain them. Repair the shared encoding/binding/group-walking concepts before adding more language machinery.

## Reproduction material and commands

The complete runnable cases, each labeled independently, are preserved outside the product tree:

- `/home/ubuntu/polemic-notes/alphabet-review/AlphaReview.scala`
- `/home/ubuntu/polemic-notes/alphabet-review/results.txt`

Run against the unchanged reviewed implementation:

```bash
cd /home/ubuntu/workspace/.review
env _JAVA_OPTIONS='-Xmx4g -Xss32m' scala-cli run \
  src/main/scala /home/ubuntu/polemic-notes/alphabet-review/AlphaReview.scala \
  --main-class morkl.alpha.AlphaReview --server=false \
  --scala 3.8.1 --source 3.3 \
  --dependency org.scala-lang.modules::scala-collection-contrib:0.3.0
```

The Fibonacci experiment spawns a bounded-duration child and kills/waits for it if needed; it does not leave a worker running. The other cases exercise real generated `Evaluator` programs, the existing symbol evaluator, and the actual optimizer rule. The stock test comparison was run with:

```bash
env _JAVA_OPTIONS='-Xmx8g -Xss32m' scala-cli test src/main/scala src/test/scala \
  --test-only morkl.AlphabetLanguagesCheck --server=false \
  --scala 3.8.1 --source 3.3 \
  --dependency org.scalameta::munit:1.2.1 \
  --dependency org.scala-lang.modules::scala-collection-contrib:0.3.0
```
