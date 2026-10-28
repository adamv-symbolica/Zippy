# Review of the Zippy_bits / Zippy_bytes attempt (`.review`, HEAD 7d5f50e)

Baseline: `scala-cli test … --test-only 'morkl.*Alphabet*Test'` passes its 24 tests
(21 in FiniteAlphabetTest, 3 in AlphabetExamplesTest; log in /tmp/rev-test.log). Both reference backends
are present (`Set[BigInt]` with a sentinel bit, `Set[BytePath]`). All seven codecs and the byte zipper
are present, and the syntax mirrors `Space`'s 24 cases. The claim is falsified on scaling: the
translated letter-observing ops do not run on modest inputs.

## P0. TailsIntersection over interned bits overflows the stack at 25 heads (reproduced)
Probe: a temporary `src/main/scala/ZZProbe.scala`, since deleted. It interned `s0000..s00NN` plus
`t` in reverse order, then ran
`AlphabetTranslation.symbols(Space.TailsIntersection(Literal{sI.t}), AlphabetEncodings.internedBits(interner)).eval()`
with `_JAVA_OPTIONS=-Xss32m`.
- n=2, 3, 6: correct, in 7–12 ms.
- n=25: `java.lang.StackOverflowError`. The frames are `AlphabetEvaluator.run`/`rec`
  (FiniteAlphabet.scala:151/176/185/194).
- `morkl.eval` on the same program: 0 ms.

Expected: `{t}`. Observed: crash. Violated invariant: the translation must preserve `eval` on every
program the symbol language handles trivially.

Cause (AlphabetTranslation.scala `tailsIntersection`): the loop peels off ONE head per recursive call,
and every call routes through `firstHead`, then `orderedRange`, then `recode`. So the stack depth is
#heads × (scanner depth + recode depth), and each level rebuilds the whole remaining set.

Fix: intersection needs no order, so drop `firstHead`/`orderedRange` there. Better, have the target do
the intersection itself: per letter, wrap each group's sub-result together with an incomplete-codeword
"junk" path, then take the target `TailsIntersection` and subtract the junk. This makes stack depth
O(maxWidth) and removes the head-count recursion.

Validate: a test with n ∈ {25, 200, 2000} at the default `-Xss`, compared against `morkl.eval`.

## P1. Range, Fold and TailsIntersection on interned codecs scale with |alphabet| × |heads|
- `orderedRange` recodes through a helper that is a `Union` over ALL snapshot atoms (`pairs.foldLeft`).
  Its code size and per-level work are proportional to the interner size, not to the program.
- `fold` calls `firstHead` (a full recode plus Range) once per head, which makes it quadratic or worse.
- Probe at n=25: Fold took 85 ms translated vs 23 ms symbolic, with 36 routines and 1822 nodes
  for a 4-node source term.

The request says the interned bit length should be "a real bound on recursion depth informing
unrolling". The current attempt pays O(alphabet) per ordered op instead.

Fix: make interned codes order-preserving at snapshot time. Sort the snapshot's symbols and assign
them the lexicographically sorted Fibonacci codewords, or big-endian 4-byte ranks for the bytes codec.
Then `Range` translates to the target `Range` directly, `Fold` walks letters least-first, and
`recode` / `orderedAtoms` can be deleted.

Validate: generated node count is independent of interner size (assert it is equal for interners of
10 and 10 000 symbols), and timings for n = 25 / 200 / 2000 are recorded in build.log.

## P1. Laziness depends on Composition short-circuiting, which is not part of the semantics
`choose` / `guard` use `Composition(truth(t), body)`. They only avoid evaluating the wrong branch
because `AlphabetEvaluator` and this tree's `eval` happen to return early on an empty left operand.

Risk (not reproduced): an eager backend (zipper, IntTrie or op-graph exec) evaluates both branches.
- The cost doubles at every scanner level.
- `done` runs on partial codewords, so a grounded callback decoding a partial symbol throws.

Fix: use a guard whose laziness is semantic, `Iteration(Wrap(test, c), _, _, body)` (the body runs
once, and only when `test` is non-empty).

Validate: run the translated test programs on `Space`'s compiled executor after the bits→symbols
injection.

## P2. Adherence gaps
- `docs/FINITE_ALPHABETS.md` claims "full 2x2 reachability" runs on the new backends, but
  AlphabetExamplesTest only names "runs all four pure 2x2 move functions". Either add the
  reachability fixpoint or correct the doc.
- The request's "ByteSpace.Head … can be modelled e.g. by a bitmask" is met only as an observer
  (`headMask`). It is not used to make Head/TailsIntersection cheap in the reference backend.
- Not reproduced, but worth stating: `BytePaths.concat/take/drop/compare` call `toArray` (a clone)
  on every operand, so every comparison inside the `Range` sort allocates two arrays. Measure a
  `Range` over 1e5 byte paths before and after comparing the raw arrays.

## P3. Minimality
- `close()` iterates closure conversion to a fixpoint over the whole generated graph. Passing
  captures explicitly when a scanner is generated (which `iterate` already computes as `cp`/`cs`)
  would make the global fixpoint unnecessary.
- `orderedRange` / `recode` / `orderedAtoms` disappear once the codecs are order-preserving (see P1).
