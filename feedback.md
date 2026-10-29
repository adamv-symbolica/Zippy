# Review of `.review` round 2 (WordEncoding / WordCompiler / WordLanguage) — falsification attempt

**Scope.** HEAD `9f9ae4c`, with focus on `1e9a8af` ("Fix encoded ordering, scanner dispatch and reference evaluation scale").

**Probe.** `~/polemic-notes/review-r2/Probe.scala`. It runs on the production compiler and `WordProgram.eval`:

```bash
env _JAVA_OPTIONS='-Xmx16g -Xss64m' scala-cli run src/main/scala ~/polemic-notes/review-r2/Probe.scala \
  --server=false --scala 3.8.1 --source 3.3 \
  --dependency org.scala-lang.modules::scala-collection-contrib:0.3.0 --main-class morkl.ReviewProbe
```

**What round 2 fixed.** Round 1's crashes are gone:
- interned `Range` is native;
- an invariant `Fold` is a single iteration;
- dispatch filters instead of guarding;
- the evaluator is trampolined.

**What still fails.** Four defects reproduce, and each is large:
- nesting makes code size exponential;
- the general `Fold` is quadratic;
- the string code is not total over Java strings;
- the "interned" codes are compile-time dictionaries, not interner ids.

## P1 — Default trie unrolling makes code size exponential in Iteration nesting (reproduced)

`iterate` with `unrollBounded=true` (the default) and a Fibonacci alphabet of ≤ 32 symbols calls `WordSyntax.substitute(body, h -> constant, t -> input)` once per codeword. The body is already compiled, so an inner unrolled iteration is copied k times at every level, giving k^depth copies.

Probe A uses k symbols and nested `Iteration(x,h1,t1, Iteration(t1,h2,t2, …Singleton(h1.hd)))`. Target AST nodes counted as a tree, with 0 routines:

| k | depth 1 | 2 | 3 | 4 (source = 26 nodes) |
|---|---|---|---|---|
| 8 | 326 | 2,902 | 23,510 | 188,374 |
| 32 | 1,334 | 43,894 | 1,405,814 | **44,987,254** |

- **Expected:** target size is O(|source| · k · maxLen). The request says the maximum code length should *inform* unrolling. It should not multiply the program.
- **Observed:** the size grows by ×k per nesting level. Compiling still takes only 227 ms because the JVM shares subtrees, but any printer, serializer, eager backend, or `WordSyntax.names`/`free` pass on the output walks all 45M nodes.
- **Violated invariant:** the translation must stay polynomial in program size. The "≤ 32 symbols" threshold bounds only the width of one level, not the size of the product.

**Fix (simplest):**
- Unroll the *code trie* over a lifted body: emit one `symbol_body` routine (as the non-unrolled path already does), and have the trie leaves `Call` it with the constant head. Copies of the body then disappear, and the size is O(k·maxLen + |body|).
- Alternatively, keep inlining but charge a global node budget. Fall back to the lifted body once `size(body) · k` exceeds the budget.

**Validation:** rerun Probe A. Require target size ≤ c·(source + k·maxLen) for depths 1..6 at k = 32, and add a regression test that asserts the bound.

## P1 — General (changing-accumulator) `Fold` is quadratic on every multi-digit code (reproduced)

Probe B is `Fold(x, ε, a, h, t, Singleton(a.h), h)` over n single-symbol paths. Every coded result was checked equal to the symbol result.

| n | symbol `eval` | interned-4 | Fibonacci | string-NUL |
|---|---|---|---|---|
| 500 | 23 ms | 225 ms | 256 ms | 2,388 ms |
| 1000 | 2.9 ms | 363 ms | 921 ms | 5,381 ms |
| 2000 | 4.7 ms | 1,357 ms | 5,082 ms | 22,355 ms |
| 4000 | 9.0 ms | **5,641 ms** | **26,063 ms** | **91,640 ms** |

