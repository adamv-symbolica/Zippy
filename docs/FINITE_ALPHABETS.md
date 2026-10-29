# Zippy_bits and Zippy_bytes

`morkl.bits` and `morkl.bytes` instantiate the same syntax in
`morkl.finite.Language` with `Boolean` and unsigned `Int` (0–255) alphabets.
Every symbol-language path and space constructor is present, including calls,
ordered folds, fixpoints, grounded boundaries, ranges, and all three closures.
`Space.Head` is derived with iteration in all instances. Bit heads have at most
two members; byte heads have at most 256, regardless of subspace size.

The initial runtimes are deliberately reference implementations:

* `BitSpace` contains a `Set[BigInt]`. A leading sentinel 1 encodes length:
  epsilon is 1, the path 0 is 2, and 00 is 4. Concatenation, prefix checks,
  taking and dropping bits operate on the integers directly.
* `ByteSpace` contains a `Set[BytePath]`. Each `BytePath` owns an immutable,
  content-equal byte array. External arrays are copied on both entry and exit;
  ordering is unsigned. `headMask` is a 256-bit `BigInt`.
* `ByteZipper` is a snapshot cursor with root-bounded ascent, off-trie descent,
  terminal observation and subtree extraction, following the contracts in
  `~/PathMap/lean/PathMapModel/Zipper.lean`.

`Evaluator` executes every constructor against these native representations.
It trampolines language recursion, including iteration and function calls.
Neither backend stores symbol tags, codeword lengths, boundary markers outside
the path, nor a separate symbol trie.

## Translation API

```scala
import morkl.*
import morkl.finite.ZippyTranslations

val source = Space.TailsUnion(Space.Literal(
  SpaceValue(Set(Syntax.parse("parent.ann.bob")))))
val bits = ZippyTranslations.symbolsToBits(source)
val bitResult: BitSpace = bits.run()
assert(bits.runDecoded() == eval(source))

val bytes = ZippyTranslations.stringsToBytes(source)
val byteResult: ByteSpace = bytes.run()
val fixed = ZippyTranslations.internedStringsToBytes(source)
```

These methods accept path-input maps, space-input maps, and a map of routines.
`Symbols.reachable(expression, routineContext)` extracts the transitive routine
map from an existing `PartialFunction[RoutinePtr, Routine]`, retaining self and
mutual recursion. `Symbols.fromSpace` / `toSpace` and their path/routine variants
bridge the existing symbol syntax losslessly.

The other four entry points take bit or byte syntax and return a `Translation`:

| Entry point | Encoding |
| --- | --- |
| `bitsToBytes` | false → 0, true → 1 |
| `bytesToBits` | eight bits per byte, most significant first |
| `bitsToSymbols` | exactly `__zippy_bit_0` and `__zippy_bit_1` |
| `bytesToSymbols` | exactly `__zippy_byte_000` through `__zippy_byte_255` |

A `Translation` exposes `expression`, `routines`, `code`, and `scanBounds`.
Use `code.encode` on input paths, evaluate `expression` with `routines`, and
use `code.decode` on output paths. Translations compose by passing both the
expression and its routine map to the next translator. Symbol injections can
run through the existing symbol evaluator using `Symbols.toSpace` and
`Symbols.toRoutine`.

## Symbol encodings and physical bounds

Fibonacci coding encodes intern ID + 1 in canonical Zeckendorf form, least
significant Fibonacci digit first, followed by a 1. The first `11` ends a
codeword; ID 0 is `11`, ID 1 is `011`. Interned byte coding uses four bytes per
ID, most significant first. Both codecs snapshot the declared interner.
The convenience API registers literals, all supplied input paths, and all
supplied routine constants before taking that snapshot. A grounded callback
that creates other symbols must declare them with `extraSymbols`. Direct
`Codes.internedBits(interner)` and `internedBytes(interner)` support a caller's
existing `PathItemInterner`; register its alphabet before creating the code.
Unknown symbols fail explicitly instead of silently invalidating depth bounds
or lexical ordering.

