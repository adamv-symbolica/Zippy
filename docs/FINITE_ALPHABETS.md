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
`~/PathMap/lean/PathMapModel/Basic.lean` and `Zipper.lean`. This cursor is **not
an evaluation backend**: it scans the underlying set for subtree and mask
queries, with cost proportional to the set size and prefix length. A byte-trie
`evalZ` backend is out of scope for this implementation.

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
| `symbolsToBits` | Lexically assigned Fibonacci codewords, low weight first, `11` terminator |
| `stringsToBytes` | UTF-8 string symbols terminated by NUL |
| `internedStringsToBytes` | Four-byte, big-endian symbol rank |
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
The four-byte IDs are ranks in sorted symbol order. Fibonacci encoding sorts
the same first `k` Fibonacci codewords lexicographically and assigns them to
the `k` sorted symbols. Both encodings preserve order by construction, so
`Range` stays native and performs no recoding. The code set and maximum
Fibonacci length are unchanged. This changes the byte/bit assignment from the
initial implementation; stored values must be re-encoded with the new codec.
An encoding owns its closed alphabet and cannot be extended in place.

The string-to-byte encoding accepts new symbols after compilation and needs
no closed dictionary. NUL and byte 1 inside UTF-8 payloads are escaped as
`1,1` and `1,2`, respectively; `0` therefore remains an unambiguous terminator,
even for empty symbols or symbols containing NUL. Ill-formed UTF-16 strings
(unpaired surrogates) and malformed/noncanonical encoded words are rejected.
The optional `maxSymbolBytes` argument to
`WordEncodings.stringSymbolsToBytes` bounds the escaped payload size,
excluding the terminator.

Escaping NUL and byte 1 is a deliberate extension of the simpler “reject NUL,
then append a zero byte” contract. It preserves empty symbols and embedded
NUL without collisions or metadata; ordinary UTF-8 text without those control
bytes keeps its usual byte image. Ordered operations also retain JVM UTF-16
string order. UTF-8 order differs for supplementary Unicode scalars, so an
ordinary generated byte transducer repairs that order around `Range` and
sequenced folds. This transducer does not change the codec's stored byte image.
Accepting native UTF-8 order instead would change source `Range`/`Fold` results.

## Generated programs and bounds

Translation is syntax-to-syntax. Symbol iteration scans target digits until a
codeword boundary, then calls the translated body with the complete encoded
head and grouped suffix space. Fixed-width codewords use nested iterations:
byte-to-bit iteration is eight levels deep, and intern-ID byte iteration is
four levels deep. One-digit injections retain native operations directly.
Variable-length codewords use recursive scanners or bounded inlined traversal.
Branches filter their input with `Iteration` before entering a continuation;
they do not depend on short-circuit evaluation of `Composition`.

Symbol-level tails and closures traverse complete codewords. Applying the
native bit/byte closure directly to a flattened symbol space would expose
partial symbols, so the compiler generates routine traversal instead.
A fold with an unchanged accumulator becomes a single symbol iteration, with
the initial accumulator evaluated once. Other folds select the least remaining
full path, decode only its head, and carry the accumulator as an ordinary path
parameter. The set reference backend still scans/removes remaining paths in
these general folds, so their worst-case work remains quadratic in the number
of head groups; the evaluator is stack-safe. `TailsIntersection` lifts its
source and union of tails into evaluated-once routine arguments. All of this
uses existing set/path operations and routine abstraction.

`PathRef.lengthHint` remains an exact length. `maxLengthHint` and `bounded(n)`
express an upper bound without claiming exactness. A fixed-length encoding
multiplies exact lengths; Fibonacci encoding multiplies by the maximum
codeword length to obtain an upper bound. `WordPathLength.upper` reads those
facts. Automatic unrolling is enabled by default. Fibonacci alphabets of at
most 32 symbols inline their actual code trie with constant complete heads,
without introducing scanner or body routines. Larger alphabets use the
recursive scanner, retaining its codeword bound. Terminated string encodings
with a bound of at most 32 encoded bytes (including the terminator) inline nested iterations and retain a single
body routine. Unbounded/longer strings stay recursive. Set
`unrollBounded = false` to disable these policies. The interned-byte entry
point exposes the same option; fixed-width scans always expand to their known
number of iterations. Annotations are compile-time facts, not runtime metadata.

`WordProgram.eval(validateHints = true)` checks exact and upper bounds on
actual dereferences and routine arguments. An optional `WordEvaluationStats`
counts evaluated nodes and calls. The reference evaluator uses a trampoline
so target recursion does not consume JVM stack per group or digit.

Grounded source callbacks remain explicit host boundaries and are adapted
through the codec. Pure source programs introduce no grounded callbacks.

## Validation

`FiniteAlphabetTest` compares reference results through all seven encodings,
including every constructor, seeded dependent programs, grouped iteration,
recursive routines, composition of translations, Unicode ordering, embedded
NUL, all 256 bytes, epsilon/leading zeroes, and bounded scanner unrolling.
`WordEncodingCorpusTest` adds 1,000 repository-fuzzer programs, each compiled
once and evaluated on two random inputs through all seven routes plus a
composition (16,000 target evaluations with hint checks). `WordEncodingScaleTest`
covers `Range` through 20,000 paths, unchanged-accumulator folds through 10,000
groups, changing accumulators, evaluate-once tails intersections, linear scanner
call counts, and hashed-set immutability. `WordEncodingBackendTest` checks
injected programs on `eval`, `execT` and `execZ`. The existing zipper compiler
supports the acyclic bounded scanners; general recursive scanners remain
outside its supported union-saturating fixed-point fragment.

See [review measurements and remaining limits](FINITE_ALPHABET_REVIEW.md).

```bash
env _JAVA_OPTIONS='-Xmx4g -Xss32m' \
scala-cli test src/main/scala src/test/scala --server=false \
  --scala 3.8.1 --source 3.3 \
  --dependency org.scalameta::munit:1.2.1 \
  --dependency org.scala-lang.modules::scala-collection-contrib:0.3.0 \
  --test-only 'morkl.WordEncoding*Test'
```