Time grows ×4 per doubling of n: quadratic, and 600–10,000× slower than the source at n = 4000. The docs admit "can take quadratic work", but this is the ordinary `Fold`, not a corner case. `WordEncodingScaleTest` only tests the invariant-accumulator `Fold` at scale, and the changing-accumulator test uses n = 1000 with no budget comparison against `eval`.

**Cause (`WordCompiler.fold`).** Every step re-evaluates three O(n) operations over the whole remaining input, and the next step receives the remainder as a new space:
- `Range(Subtraction(input, ε), 0, 1)`, which sorts the whole remainder in the reference evaluator;
- for strings, two full `utf8_order` recodings per step;
- `Unwrap` and `Raffination`.

**Fix.** Decode groups *once* and sequence over the already-grouped structure:
- Use `Iteration` over the source to produce the set of (head, tail) groups. With prefix-free, order-preserving codes, `Range(src, i, i+1)` on encoded paths already enumerates groups in source order.
- Alternatively, recurse by splitting: `Range(S, 0, |S|/2)` / `Range(S, |S|/2, ∞)` cannot carry the accumulator, but `Range(S, 1, ∞)` on *heads only* can. Build `heads = Iteration(src, h, _, {h})` once. Then each step is `Range(heads,0,1)` / `Range(heads,1,∞)`. That is O(n log n) with a sorted-set backend, and it never touches the tails again.

**Validation:** rerun Probe B up to n = 16000. Require a growth exponent ≤ 1.3 and coded time ≤ 20× symbol `eval`, and add this exact program to `WordEncodingScaleTest` with those assertions.

## P1 — String code is not total over Java strings (reproduced)

`PathItem` is an opaque `String`. `stringSymbolsToBytes` uses `CodingErrorAction.REPORT`, so it fails on any lone surrogate:

```scala
val it = new PathItemInterner; it.intern(PathItem("\uD800"))
WordEncodings.stringSymbolsToBytes(it)          // MalformedInputException: Input length = 1
ZippyTranslations.stringsToBytes(Space.Literal(SpaceValue(Set(PathValue(List(PathItem("a\uDC00")))))))
                                                 // MalformedInputException at compile time
```

- **Expected:** every source symbol has a codeword, as for the source language.
- **Observed:** compiling or encoding throws.

**Fix (simpler, and it deletes code).** Encode each **UTF-16 code unit** with the 1/2/3-byte UTF-8 scheme (CESU-8 / "generalized UTF-8"), keep the NUL/SOH escapes, and terminate with 00. This gives three properties:
- **Total:** surrogates D800–DFFF become ED A0..BF xx.
- **Order:** byte order equals `String.compareTo` by construction. Each code-unit range maps monotonically, and surrogates sort below EE (U+E000).
- **No recoder:** `utf8OrderRecoder`, `utf8StringOrder`, and the `preservesOrder=false` path for strings can be deleted.

It also removes the 300× `Range` penalty measured in Probe E:

| n | interned-4 `Range` | string `Range` |
|---|---|---|
| 5,000 | 1.1 ms | 96 ms |
| 20,000 | 2.1 ms | 298 ms |

The authors' own ASCII control shows the same gap: 228 ms vs 0.7 ms.

**Validation:**
- Probe C must encode, decode, and round-trip `"\uD800"`, `"􏿿"`, `""`, `"?"`, and `"\u0000"`.
- Add a property test that byte-order comparison equals the sign of `String.compareTo` over random strings that include lone surrogates.
- Rerun Probe E. String `Range` should be within 2× of interned-4.

## P2 — "Interned id" codes are closed sorted dictionaries, not interned ids (adherence; reproduced)

The request says "interned symbols → bytes, using the 4 bytes of the interned id" and "interned symbols → bits using Fibonacci coding". Both `internedSymbolsTo*` replace the interner id with the symbol's **rank in a sorted, closed alphabet** that is fixed when the encoding is built.

