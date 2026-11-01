# Bit and byte Zippy

`Zippy_bits` and `Zippy_bytes` have exactly the five `Path` cases and the
24 `Space` cases of the symbol language in `MORKL.scala`. The shared definition
is `DigitLanguage` in `RadixLanguage.scala`. `BitPath`, `BitSpace`,
`BitSpaceValue`, `BytePath`, `ByteSpace`, and `ByteSpaceValue` are convenient
aliases. The existing `morkl.Path` and `morkl.Space` remain the symbol frontend.

Each language has one set-based reference evaluator. Its path and space
continuations are trampolined, so deep generated routines and concatenations
consume heap continuations rather than JVM stack frames. Bit paths are positive
`BigInt`s with a leading sentinel bit: epsilon is 1, the path 0 is 2, and the
path 1 is 3. Thus zeroes and length survive without a separate length table.
The sentinel is never visible to language operations. Use `BitPaths.fromDigits`
and `BitPaths.digits` to construct and inspect paths.

Both dialects also support the [C++ / OpenFst backend](OPENFST_BACKEND.md),
which transpiles the same operations and routines to native functions. It can
execute either direct digit programs or any of the translations below.

Byte paths are `BytePathValue`s backed by immutable byte arrays with structural
equality. Construction and array access copy; `unsigned` exposes digits as
integers in 0..255. Ordering is unsigned lexicographic ordering, with a proper
prefix before its extensions. `BytePathValue(0, 255)` constructs a two-byte path.

```scala
import morkl.*

val bits = Zippy_bits.literal(BitPaths.fromDigits(Vector(0, 1)), BigInt(1))
val bitHeads = Zippy_bits.eval(BitSpace.Head(bits))
assert(bitHeads.paths == Set(BitPaths.fromDigits(Vector(0))))

val bytes = Zippy_bytes.literal(BytePathValue(0, 255), BytePathValue(255))
val byteHeads = Zippy_bytes.eval(ByteSpace.Head(bytes))
assert(byteHeads.paths.size == 2)
assert(Zippy_bytes.headMask(byteHeads) == BigInt(1).setBit(255))
```

`Head` is derived from `Iteration`, just like `Syntax.head` for symbols; it is
not an additional AST case. Its result has at most two or 256 paths,
respectively. `headMask` gives the corresponding two-bit or 256-bit child mask.
Iteration binds a one-digit path, and groups **all** tails for that digit before
executing its body. Epsilon has no head and contributes no iteration group.
Fold, closure, fixpoint, range, call, and grounded semantics match the existing
symbol reference, including its stationary positive self-call rule.

## Translations

`RadixTranslations` exposes the original seven directions and a length-prefixed string translation:

| Entry point | Encoding |
| --- | --- |
| `symbolsToBits` | Fibonacci code of the nonnegative intern ID plus one |
| `stringsToBytes` | zero-terminated string codewords |
| `lengthPrefixedStringsToBytes` | one byte of payload length, followed by 0–255 raw CESU-8 bytes |
| `internedSymbolsToBytes` | four bytes per intern ID, most significant first |
| `bitsToBytes` | one byte, 0 or 1, per bit |
| `bytesToBits` | eight bits per byte, most significant first |
| `bitsToSymbols` | the symbols `false` and `true` |
| `bytesToSymbols` | the symbols `000` through `255` |

The final two return a `SymbolProgram` containing the **existing** symbol AST.
Their programs use only the two or 256 assigned symbols, including constants
inside generated helper routines. All entry points accept an optional map of
routine definitions and return the entry expression together with its complete
translated module. Pass the whole routine map when translating again.

```scala
val source = Space.TailsUnion(Space.Literal(
  SpaceValue(Set(PathValue(List(PathItem("alice"), PathItem("bob")))))))
val translated = RadixTranslations.symbolsToBits(source)
assert(translated.decode(translated.program.eval()) == eval(source))

val byteProgram = RadixTranslations.bitsToBytes(
  translated.program.entry, translated.program.routines)
val symbolProgram = RadixTranslations.bytesToSymbols(
  byteProgram.entry, byteProgram.routines)
val result = symbolProgram.eval()
```

For an open program, pass encoded path/space argument maps to `program.eval`.
Intern input symbols **before compiling** an interned translation:

```scala
val input = SpaceMention("input")
val data = SpaceValue(Set(PathValue(List(PathItem("alice")))))
val interner = PathItemInterner()
data.paths.foreach(interner.encode)
val compiled = RadixTranslations.symbolsToBits(
  Space.TailsUnion(Space.Mention(input)), interner = interner)
val output = compiled.program.eval(spaceArgs = Map(input -> compiled.encode(data)))
assert(compiled.decode(output) == SpaceValue(Set(PathValue(Nil))))
```

Constants throughout the supplied module are interned automatically. The
returned codec snapshots that alphabet, preserving existing IDs. Later interner
mutations do not change the translation. Encoding an unknown symbol fails
explicitly; include all possible input and grounded-result symbols in the
interner before compiling. String encoding needs no alphabet snapshot.

