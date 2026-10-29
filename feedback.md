# Review of `.review` (WordLanguage / WordCompiler / WordEncoding) — falsification attempt

Scope reviewed: `git diff 060de30 HEAD` (11 files, +1531): `WordLanguage.scala`, `WordCompiler.scala`,
`WordEncoding.scala`, `WordReferenceHints.scala`, `SymbolWordLanguage.scala`, `ZippyTranslations.scala`,
`ByteZipper.scala`, `FiniteAlphabetTest.scala`, `docs/FINITE_ALPHABETS.md`, plus the `PathRef.bounded` hook in MORKL.

The suite itself is green (`scala-cli test ... --test-only morkl.FiniteAlphabetTest`: 14/14 pass, ~1.4 s).
The claim that does not hold is that the translations are a usable *reference*. Ordered operations
(`Range`, `Fold`) do not scale on the interned encodings, and every test input is tiny (≤ 9 paths).
The repro harness is `~/polemic-notes/exp/Exp.scala`. It is run with the same `scala-cli` flags as the
docs plus `--main-class morkl.Exp`, `-Xss64m` and `-Xmx16g`. Raw output is in
`~/polemic-notes/exp/out.log` and `out2.log`.

## P1 — Correctness/performance defect: translated `Range` on interned codes is quadratic and overflows the stack

Repro (`Exp.scala`, E2): `Space.Range(Mention x, 0, 1)` where `x` = { s_i.z | i < n }, compiled with
`internedSymbolsToBits` / `internedSymbolsToBytes`, evaluated with `WordEvaluator`:

| n | symbol `eval` | Fibonacci coded | interned-4 coded |
|---|---|---|---|
| 200 | 1.4 ms | 214 ms | 227 ms |
| 1000 | 0.9 ms | **1977 ms** | **1834 ms** |
| 4000 | 1.9 ms | **`StackOverflowError`** (even with `-Xss64m`) | — |

- **Expected:** a result equal to the symbol result, at comparable cost.
- **Observed:** cost grows by about n² (5× n gives 9× time), and at n = 4000 the program crashes.
- **Violated invariant:** a translation must be total wherever the source evaluates.

**Cause** (`WordCompiler.recoder`): `mapped` is a left-deep `Union` with one `guard(...)` arm per
symbol of the alphabet (`encoding.orderedDigits.map {...}`). It is rebuilt and evaluated inside
`iterate` for every head group of every path. The depth is therefore |alphabet| (stack overflow), and
the work is |alphabet| × |groups| × path length. The same recoder sits on the hot path of every `Fold`
step (`fold` → `orderedRange(heads, 0, 1)`), which also rescans all heads on each step.

**Fix (smallest, and it deletes code):** make the interned codes order-preserving *by construction*,
so `preservesOrder = true` and `Range`/`Fold` stay native.
- `internedSymbolsToBytes`: assign 4-byte IDs in sorted symbol order (rank = position in `order`).
  Big-endian IDs then sort like the symbols.
- `internedSymbolsToBits`: take the k smallest Fibonacci codewords, sort them lexicographically, and
  give the i-th smallest symbol the i-th smallest codeword. The code set (and so the max length and
  recursion bound) is unchanged, and lex order equals symbol order because the code is prefix-free.

This removes `orderCode`, `recoder`, `toOrder`/`fromOrder` for these encodings, and most of
`orderedRange`.

**Validation:** rerun E2 with n ∈ {1000, 4000, 20000}. Require no stack overflow and coded time within
a small constant factor of symbol `eval`. Add n = 4000 as a regression test.

## P2 — Performance: `Fold` is quadratic and re-recodes on every step; nested `TailsIntersection` re-evaluates its source 3× per level