Probe D shows three consequences:
- **Unstable codes.** Under alphabet {b}, `encode(b) = 00 00 00 00`. After `a` is interned, a fresh encoding gives `encode(b) = 00 00 00 01`. Codes change whenever the alphabet grows, so encoded data cannot be shared across compilations or with the trie/interner-based back-ends.
- **No compile-once for open inputs.** A compiled program cannot accept an input frame with an unseen symbol: `encode(a)` → `IllegalArgumentException: symbol 1 is outside the declared encoding alphabet`.
- **Side effect on failure.** The failed encode still *interns* `a` into the caller's interner, because `SymbolWords.digits` → `interner.encode`.

This is a defensible *specialization*, which buys native `Range`. But it replaces the requested codec rather than implementing it.

**Fix:**
- Provide the literal codec: 4 big-endian bytes / Fibonacci of `interner.intern(s)` (+1), stable and open-world.
- Lower ordered ops for it explicitly. `Range` and `Fold` order needs a source-order key. Either compute it per step from a recoder over the *heads only* (not every path), or document the operation as unsupported for that codec with a named error.
- Keep the rank-dictionary as a separately named `sortedDictionary*` encoding.
- Make `digits` non-mutating: use `ids.get`, not `intern`, when encoding against a closed code.

**Validation:**
- Encode the same symbol before and after interning others; the bytes must be equal.
- Compile once, then evaluate frames containing symbols interned after compilation.
- Confirm the interner size is unchanged after a rejected encode.

## P2 — Byte zipper still descoped (adherence)

`ByteZipper` (32 lines) is a read cursor that rescans the whole `Set`. The docs now say so honestly. The request names `~/PathMap/lean/PathMapModel` as the byte-zipper reference.

**Next step.** Implement `evalZ` for `ByteSpace` over a 256-ary trie with a child bitmask, so that `Head` is literally a ≤ 256 bitmask. Run the existing corpus differentially against `WordEvaluator`. This is the most valuable unrealized part of the brief, because bytes exist to run on a byte-trie runtime.

**Validation:** the corpus route `bytes(evalZ) == bytes(WordEvaluator)` passes on all 1000 programs × 2 frames.

## P3 — Ambition and measurement gaps

- **The corpus suite checks correctness only.** Record per-program `WordEvaluationStats.nodes` and the target AST size against source `eval`, and fail on outliers (for example, ratio > 50). P1 (size) and P1 (`Fold`) would both have been caught.
- **The "maximum code length bounds recursion" claim is only partly realized.** For > 32 symbols the scanner is recursive with a `bounded` prefix hint. No depth-indexed specialization exists, so the bound is not visible in the program structure. A depth-indexed DAG with a node budget would make the bound visible and keep size linear. It is O(states · maxLen) routines and works for k > 32 too.

## P4 — Minimality

- **Shrink or delete the recoders.** After the CESU-8 string code (P1), the only remaining users of `orderCode` / `recoder` are custom injections whose symbol names are not order-compatible. Either:
  - require order-compatible injection names (the defaults `false`/`true` and `byte_%03d` already are) and delete `orderCode`, `recoder`, `utf8OrderRecoder`, `toOrder`, `fromOrder`, and the `orderedDigits` / `preservesOrder` / `utf8StringOrder` fields (about 90 lines); or
  - keep a single recoder used only by injections.
- **Express each concept once.** The `Fixed`, `Terminated`-unrolled, Fibonacci-trie and scanner branches of `iterate` are four decoders for one concept. A single "code trie → (state, depth) routine DAG with a budget" generalizes all of them; fixed-width codes are just a full trie. That would cut `iterate` roughly in half and fix P1 (size) at the same time.

## Priority order

1. Bound unrolled code size, by lifting the body or applying a global budget, and add a regression test for the size bound.
2. Make the general `Fold` near-linear (heads computed once, sequenced via `Range` on heads), with Probe B as the regression.
3. Switch to the CESU-8 per-code-unit string code: total, order-preserving, with the recoder deleted. Validate with Probe C/E and the `compareTo` property test.
4. Add the literal interner-id codecs alongside the sorted dictionaries, and make encoding non-mutating.
5. Add size and work ratios to the corpus test. Then build the byte-trie zipper evaluator, with differential tests.
