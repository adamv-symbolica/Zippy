# Review of `.review` at `e5ad576` (side-a finite-alphabet words)

**Verdict: the completion claim does not hold.** I inspected the round-3 commits (`934c889..e5ad576`), `WordEncoding.scala`, `WordCompiler.scala`, `ByteTrie.scala`, `ZippyTranslations.scala`, and the `WordEncoding*Test` suites. I did not change the implementation.

**Probe location.** The probe source and logs are in `~/polemic-notes/review-r3/`:
- `Probe.scala` (main `morkl.ReviewProbe`) and its output in `probe.log`;
- the full suite output in `suite.log`.

Run them as follows:

```bash
cd /home/ubuntu/workspace/.review
env _JAVA_OPTIONS='-Xmx8g' scala-cli run src/main/scala ~/polemic-notes/review-r3/Probe.scala \
  --server=false --scala 3.8.1 --source 3.3 \
  --dependency org.scala-lang.modules::scala-collection-contrib:0.3.0 --main-class morkl.ReviewProbe
```

**Preserve these strengths:**
- the shared word AST and trampolined `WordEvaluator`;
- total CESU-8 strings that keep Java `compareTo` order (verified on 10k random pairs);
- the lifted decoder bodies, with polynomial nesting size;
- separate sorted-dictionary codecs;
- the explicit, non-weakened failing performance test and the honest R3 report;
- the existing `toOrder`/`fromOrder` recoder machinery.

## P1. Requested stable interner-ID routes still reject `Range` and `Fold` (reproduced)

**Evidence.**
- `WordCompiler.scala:263` and `:291` throw `UnsupportedEncodedOrdering` for both literal-ID codecs.
- `probe.log` shows the result on the review literal `Range(Literal{z,a},0,1)`: both the `ibits` and `ibytes` routes raise `UnsupportedEncodedOrdering`. Expected `{a}`.
- `WordEncodingReviewTest:55-56` *asserts* the rejection. The corpus "matrix" counts 180 of the 1000 programs as raised errors. That is an omission of the requested `interned symbols → bits (Fibonacci)` and `→ bytes (4-byte id)` translations over the full constructor set. It has been relabelled as a limitation.

**A sharper sub-defect: the throw happens before order-free cases are detected.**
- In `fold`, the throw at `:291` runs *before* the `update == deref(acc)` branch at `:296`. That branch is a plain Iteration that needs no ordering. So an unchanged-accumulator Fold, and an `_` accumulator, are rejected for literal IDs although they are well-defined.
- `Range(x, 0, n)` on a statically order-free use is also rejected.
- Status: established by reading the code. The throw path itself was executed in the probe.

**Requested change (small, since the machinery exists).**
- Replace `unsupportedOrdering: Option[String]` with an optional frozen admissible-vocabulary snapshot, for example `internedSymbolsToBits(interner, orderedVocabulary = Some(names))`. Set `orderedDigits` to the snapshot's ids sorted by `show`.
- The existing `orderCode` + `recoder` (`:229-261`) then compiles `toOrder`/`fromOrder` for literal IDs exactly as it already does for `preservesOrder = false`. The id bytes stay unchanged; no dictionary ranks are used.
- Move the order-free Fold check above any ordering requirement.
- Throw only when an ordered op actually needs order and no snapshot was supplied.
- At compile time, reject program constants that are outside the snapshot.

**Validation.**
- Delete the `intercept[UnsupportedEncodedOrdering]` assertions. Add differential tests on literal-ID bits and bytes covering:
  - the review literal;
  - interning order opposite to lexical order;
  - negative Range bounds;
  - an accumulator-sensitive Fold;
  - Range inside `GroundedSP`;
  - mutual routines.
- Assert that `encode(p("b"))` is unchanged before and after unrelated interning.
- Turn the corpus matrix's 180 "raised" rows into executed rows that equal the source evaluation.

## P1. The acceptance suite fails, and the changing-Fold gate is missed by more than claimed (reproduced)

**Evidence.**
- `suite.log` shows the run with the documented command: 17 tests, **1 failed, exit 1**.
- Probe B at 16,000 groups:

  | Route | Ratio to source |
  |---|---|
  | CESU8 | **26.95×** (655.6 ms vs 24.3 ms source) |
  | dictionaryFib | 15.7× |
  | dictionary4 | 13.3× |

- R3 reports CESU8 at 20.89×. That result is not stable; it is worse here.
- Growth 4k→16k gives an exponent of about 1.36 for CESU8 (97→656 ms), above the test's own 1.3 gate.

**Likely cause** (from reading `:298-314`). Every step of the loop does all of the following:
- `orderedRange(heads, 2, 0)`, which rebuilds the remaining head set;
- `Range(heads, 0, 1)`;
- `Unwrap(input, selected)`;
- a `bindHead` dispatch (Fibonacci) or a re-iteration of the selected head.

The first two steps alone make n persistent-set rebuilds.

**Requested change.**
- Take the min with a single scan (or `firstKey` on a sorted backend) instead of `Range(…,0,1)`.
- Replace "rebuild remaining heads" with `Subtraction(heads, Singleton(selected))`, which is O(log n) on a sorted set.
- Profile allocations with `-Xlog:gc` or JFR on the real path (`program.eval`), not on synthetic micro-ops.