- **Fold — reproduced crash.** E3 (`Fold(x, ε, a, h, t, Singleton(h), a)` over n single-symbol paths):

  | n | symbol `eval` | interned-4 coded | UTF-8+NUL coded |
  |---|---|---|---|
  | 50 | 15.5 ms | 297 ms | 81 ms |
  | 200 | 5.2 ms | **3846 ms** | **`StackOverflowError`** (with `-Xss64m`) |

  Interned-4 goes 13× slower for 4× n (quadratic or worse). Coded `Fold` is not total at 200 groups,
  which an `Iteration` over the same input handles trivially. Cause: `loop` recurses one `Call` per
  group, *nested inside* `iterate(orderedRange(...))`. Every level adds the recoder/transducer
  recursion (`utf8OrderRecoder` recurses once per **byte** of every remaining path) to the stack.
  E4 (nested `TailsIntersection`) and E6 (`execZ`) were not reached in my time budget; rerun them.
- **Fold.** `fold` → `loop` evaluates `orderedRange(heads, 0, 1)` per group. Here `heads` is the full
  head set of the remaining input (O(groups) each step), plus the P1 recoding. Total work is
  O(groups²) recoder calls.
  - Validate with E3 (`Fold` with n ∈ {50, 200, 800} groups, interned-4 and UTF-8) and record the
    growth exponent.
  - After the P1 fix, `Range(heads, 0, 1)` is native. Better still, select the minimum group with
    `Range(Subtraction(input, ε), 1, 2)` on the *paths*: the least path's head is the least head, so
    no `heads` space needs to be built.
- **TailsIntersection.** `space(TailsIntersection(src))` builds `Subtraction(all, iterate(source, …,
  Subtraction(all, t)))`. Here `all = tails(source)` appears twice, and `source` (an arbitrary
  translated expression) is captured as a free subexpression of the lifted body. So it is
  re-evaluated once per head group, and nesting multiplies the cost by ≥ 3 per level.
  - Validate with E4 (nested depth 1..6) and look for geometric growth.
  - Fix: lift to a routine `TI(; X) = TIbody(; X, tails(X))` so `X` and `U` are each evaluated once and
    passed as mentions.

## P3 — Fragile design: guards rely on the evaluator short-circuiting `Composition`

Every scanner branch is `guard(test, body) = Composition(nonEmpty(test), body)`, and every Fibonacci,
terminated and recoder step evaluates *both* arms. The result is right only because `WordEvaluator`
(`for p <- rec(x); q <- rec(y)`) and MORKL `eval` skip the right operand when the left is empty.
That is a property of these two evaluators, not of the algebra:
- An eager backend (trie / zipper executor, a future native bit/byte backend) would evaluate the
  wrong branch.
- The wrong branch calls the user's lifted body with a **non-codeword head** (`finish(nextPrefix, …)`
  on a `0` digit). A body whose recursion depends on its head may then diverge.
- Branching is ×2 per digit, i.e. 2^(code length) per symbol, unless the evaluator is lazy.

This is a risk, not a reproduced defect: E6 (the injected bits→symbols program on `execZ`) is in
`Exp.scala` but did not run within the time budget.

**Fix (simpler and backend-independent):** dispatch by *filtering the space, not guarding the result*.
`Iteration(Restriction(X, Singleton(c)), _, r, k_c)` enters `k_c` only if some path starts with `c`.
For "any other byte", use `Raffination(X, Singleton(0))`. This needs no `nonEmpty`, no `Range` probe
and no `Composition`, and each branch receives only its own sub-space.

**Validation:** evaluate the generated programs (after `bitsToSymbols` / `bytesToSymbols`) on
`execZ` / `execT` as well as `eval`. Also count `Call`s per input symbol and assert O(code length),
not O(2^code length).

## P4 — Adherence gaps

1. **Unrolling is opt-in, and its output is not unrolled.** The request says the interned max bit
   length "translates to a real bound on recursion depth informing unrolling". Here `unrollBounded`
   defaults to `false` in every public entry point, and `internedStringsToBytes` does not even expose
   it. When enabled, it emits one routine per (state, depth) — still `Call`s, just acyclic (E1: 24–36
   routines).
   - The default should follow the bound: unroll when the universe is small.
   - With a known universe, unroll over the **code trie** with constant heads. That is
     O(k · maxLen) nested `Iteration`s and no routines at all, so the bound is visible in the program.
   - Measure E1 before and after. Today, unrolled Fibonacci at n = 4000 is *slower* than recursive
     (105 ms vs 76 ms).
