The completion claim does not hold. Review of `.review` at `e80440a` (implementation commit `2448e9d`, against `5afad8a`) reproduced wrong results, valid-program crashes, false length annotations, mutable set keys, and exponential evaluation. All five submitted `ZippyCodedCheck` tests nevertheless passed, including its 3,000 corpus frames. No implementation files were changed.

The implementation does cover the reviewed symbol language's constructors, supplies the two requested reference representations, names all seven translation routes, and uses existing operations/routines for pure translations. The optional byte zipper is not a missing mandatory deliverable. The substantive gaps are general string semantics, faithful binding/annotation semantics, actual interner integration, and scaling beyond the tests' tiny alphabet and shallow paths.

Runnable diagnostic sources and recorded results are retained at `/home/ubuntu/polemic-notes/zippy-coded-review-2026-10-29/`. Run each from the reviewed checkout with:

```bash
cd /home/ubuntu/workspace/.review
PROBES=/home/ubuntu/polemic-notes/zippy-coded-review-2026-10-29
# Repeat with PerfProbe.scala / morkl.perfDiagnostic,
# ScopeProbe.scala / morkl.scopeDiagnostic, or NativeProbe.scala / morkl.nativeDiagnostic.
env _JAVA_OPTIONS='-Xmx8g -Xss16m' scala-cli run src/main/scala \
  "$PROBES/ReviewProbe.scala" --server=false --scala 3.8.1 --source 3.3 \
  --dependency org.scala-lang.modules::scala-collection-contrib:0.3.0 \
  --main-class morkl.reviewProbe
```

`results.txt` in that directory records the observations below. Prioritized changes:

1. **P1 — Make the string translation preserve the source string domain and ordering.**

   `ZippyCoded.scala:429–438` restricts its order-preservation comment to nonsurrogate BMP text, rejects embedded NUL, and silently replaces malformed UTF-16 in `getBytes(UTF_8)`. The translator nevertheless maps every `Range` structurally at line 524 and uses target order when lowering `Fold`. The original request did not restrict string symbols to that subset.

   `ReviewProbe.scala` reproduces two separate semantic violations:

   ```scala
   val s: SymSpace = ZSpace.Range(
     ZSpace.Literal(Set(Vector("\uD800\uDC00"), Vector("\uE000"))), 0, 1)
   ```

   Expected: singleton U+10000, because the source compares Java strings using UTF-16 `compareTo` (`MORKL.scala:224`). Observed after UTF-8 translation and decoding: singleton U+E000. Both are ordinary valid Unicode symbols. The invariant `eval(translate(s)) == encode(eval(s))` fails. `Fold` shares the same ordering assumption; it needs a regression too.

   ```scala
   val s: SymSpace = ZSpace.Intersection(
     ZSpace.Singleton(ZPath.Constant(Vector("?"))),
     ZSpace.Singleton(ZPath.Constant(Vector("\uD800"))))
   ```

   Expected: empty set. Observed: `Set(Vector(63, 0))`. An unpaired surrogate and the distinct symbol `"?"` encode identically, so even unordered set intersection is wrong. `NativeProbe.scala` additionally confirms `Utf8Z.enc("a\u0000b")` throws rather than translating a legal source symbol.

   Restore a reversible string encoding, with in-band escaping for embedded terminators, and preserve source ordering through ordinary target operations/routines where raw UTF-8 order differs. Do not silently weaken `Code`'s contract or add decoding callbacks to otherwise pure programs. An immediate malformed-input check can prevent silent collisions, but a partial-string precondition alone does not complete the original task.

   Validate round trips, intersection/difference of distinct strings, ordered ranges, and accumulator-sensitive folds over empty strings, NUL, escape characters, BMP boundary characters, supplementary characters, and the source's permitted UTF-16 values. Include paths containing several such symbols and shared symbol prefixes.