String coding terminates each symbol with byte zero. Ordinary nonzero ASCII
is unchanged. To include every Java String, NUL and SOH use `01 01` and `01 02`;
other UTF-16 code units use UTF-8, including CESU-8 for surrogate pairs. Thus
payloads contain no zero, empty symbols are representable, unpaired surrogates
round-trip, and byte order agrees with Java String order. This deliberately
specifies the Unicode representation rather than conflating Java UTF-16 order
with Unicode scalar order.

`Code.lengthBounds(symbolEstimate)` converts existing `PathLengthEstimate`
annotations into physical unit bounds. Fixed-width encodings multiply both
bounds by four or eight; Fibonacci coding uses its minimum and maximum
codeword sizes. A terminated string has no finite maximum without a separate
string-length bound. Exact path-reference hints are multiplied only for
fixed-width codes; variable-width translation drops misleading exact hints.

Iteration becomes calls to ordinary target routines that accumulate a path
prefix while dispatching with target iteration. Fibonacci recognizers have
two states; terminated strings recognize zero; fixed encodings consume four
or eight units. A known maximum codeword length generates a finite set of
routines indexed by depth, with exact prefix-length annotations. This is real
unrolling: those scanner calls have no recursive cycle. `scanBounds` reports
the bound and whether it was unrolled. `Translator(code, unrollLimit = 0)`
retains recursive scanners for comparison.

## Preserving the algebra

Set operations, composition, wrapping and unwrapping commute with the
prefix-free encodings. Operations that observe a symbol boundary are lowered:

* iteration binds a whole encoded source symbol and its complete tail group;
* tails intersection combines whole-symbol groups, including the no-head case;
* prefix/suffix/tails closures recurse only across whole codewords;
* folds consume logical heads in source order, passing the path accumulator to
  the next ordinary call, including ignored and shadowed binder cases.

Fibonacci order and intern-ID order need not match symbol lexical order. Around
`Range`, generated routines convert whole paths to fixed-width ordinal keys,
apply the existing target range operation, then convert them back. The mapping
is expressed with ordinary path-set literals, unwrap, composition and calls.
The same adapter selects fold heads. No ordering primitive or hidden runtime
annotation is introduced. String and injection codes already preserve order.
All generated programs use only the existing constructors. Existing grounded
callbacks are adapted at their explicit host boundaries; pure input programs
produce pure target programs.

## Verification

`FiniteAlphabetTest` covers every constructor through the encoding routes,
actual execution of symbol injections, codec round trips (including every
UTF-16 code unit), unsigned byte ordering, empty paths/symbols, extreme range
bounds, folded accumulators, lexical order differing from interning order,
shadowed binders, mutual recursion, and 1,000 existing dependent corpus cases.

To audit all seven routes against every reference evaluation made by every
registered test body, run:

```bash
env _JAVA_OPTIONS='-Xmx32g -Xss64m -Dmorkl.translationAudit=true' \
scala-cli run src/main/scala src/test/scala --test \
  --main-class morkl.translationAuditSuite --server=false \
  --scala 3.8.1 --source 3.3 \
  --dependency org.scalameta::munit:1.2.1 \
  --dependency org.scala-lang.modules::scala-collection-contrib:0.3.0
```

The runner discovers the compiled MUnit suites and executes their registered
bodies, preserving hooks and ignore tags. It avoids imposing normal benchmark
timeouts on seven additional interpreters. The ordinary `scala-cli test`
command remains the normal MUnit gate. Auditing is disabled by default and
reports successful source evaluations and route comparisons on exit.
Grounded results are recorded during the source run and replayed during the
audit, so tracing and other host side effects are not repeated. Callback
adapters themselves still execute. This recording is test instrumentation,
never part of a translated language or backend.