2. **The byte-zipper is a cursor, not a back-end.** `ByteZipper` (32 lines) is a read-only cursor
   that filters the entire `Set` on every `childMask`, `makeMap` or `pathExists` call, so each move
   costs O(|space| · |path|). It cannot evaluate a `ByteSpace` program. The request points at
   `~/PathMap/lean/PathMapModel` for a byte-zipper *reference*.
   - Either implement `evalZ` for `WordSpace[BytePathValue]` over a byte trie (256-way children with a
     mask, which is what makes `Head` "≤ 256, a bitmask") and test it differentially against
     `WordEvaluator`, or label the zipper explicitly as out of scope in the docs.
3. **The "string symbols to bytes" encoding is not plain "\0 as the terminator".** In-band escaping
   (NUL → 1,1; SOH → 1,2) plus an ED-insertion transducer for supplementary characters goes beyond the
   brief. It changes the byte image of every symbol containing byte 1, so it is no longer plain UTF-8
   plus `\0`.
   - Defensible, but document it as a deviation and justify it against the simpler contract: reject
     NUL in symbols, and accept UTF-8 order with an explicit note that it differs from UTF-16 order
     only for surrogates.
   - Validate by measuring `Range` on strings with and without the transducer.
4. **Test scale is not adequate for a reference.** Every differential uses ≤ 9 input paths and
   hand-written expressions. Corpus-scale differential evidence is absent, and the repository has a
   fuzzer (`Fuzzer.scala`).
   - Add a seeded sweep over fuzzer-generated programs × random inputs (≥ 1000 programs) for all seven
     encodings and their compositions.
   - Add one input-size sweep per ordered operator (n up to 10⁴) with a time budget, which would have
     caught P1 immediately.

## P5 — Minimality / simplification

- **`WordOps` defaults go through `Vector[Int]`.** `compare`, `startsWith`, `concat`, `drop` and
  `split` all round-trip through `digits`, and `ByteWords` overrides none of them. Every byte `concat`,
  `startsWith` and `compare` allocates two boxed `Vector[Int]`s.
  - Implement them on the `ArraySeq[Byte]` directly: `Arrays.compareUnsigned`, `Arrays.equals` over a
    range, and array concat.
  - Validate with E5 (Range over 20 000 × 32-byte paths, Composition 300×300) before and after.
  - `BitWords.compare` also falls back to `digits`. Compare the two aligned prefixes as `BigInt`s
    instead: shift each so both have the shorter length, compare numerically, then break ties by
    length.
- **Duplicated syntax walkers.** `WordSyntax.free` and `WordSyntax.names` duplicate a full constructor
  walk. A single `children`/`paths` traversal (as `MORKL.collect`/`subs` already provide for `Space`)
  would halve them, and makes forgetting a constructor when the AST grows impossible.
- **Once codes are order-preserving (P1),** the `preservesOrder`, `orderedDigits`, `utf8StringOrder`
  and `orderCode` fields, and the `recoder`/`utf8OrderRecoder` routines, can be deleted. Roughly 80
  lines of the hardest-to-verify code go.
- **One mechanism for `TailsUnion`, `TailsIntersection` and the closures.** All are desugarings to
  `Iteration` plus a lifted routine. Express them once at the source level, as is already done for
  `tails`, so the compiler has a single atom-observing case (`Iteration`).

## Priority order to execute

1. P1 order-preserving interning (fixes both crashes, deletes the recoders); add n = 4000 `Range` and n = 1000 `Fold` regression tests.
2. P3 filter-based dispatch (removes the laziness dependency and the exponential branch evaluation).
3. P2 `Fold` minimum selection and `TailsIntersection` lifting, with the E3/E4 measurements recorded.
4. P4.1 default unrolling from the max code length; P4.4 a corpus-scale differential sweep.
5. P5 `ByteWords`/`BitWords` native ops (E5 before and after); then P4.2 (byte-zipper evaluator, or an
   explicit descoping in the docs).