2. **P1 — Fix lexical scope centrally before lambda lifting; generated names and parameter order are not semantics.**

   `nm` at `ZippyCoded.scala:482` only increments a counter. It never reserves source routine names, references, or nested binders. `out(r.name) = t` can therefore overwrite a source routine. In `ReviewProbe.scala`, define the valid zero-argument source routine `__fib-interned_iter_body_1` returning `{"b"}`, and union a call to it with an iteration returning its `"a"` head. Expected: `{"a", "b"}`. Observed: `RuntimeException: PathRef(h) path ref not resolved`, after the generated body replaces that routine.

   `ScopeProbe.scala` reproduces a second binding failure without adversarial generated names:

   ```scala
   val q = PathRef("q")
   val s: SymSpace = ZSpace.Fold(
     ZSpace.Literal(Set(Vector("a"), Vector("b"))),
     ZPath.Constant(Vector.empty), q, q, SpaceMention("r"),
     ZSpace.Singleton(ZPath.Deref(q)), ZPath.Deref(q))
   ```

   The original evaluator returns `{"a", "b"}`: the head binding shadows the accumulator. `Interned4` translation returns `{epsilon}`. The generated step declares `(h, acc)` at line 564, reversing the native binding precedence when names coincide.

   Ignored binders also lose outer scope. `ReviewProbe.scala` supplies an outer `PathRef("_") -> Vector("b")` and uses `_` as an ignored iteration binder. Source evaluation returns `{"b"}`; translation throws unresolved `_`. `ZOps.free` drops every `_` dereference at line 182, although ignoring a new binding does not erase an existing outer value. There is also a native evaluator mismatch: `NativeProbe.scala` calls a source routine whose formal path parameter is `_`. Original `eval` returns its argument, while `ZEval.syms` throws because `Call` filters formals through `bindP`. Original calls build parameter maps directly (`MORKL.scala:307–311`); throwaway behavior applies to local binder operations.

   Use one capture-avoiding naming/renaming discipline that reserves names from the whole program, including supplied routines and nested binders. Preserve native shadowing and ignored-binder semantics when assigning fresh formals. Align `ZEval.Call` with the existing evaluator. Avoid a collection of prefix-specific patches or forbidding otherwise legal source names.

   Validate all four reproduced cases, alpha-renamed variants, nested shadowing, collisions in path and space namespaces, and mutual recursion. The results must be independent of user spelling and routine traversal order.

3. **P1 — Evaluate a decoder's source once; default Fibonacci unrolling introduces exponential work.**

   At `ZippyCoded.scala:586–596`, each code-trie arm embeds the entire `src` expression under its own filter. With two leading-bit branches, each translated tail operator reevaluates the preceding expression twice. The submitted interpreter does no memoization, so repeated tails take exponential time even on one path.

   `PerfProbe.scala` runs the real compiler and `ZEval.bits` on `n` nested `TailsUnion` operations over one literal path of `n` copies of `"a"`, using the two-symbol universe `("a", "b")`. Results agree, but warmed single-run measurements on this host were:

   | n | Source evaluation, ms | Default unrolled target, ms | Recursive target, ms |
   | --- | ---: | ---: | ---: |
   | 8 | 0.71 | 4.19 | 2.50 |
   | 12 | 0.08 | 58.77 | 0.84 |
   | 16 | 0.12 | 351.95 | 2.23 |
   | 20 | 0.15 | 2960.98 | 0.50 |

   These are diagnostic timings, not statistical benchmark estimates. The complexity evidence is deterministic: the otherwise identical instrumented source in `ReviewProbe.scala` was evaluated 16, 256, 4,096, and 65,536 times at depths 4, 8, 12, and 16. This is `2^n` input evaluation, not merely a large constant for a reference backend.

   The small repair fits the original language: lift the decoder entry into a routine whose space parameter receives `src` once, then let every dispatch arm refer to that parameter. Reuse the evaluate-once routine pattern already present in `tailsIntersection` (lines 537–547). Do not add a language-level let, runtime metadata, or a global evaluator cache to hide the duplication.

   Re-run the pure literal benchmark and work counter after repair. Base evaluation must remain constant in nesting depth, and work should grow with consumed encoded digits rather than `2^n`. Cover both code-set unrolling and recursive dispatch, plus nested iteration sources. Measure compile size, runtime, and allocation/peak memory on wider universes and skewed/shared-prefix inputs before calling the encoding high performance.

4. **P1 — Make path annotations sound, and actually use the Fibonacci maximum as a bound.**

   `iteration` calls `routine(ZRoutine(..., ref(h) +: fp, ...))` at line 577, but `routine` maps `ref` over those parameters again at line 502. `ScopeProbe.scala` demonstrates that a source head annotated `.known(1)` becomes a formal with exact length **16** under the four-byte encoding, although its actual argument has length **4**. Byte-to-bit iteration similarly declares **64** while passing **8**. The reference evaluator ignores the annotations, so ordinary result comparisons miss this violation of the annotations' exact-length contract.

   Separately, `ref` explicitly discards all variable-code information at lines 494–499. For a 65-symbol Fibonacci universe, the probe reports `maxCodeLength=10`, `codeSet=None`, and `-1` on every generated head/accumulator formal. `maxCodeLength` is never consumed by production translation code; unrolling is selected by universe cardinality at line 427, not by a recursion bound. Thus the requested annotation-driven bounded recursion is replaced by small-universe enumeration, and larger known universes use an unbounded automaton despite their finite bound.

   Keep source and target reference conversion separate and perform it exactly once; deleting the premature `ref(h)` is the local fixed-width repair. For variable codes, carry a maximum/range rather than falsely asserting an exact length. Reuse the existing `Lower.LenBounds` / `SpatialAnnotations.pathLens` concepts instead of creating a competing analysis. Use the resulting bound to specialize scanner states by remaining depth, with an explicit code-size budget, including when the universe exceeds 64 symbols.

   Validate exact 4/8 head annotations against actual call arguments, bounded Fibonacci lengths at universe sizes 1, 64, 65, and 1,024, and bounded specialization of a recursion-free source program. A variable-length maximum must never masquerade as `.known(max)`. Assert the generated recursion bound and code size, not just value parity or the existence of a `maxCodeLength` field.

