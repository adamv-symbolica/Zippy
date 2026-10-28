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

Every translation is an instance of one construction: a prefix-free code `enc: A -> Word[B]`
recognised letter by letter by an automaton (`Fsm`), with an optional sound length `bound` (the
unrolling depth; `None` = plainly recursive routines, still terminating since each call consumes a
letter), an optional fixed `width`, and an `orderPreserving` flag.

| code | from -> to | codeword | bound / width | order |
|---|---|---|---|---|
| `symbolsToBits(maxId)` | interned -> bits | Fibonacci(existing `Interner` id + 1), Long weights | from optional `maxId` contract (enforced) / – | id order: no |
| `stringsToBytes(maxUnits)` | strings -> bytes | UTF-16 unit c -> byte c+1 (c<0x7E) else `7F hi lo`; then `00` | 3·maxUnits+1 if contracted (enforced) / – | `String.compareTo`: yes |
| `internedToBytes(4)` | interned -> bytes | 4-byte big-endian existing `Interner` id | 4 / 4 | id order: no |
| `bitsToBytes` | bits -> bytes | false->0, true->1 | 1 / 1 | yes |
| `bytesToBits` | bytes -> bits | 8 bits, MSB first | 8 / 8 | yes |
| `bitsToSymbols` | bits -> symbols | "0", "1" | 1 / 1 | yes |
| `bytesToSymbols` | bytes -> symbols | "000".."255" | 1 / 1 | yes |

The string code is total and injective on every JVM string (NUL, lone surrogates included).

How each kind of op translates:
- Set/monoid ops, `Restriction`, `Raffination`, `Fixpoint`, `Call` translate homomorphically
  (prefix-freeness). `Range` and `Fold` too, for order-preserving codes; the interned codes refuse
  them with `UnsupportedOperationException` (an explicit, documented limitation: id order is not
  string order and no in-language rank conversion is generated yet).
- Annotations: `PathRef.known(n)` becomes `known(n·width)` for fixed-width codes and is dropped
  otherwise (an exact length is never copied as a falsehood).
- `TailsUnion`, `TailsIntersection`, `Iteration`, `Fold` become routines walking a codeword with the
  target's own one-letter `Iteration`, dispatching on the automaton state.
- `Fold` is ONE walk per source group: the least path of the remaining space (`Range(_,1,2)`, bound
  by an `Iteration`, no `GroundedSP`) gives the least head; at the codeword end the body runs and
  `next(acc, head; X∖head)` re-enters with the update evaluated once. Pure in, pure out.
- Hygiene: generated names use a prefix `§<n>.` chosen so no source name starts with it
  (deterministic). Lifted bodies take binders as formals in source binding order (Fold: acc, then
  head); `_` binders become fresh unused formals, so an outer `_` is never captured.
- `TailsIntersection` still uses an incomplete-codeword sentinel inside one expression (subtracted
  before the result is observable).

Tests: `morkl.AlphabetLanguagesCheck` (registered in `scripts/gates.py`). Codes are built before
evaluation and never see program outputs. The PathMap byte-zipper backend is not implemented.
