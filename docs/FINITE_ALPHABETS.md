# Zippy over bits and bytes

`Zippy_bits` and `Zippy_bytes` share the exact path and space constructors of
Zippy_symbols through the typed `WordPath[P]` and `WordSpace[P]` definitions.
Their public aliases are `BitPath`, `BitSpace`, `BytePath`, and `ByteSpace`.
The namespace objects also expose `Path`, `Space`, `PathValue`, `SpaceValue`,
`Routine`, and `eval`. `Head` is derived from `Iteration` and `Singleton`, as
it is in the symbol language. No encoding-specific language constructors exist.

The reference backends are:

- bits: `Set[BigInt]`, with one leading delimiter bit retaining the word's
  length and leading zeroes. Epsilon is `1`, bit `0` is `2`, bit `1` is `3`,
  and word `00` is `4`. The delimiter is representation, not a path digit.
- bytes: `Set[BytePathValue]`, whose elements contain immutable byte arrays
  with content equality. Array construction and extraction copy their input.
  Byte order is unsigned, from 0 through 255.

Neither backend retains symbol boundaries, decoding tables, or other metadata
on spaces or paths. The optional `ByteZipper` is a read-only cursor over the
same byte-set backend. Its root, relative focus, off-trie movement, child mask,
`makeMap`, and `focusNode` follow the vocabulary of
`~/PathMap/lean/PathMapModel/Basic.lean` and `Zipper.lean`.

`Iteration` binds a one-bit or one-byte path and its grouped suffix space.
Epsilon has no iteration branch. `Fold` visits groups in unsigned digit order;
`Range` uses lexicographic path order with prefixes first. Closure and
fixpoint epsilon behavior matches the symbol reference evaluator.
`Zippy_bits.headMask` is a two-bit integer and `Zippy_bytes.headMask` is a
256-bit integer. The maximum head cardinalities are always 2 and 256.

## Translations

`ZippyTranslations` accepts the existing symbol AST, or typed bit/byte ASTs:

| Entry point | Encoding |
| --- | --- |
| `symbolsToBits` | Fibonacci code of intern ID + 1, low weight first, `11` terminator |
| `stringsToBytes` | UTF-8 string symbols terminated by NUL |
| `internedStringsToBytes` | Four-byte, big-endian intern ID |
| `bitsToBytes` | One byte per bit: false to 0, true to 1 |
| `bytesToBits` | Eight bits per byte, most significant first |
| `bitsToSymbols` | Two distinct, configurable symbol names |
| `bytesToSymbols` | 256 distinct, configurable symbol names |

Symbol entry points return `(encoding, program)`. Bit/byte injections return
`WordProgram`, and injections into symbols return `(encoding, SymbolProgram)`.
The latter contains ordinary `Space` and `Routine` values and executes with the
existing symbol evaluator. Its literals use only the injection's two or 256
symbols. `WordEncodings` exposes the same codecs and the generic compiler for
composing translations, including translating generated helper routines.

For example:

```scala
import morkl.*

val source = Space.Literal(SpaceValue(Set(
  PathValue(List(PathItem("hello"), PathItem("world")))
)))
val (code, program) = ZippyTranslations.symbolsToBits(source)
val bits = program.eval()(using BitWords)
assert(code.decodeSpace(bits).paths == morkl.eval(source).paths)

val bytesProgram = WordEncodings.bitsToBytes.compile(program.entry, program.routines)
val bytes = bytesProgram.eval()(using ByteWords)
assert(WordEncodings.bitsToBytes.decodeSpace(bytes) == bits)
```

For open programs, pass encoded free path and space arguments to
`program.eval(paths = ..., spaces = ...)`. Pass the source routines as a map
when compiling. Self recursion, mutual recursion and closure capture use
ordinary target routines.

The intern-ID encodings close their alphabet at construction. Register all
open-input and callback-produced symbols in the supplied `PathItemInterner`
before compiling. The symbol entry points automatically register literals and
path constants in the entry and supplied routines. Free inputs are not
inspected by compilation. This closed alphabet provides both a finite maximum
codeword length and the source symbol ordering needed by `Range` and `Fold`.

The string-to-byte encoding accepts new symbols after compilation and needs
no closed dictionary. NUL and byte 1 inside UTF-8 payloads are escaped as
`1,1` and `1,2`, respectively; `0` therefore remains an unambiguous terminator,
even for empty symbols or symbols containing NUL. Ill-formed UTF-16 strings
(unpaired surrogates) and malformed/noncanonical encoded words are rejected.
The optional `maxSymbolBytes` argument to
`WordEncodings.stringSymbolsToBytes` bounds the escaped payload size,
excluding the terminator.

## Generated programs and bounds

Translation is syntax-to-syntax. Symbol iteration scans target digits until a
codeword boundary, then calls the translated body with the complete encoded
head and grouped suffix space. Fixed-width codewords use nested iterations:
byte-to-bit iteration is eight levels deep, and intern-ID byte iteration is
four levels deep. One-digit injections retain native operations directly.
Variable-length codewords use recursive scanners.

Symbol-level tails and closures traverse complete codewords. Applying the
native bit/byte closure directly to a flattened symbol space would expose
partial symbols, so the compiler generates routine traversal instead.
Ordered folds select and consume source head groups sequentially, carrying the
accumulator as an ordinary path parameter. Intern-ID ordering is repaired by
reversible generated recoders. String ordering uses a small byte transducer
to retain JVM UTF-16 lexical order across supplementary Unicode characters.
All of this uses existing set/path operations and routine abstraction.

`PathRef.lengthHint` remains an exact length. `maxLengthHint` and `bounded(n)`
express an upper bound without claiming exactness. A fixed-length encoding
multiplies exact lengths; Fibonacci encoding multiplies by the maximum
codeword length to obtain an upper bound. `WordPathLength.upper` reads those
facts. `unrollBounded = true` uses the encoded symbol bound to generate an
acyclic scanner graph. Unbounded string scanners remain recursive; providing
`maxSymbolBytes` also makes them eligible for unrolling. Annotations are
compile-time facts, not runtime metadata.

Grounded source callbacks remain explicit host boundaries and are adapted
through the codec. Pure source programs introduce no grounded callbacks.

## Validation

`FiniteAlphabetTest` compares reference results through all seven encodings,
including every constructor, seeded dependent programs, grouped iteration,
recursive routines, composition of translations, Unicode ordering, embedded
NUL, all 256 bytes, epsilon/leading zeroes, and bounded scanner unrolling.

```bash
env _JAVA_OPTIONS='-Xmx4g -Xss32m' \
scala-cli test src/main/scala src/test/scala --server=false \
  --scala 3.8.1 --source 3.3 \
  --dependency org.scalameta::munit:1.2.1 \
  --dependency org.scala-lang.modules::scala-collection-contrib:0.3.0 \
  --test-only morkl.FiniteAlphabetTest
```