**Validation.** Make the existing Probe B pass unmodified, with ≤20× and an exponent ≤1.3, in three consecutive runs. Report the medians in `docs/FINITE_ALPHABET_REVIEW_R3.md`.

## P1. `ByteTrie` set algebra is not stack-safe, and its iteration is quadratic in path length (reproduced)

**Evidence (`probe.log`).**
- On a 512 KiB thread, `ByteTrie.fromPaths(Vector(q)).union(fromPaths(Vector(q ++ [1])))` with a 100,000-byte `q` throws **StackOverflowError**. Cause: `union`, `intersect`, `subtract`, `restrict`, `compose` and `slice` recurse once per byte.
- `iterator` copies the full prefix at every node (`ByteWords.concat(prefix, …)`):
  - one 20,000-byte path takes 27 ms;
  - one 80,000-byte path takes **268 ms**, about 10× for 4× the length, which is quadratic.
- The "stack safe on both byte evaluators" test only passes because the suite runs with `-Xss8m`, and only for 5,000-byte words.
- `Zippy_bytes.evalZ` itself survived a 65,536-char CESU head on 512 KiB, because that program performs no deep union.

**Requested change.**
- Make the binary trie operations iterative: use an explicit work stack, or path-compress single-child chains, which also removes the per-byte `IntMap` overhead.
- Make `iterator` keep a mutable byte buffer (push and pop one byte) and copy only when it emits a path.
- Drop the per-construction `require(children.forall…)`, which is O(children) for every node built. Keep that check in a test-only validator.

**Validation.**
- Run the binary ops (`union`, `intersect`, `subtract`, `restrict`, `compose`, `slice`) on 100k-byte overlapping paths on a 512 KiB thread.
- Iterate 20k/80k/320k-byte single paths and assert near-linear growth (exponent ≤1.2).
- Remove `-Xss8m` from the documented acceptance command.

## P2. The literal-ID Fibonacci decoder is sized for `Int.MaxValue`, and the bit routes are 7× slower than source on plain Iteration (measured)

**Evidence.**
- `internedSymbolsToBits` sets `maxCodeLength = FibonacciCode.encode(Int.MaxValue).size` regardless of the interner.
- On a 20k-path Iteration (`probe.log`):

  | Route | Nodes / routines | Median | vs source (22 ms) |
  |---|---|---|---|
  | ibits | 1722 / 92 | 160 ms | 7.3× |
  | dictFib | 810 / 44 | 162 ms | — |
  | ibytes | — | 59 ms | — |
  | cesu | — | 103 ms | — |

- So the routine count is driven by the global bound, and the Fibonacci decode dominates the time.

**Requested change.**
- Bound by `FibonacciCode.encode(maxAdmissibleId).size` when a snapshot or explicit id bound is supplied. Keep the `Int.MaxValue` default only for open-world use.
- Reuse `bindHead`-style whole-word membership for literal IDs when a snapshot exists.
- Investigate why a 1-bit Iteration step costs about 8 µs per path on BigInt words. A likely cause is `BitWords.digits`/`fromDigits` boxing in `split` and `concat`.

**Validation.** Report nodes, routines and the eval median at 1k/4k/20k paths for ibits with and without a snapshot. Target: routines ≤ 2·⌈log_φ(idBound)⌉+1, and ibits within 3× of ibytes.

## P2. Adherence and documentation gaps

- **Open-world contract.** The open-world literal-ID contract ("future symbols accepted") is valuable; keep it. But `docs/FINITE_ALPHABETS.md` should state plainly that ordered operations require a snapshot, not "use a different codec".
- **Benchmark entry point.** The 20,000-path benchmarks for the *stable-ID* route do not exist, because those routes cannot run Range/Fold. Add them once P1 lands; `WordEncodingBenchmarks.scala` is the durable entry point.
- **Gate registration.** Check whether `WordEncoding*Test` is registered in the repository's acceptance gate (for example `scripts/gates.py` `GATE_SUITES`, if present). If not, register it. Then demonstrate one controlled failing assertion (exit 1) and its restoration (exit 0).

## P3. Minimality

- **Remove the parallel limitation flag.** `unsupportedOrdering` duplicates the `preservesOrder`/`orderedDigits` concept. Once a snapshot drives `orderedDigits`, delete the field, the `UnsupportedEncodedOrdering` class, and the two throws. That deletes code rather than patching around the concept.
- **`orderCode` decode is linear per digit.** It uses `codes.find(_._2 == ds)`, O(alphabet) per digit. Use an inverse map; this matters once literal-ID snapshots of 20k symbols use it.
- **`ByteTrie.childMask` is test-only.** It is a `BigInt` built per node on demand, and is only used by a test. Derive it in the test, or drop it.

## Priority order

1. Literal-ID ordered ops via a frozen snapshot, plus order-free Fold before the throw.
2. Make the changing-Fold gate green without weakening it.
3. Stack-safe and linear `ByteTrie`.
4. Fibonacci bound and bit-route constant factors.
5. Docs, gate registration and deleting `unsupportedOrdering`.