String codewords end in byte 0. Payloads use UTF-8 per UTF-16 code unit
(CESU-8); NUL and SOH are escaped as `1,1` and `1,2`. This preserves Java string
ordering and handles every JVM string, including embedded NULs, empty symbols,
supplementary characters, and lone surrogates. Interior payload bytes are never
0. Ordinary BMP text apart from NUL/SOH has its usual UTF-8 bytes. This is an
explicit reversible string encoding, rather than raw UTF-8 for all strings.

Length-prefixed strings use raw CESU-8 payload bytes without NUL/SOH escaping.
The empty symbol is `[0]`; `"a"` is `[1,97]`; a NUL symbol is `[1,0]`.
The maximum is 255 **encoded bytes**, rather than 255 characters. Encoding a
longer symbol, or decoding a truncated/noncanonical codeword, fails explicitly.
`PathEncodings.lengthPrefixed` exposes the codec independently.

Length order differs from string order. The translation selects ordered heads
by payload and then restores the corresponding length byte. Iteration scans the
length counter using an ordinary predecessor relation and recursive functions;
tails union dispatches to ordinary byte tails operations. No new language case,
encoding table or symbol-boundary metadata is stored at runtime. The PathMap
compiler proves these ordinary programs before replacing their scans with native
bulk projections and streaming fixed-depth zipper selection. See the
[OEIS reproduction](OEIS_BENCHMARK.md) for a numeric use of the same framing.

## Lowering and bounds

Set algebra, concatenation, wrapping, unwrapping, calls, and fixpoints translate
structurally. One-digit injections also retain the native tail, closure, and
fold operators. Other iterations become routines that consume a complete
codeword before invoking the translated body with its encoded head and entire
tail group. Fixed-width codes become nested digit iterations; in particular a
byte iteration becomes eight bit iterations.

The Fibonacci scanner recognizes the terminal `11`. Its finite states include
the consumed depth, and compilation caps them at the longest codeword in the
interner snapshot. Scanner path parameters have exact consumed lengths.
Translated source references have exact multiplied lengths for fixed-width
codes, and `maxLengthHint` bounds for variable-width codes. `PathRef.bounded`
expresses this distinction without pretending a bound is an exact length.
`ResultPathLength` respects these upper bounds. Thus the interner's bit bound
becomes a real finite bound on scanning and unrolling.

Prefix/suffix/tails closures stop at source symbol boundaries. Tails
intersection uses the union of tails minus the tails absent from any group.
Fibonacci and intern IDs need not preserve string order: their ordered ranges
and folds select heads in source order using conditional codeword constants
compiled from the alphabet. Head selection shares one evaluated head set across codebook decisions. Last-path
selection tests the local frontier before descending, and range bounds share a
single evaluated source argument through a routine parameter. This avoids
exponential repeated descent and repeated source evaluation.
Range slicing preserves `RangeBounds.normalize`,
including negative and extreme integer endpoints. Repeated drops use shared
power-of-two routines, avoiding enormous generated ASTs for large endpoints.
Fold threads its path accumulator through ordinary function parameters. A
singleton path is reified into a path parameter by digit iteration and recursion.

Generated modules contain only existing operations and explicit routines with
lexical captures passed as arguments. No boundary node, host decoder operation,
runtime symbol-order table, side metadata, or extra alphabet symbol is added.
The compiler's framing automaton and codebook are discarded. Original grounded
callbacks remain explicit boundaries; their existing inputs/outputs are adapted
by the codec. A callback-free source yields callback-free target code.

`ByteZipper` is a small immutable observation/movement reference following
`~/PathMap/lean/PathMapModel/Zipper.lean`: a fixed root, a relative focus path,
off-trie descent, bounded ascent reporting the distance, terminal membership,
and a child mask. It uses the same byte-array set backend.

## Validation

`RadixLanguageTest` differentially checks every expression case through every
translation, open calls, mutual recursion, lexical capture, epsilon/empty
spaces, random path sets, all 256 bytes, source ordering, signed range bounds,
codec failures/round trips, callback-free output, length annotations, and zipper
movement. Run it using the repository's Scala CLI test command with
`--test-only morkl.RadixLanguageTest --require-tests`.


To run every Scala test with translation checks on all distinct reference
expression/input frames observed throughout the suite:

```bash
python3 tools/test_radix_translations.py --output /tmp/zippy-radix-full-suite
```

The opt-in audit captures the actual path/space arguments and all symbols
observed during reference execution, including grounded intermediate results. Supplied free bindings in unexecuted
branches are retained as well.
It follows reachable routine definitions and deduplicates equivalent expression
and input frames. After the assertions finish, it checks each case through all
eight directions; composed translations are decoded back to the original
symbol result. Replay happens outside test timing and cost-counter scopes.
Source evaluations that intentionally throw have no successful result to compare
and are counted separately. Tests that do not evaluate a source expression
(e.g. native trie data-structure and proof-format tests) still run normally.

The runner saves the complete test log, per-direction JSONL checks, and a JSON
summary. Its exit status fails if the suite fails, a translation differs or
throws, any replay remains incomplete, or the report is missing. `--workers`
controls replay parallelism; `--heap` controls JVM memory. `--test-only` is
available for diagnosing a single suite; omit it for the full run.

Full-suite results are recorded in [Full-suite translation validation](RADIX_TRANSLATION_TEST_REPORT.md).