5. **P1 — Make byte-array set keys genuinely immutable.**

   `ByteArr` at `ZippyCoded.scala:236–242` is documented as immutable but retains the caller's array and exposes it through public `val a`. Its content-dependent hash can change after insertion into the reference backend's set.

   `ReviewProbe.scala` creates a 32-element set of two-byte paths, retrieves the stored `[1,2]` path, and writes `stored.a(0) = 100`. Observed: iteration finds a path equal to `[100,2]`, but `set.contains(ByteArr(Array[Byte](100,2)))` is false. Mutating an array passed to the constructor also changes the constructed path. This breaks the set-of-paths backend's membership invariant and exposes returned evaluation results to corruption.

   Hide mutable storage and copy on construction/extraction, or use a content-equal immutable byte-array wrapper. Keep a small private trusted construction path for arrays freshly allocated by `ByteRepr` if measurements justify it; do not expose that path publicly.

   Validate constructor aliasing, accessor aliasing, stable hash/equality after attempted external mutation, and union/intersection/membership on sets large enough to use hashed storage. Small singleton-set tests alone will miss the failure.

6. **P2 — Implement the requested intern-ID encodings, rather than substituting sorted per-program dictionaries.**

   `FibInterned` sorts symbols and separately sorts their Fibonacci codewords before zipping them (`ZippyCoded.scala:417–420`). `Interned4` assigns a new sorted rank (`443–447`). Neither consumes the repository's actual stable `Interner` IDs from `IntTrie.scala:9–23`.

   This is a material reinterpretation of “the bytes from the interned symbol,” introduced to make `Range` and `Fold` structural. `ReviewProbe.scala` first interns `review-z` and `review-a`, obtaining IDs 0 and 1; `Interned4` encodes them as 1 and 0. `ScopeProbe.scala` also shows `Interned4(Vector("a","b")).enc("b")` is `00 00 00 01`, while adding unrelated symbol `"aa"` changes it to `00 00 00 02`. Existing interned tries therefore cannot use those four bytes directly, and independently compiled alphabets do not share symbol codes. The tests conceal this restriction by rebuilding a complete universe from each program *and its current input frame* at `ZippyCodedCheck.scala:23`.

   Add codecs over actual interned IDs, or an explicit supplied stable interner, with direct fixed-four-byte and Fibonacci encodings. Separate preservation of symbol order from encoding IDs: lower ordered operations appropriately rather than renumbering the interner for convenience. Define the compilation-universe/bound contract explicitly and support compiling a routine once for multiple input frames from that contract. Keep a closed-dictionary codec only as an optional, clearly named specialization if it remains useful.

   Validate four-byte decode-to-ID equality with `Interner.intern`, stable codes when unrelated symbols are interned, interoperability with existing `ITrie` paths, and repeated execution of one compiled program on distinct admissible inputs. Benchmark this actual interned path against dictionary/string lookup on substantial alphabets. That fulfills the valuable intended outcome: predictable bounded execution over existing interned data, not just round-tripping a separately rebuilt symbol dictionary.

7. **P2 — Turn the current differential suite into acceptance evidence for the actual contract.**

   Keep the corpus and chain checks, but the passing five tests are insufficient: runtime inputs are only six ASCII symbols with paths of length at most three (`ZippyCodedCheck.scala:10–12`), and each frame is allowed to redefine its compilation alphabet. The larger-universe test only checks the codec, not generated execution or annotation propagation. The byte-head test checks one actual head, rather than all 256 values. There are no performance or naming-adversary checks.

   Add the minimized reproductions above as permanent tests, direct native bit/byte programs covering every constructor, all 256 unsigned byte heads and values, epsilon/leading-zero cases, source routine parameters including `_`, alpha-renaming invariance, annotation conformance, compile-once/open-input tests, and the nested-tail work bound. Assert that pure source programs introduce no grounded callbacks and that injections emit only their two or 256 allowed symbols. Run those through the public compile/evaluate route, not a host decode-and-reencode substitute.

   Add a short runnable API example and state the supported domain and bound assumptions alongside the performance measurements. Reuse the shared generic AST, evaluator, and automaton compiler; the needed fixes do not justify separate copies of each language or unrelated compiler machinery. Preserve constructor parity with the *reviewed checkout's* symbol enum. A byte zipper remains optional, as the original request allows.

Implementation order should be string semantics and hygienic binding, then evaluate-once dispatch and sound bounded annotations, then stable interner integration and the final acceptance measurements. The byte-key repair is independent and small. No extra runtime metadata or new language operators are necessary for these repairs.
