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
  - Input symbols outside it are rejected at the encoding boundary: `enc`/`encPath` of a snapshot codec throws, BEFORE interning, and so does any Grounded callback result crossing the host adapter. Target sets built by hand (not through `encPath`) are subject to the same contract; an inadmissible id in such a set is not reported.
  - `FibInterner(bound)` rejects a new symbol whose id would reach the bound without interning it (`Interner.lookup`).
- `Fold` lowering: the order-free shortcuts (`_` or unchanged accumulator → one `Iteration`) apply only when the template, every routine it calls, and the erased initial/update contain no Grounded callback. Otherwise the fold is sequenced exactly as `eval` does it: initial once (also on empty input), then body and update per group in canonical order. All codes share one sequenced lowering (heads extracted once, least head by `Range(heads,1,2)`, group by `Unwrap(src, h)`).
- `ZEval` indexing: large sets get a sorted index on first prefix/ordered query; Subtraction/Raffination of few elements carry it over, so a fold over n groups costs O(n log n) set work on the byte routes. Caches for routine literals live with the evaluator; all other caches live for one `apply` call (`cacheSizes`).
- `ByteArr` is a window on a shared buffer: `uncons`/`dropPrefix` share, `concat` appends in place at the buffer's high-water mark. A 131,072-character string head now evaluates in ~0.5 s with ~8.5 KB allocated per character (constant across 1 K..128 K), where it previously exhausted a 12 GiB heap.
- Fibonacci decoders are depth-specialized by the maximum code length (`fibonacci(idBound).length`, computed directly) within `depthBudget`. Above the budget they use recursive state routines.

## Limitations (measured by `ZippyCodedCheck` and `ZippyCodedBench`, warm medians)
- Bounded-output Fold (accumulator = previous head), 1,000 → 16,000 groups, against original `eval` (6.4 / 11.5 / 54.5 ms):
  dict-4 24.5 / 54.7 / 248.5 ms, interner-4 (ordered) 22.7 / 97.0 / 416.0 ms, string-nul 32.9 / 124.4 / 937.3 ms,
  fib-dict 49.6 / 243.2 / 2,396.8 ms, fib-interner (ordered) 127.6 / 1,025.9 / 4,009.6 ms.
  The acceptance gate (growth exponent ≤ 1.3 and ≤ 20× `eval` at 16 k) is kept strict and currently FAILS on the bit (Fibonacci) routes and on string-nul's slowdown budget: `BitRepr` paths are BigInts whose concat/drop/compare are O(length), and the bit decoders run more steps per symbol. Not yet fixed.
- The byte zipper (PathMapModel) back-end is not implemented. It was optional.
