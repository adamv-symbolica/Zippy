# Zippy_bits / Zippy_bytes: contract and limitations

Code: `src/main/scala/ZippyCoded.scala`. Acceptance: `morkl.ZippyCodedCheck` (registered in `scripts/gates.py`). Benchmark: `sbt "runMain morkl.ZippyCodedBench"`.

## Contract
- `ZSpace[A]` mirrors `Space` case for case; bits = `ZSpace[Boolean]`, bytes = `ZSpace[Byte]`.
- Reference back-ends: `Set[BigInt]` (bits) and `Set[ByteArr]` (bytes, immutable). `ZEval` is the shared evaluator. It is trampolined (`TailCalls`), so generated recursion uses heap, not JVM stack. Tests run the review's witnesses on a 512 KiB thread stack.
- `CodedTranslation(code, main, routines)` uses only existing operators and routines. It stores no metadata and never adds a grounded callback to a pure program.
- Codes:
  - `string-nul` (total and ordered over Java strings);
  - `interner-4` / `fib-interner` (stable `Interner` ids);
  - `dict-4` / `fib-dict` (closed, sorted dictionaries);
  - `byte-8bit`;
  - injections bit→byte, bit→symbol and byte→symbol.
- Ordered operators under stable-id codes:
  - A `Fold` whose accumulator is `_` or unchanged lowers to one `Iteration` under every code.
  - Other `Range` / `Fold` need a frozen admissible vocabulary: `InternerId4.ordered(vocab)` or `FibInterner(bound).ordered(vocab)`. Ids stay the interner ids. Ordinary recoding routines map into an order-preserving projection and back.
  - Program constants must lie in the vocabulary (checked at compile time).
  - Input symbols outside it are inadmissible. A recoding table lookup drops them silently; nothing reports an error at run time.
- Fibonacci decoders are depth-specialized by the maximum code length (`fibonacci(idBound).length`, computed directly) within `depthBudget`. Above the budget they use recursive state routines.

## Limitations (measured, `ZippyCodedBench`, single runs)
- A `Fold` with a changing accumulator under stable ids is quadratic in the number of groups. Each step selects the minimum, subtracts it and unwraps over sets of size n. At 20,000 paths (5,000 heads) it took about 20 s against 18 ms in the source evaluator. Unchanged-accumulator folds, tails and `Range` stay within a small factor (≤ 15×) of the source.
- The byte zipper (PathMapModel) back-end is not implemented. It was optional.
