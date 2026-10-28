# Zippy_bits and Zippy_bytes

`src/main/scala/AlphabetLanguages.scala` defines `ZSpace[A]`/`ZPath[A]`, which have the same cases as
`Space`/`Path` (checked by the `Mirror` labels in `AlphabetLanguagesCheck`). Only the path-item type
differs: `Boolean` (Zippy_bits), `Byte` (Zippy_bytes), `String` (Zippy_symbols = the existing `Space`,
through `Symbols.fromSpace/toSpace`). `Iteration`/`Fold` bind a single letter, so the heads of a
Zippy_bits space are a subset of {0,1}, and the heads of a Zippy_bytes space fit in a 256-bit mask.

Reference backends (one evaluator, `Evaluator`, that follows `morkl.eval` case for case):
- Zippy_bits: `Set[BigInt]`. A path is stored with a leading sentinel 1 bit, so `ε`, `0` and `00` are
  different values.
- Zippy_bytes: `Set[ByteStr]` (an immutable byte array, ordered unsigned-lexicographically).

## Translations (`AlphabetTranslate.scala`)

Every translation is an instance of one construction: a code `enc: A -> Word[B]` that is prefix-free,
order-preserving, has codewords of at most `maxLen` letters, and is recognised letter by letter by an
automaton (`Fsm`).

| code | from -> to | codeword | automaton |
|---|---|---|---|
| `symbolsToBits` | interned symbols -> bits | Fibonacci code of 1..n, assigned in sorted order | 2 states (`11` ends) |
| `stringsToBytes` | string symbols -> bytes | UTF-8 then `\0` | 1 state |
| `internedToBytes` | interned symbols -> bytes | 4-byte big-endian rank | 4 states |
| `bitsToBytes` | bits -> bytes | false->0, true->1 | renaming |
| `bytesToBits` | bytes -> bits | 8 bits, MSB first | 8 states |
| `bitsToSymbols` | bits -> symbols | "0", "1" | renaming |
| `bytesToSymbols` | bytes -> symbols | "000".."255" | renaming |

How each kind of op translates:
- Set and monoid ops, `Restriction`, `Raffination`, `Fixpoint`, `Range` and `Call` translate
  homomorphically: this follows from the code being prefix-free and order-preserving.
- Grounded functions are composed with decode/encode.
- `TailsUnion`, `TailsIntersection`, `Iteration` and `Fold` observe one source letter. They become
  routines in the target language that walk a codeword with the target's own one-letter
  `Iteration`, dispatching on the automaton state. The routines are unrolled by the code length
  bound `maxLen` (for Fibonacci codes that is about log_φ(n)+2), and `…_k0` is `Empty`.
- `Fold` walks the head letters in order, least first (read off with `Range`), and threads the
  accumulator through subtree routines that return `{acc}`. Those routines turn a singleton space
  back into a path with `GroundedSP`, the language's only Space->Path operation, using one fixed,
  program-independent extractor.
- `TailsIntersection` uses the target's own `TailsIntersection`, together with an incomplete-codeword
  junk path that keeps an empty sub-result's group present. The junk path is subtracted afterwards.
- Lazy guards are written `Iteration(Wrap(g, l), _, _, T)`: `T` is evaluated once, and only when
  `g` is non-empty.

No metadata and no new language construct is introduced. Limits: universes must be supplied for
interned codes (`Codes.symbolsOf`), and `stringsToBytes` rejects symbols containing NUL as well as
universes whose UTF-8 order differs from their `String` order. The PathMap byte-zipper reference
backend is not implemented yet.
