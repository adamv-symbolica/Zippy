# Zippy over bits and bytes

`Zippy_bits.Space` / `BitSpace` and `Zippy_bytes.Space` / `ByteSpace` have exactly
`Space`'s constructors. Their path expressions likewise have exactly `Path`'s
five constructors. `AlphabetSpace[P]` and `AlphabetPath[P]` are the shared syntax;
the public constructors specialize it to the native path representation. Match
syntax using `AlphabetSpace` / `AlphabetPath` constructors.

`Head` is derived from `Iteration`, just as `Syntax.head` is in the symbol
language. Native iteration binds one bit or one byte, rather than an encoded
symbol. Consequently bit heads have at most 2 results, and byte heads at most
256. `BitSpace.headMask` and `ByteSpace.headMask` return their masks.
`AlphabetAnalysis.resultSize` retains these finite bounds for an open `Head`;
`AlphabetAnalysis.pathLengths` reuses the existing compositional length analysis.

## Reference values and execution

A bit path is a positive `BigInt`: the highest one is a sentinel, and all lower
bits are the path in traversal order. Epsilon is `1`, the path `0` is `2`, and
`01` is `5`. The sentinel distinguishes every sequence, including leading zeroes,
without a separate length field. `BitPaths.fromDigits` / `digits` are convenient
constructors and observers. Numeric BigInt order is *not* path order;
`BitPaths.compare` implements lexicographic path order.

A `BytePath` owns an immutable byte array with content equality and hashing.
Construction and `toArray` copy; `unsigned` observes bytes as integers 0–255.
`BytePaths` implements unsigned lexicographic order. Reference spaces are
`Set[BigInt]` and `Set[BytePath]`, respectively. The evaluator operates directly
on those sets; it does not dispatch back to the symbol evaluator.

```scala
import morkl.*

val input = Set(BytePath(0, 255), BytePath(1), BytePath())
val heads = ByteSpace.eval(ByteSpace.Head(ByteSpace.Literal(input)))
assert(heads == Set(BytePath(0), BytePath(1)))

val program = AlphabetProgram(ByteSpace.TailsClosure(ByteSpace.Literal(input)),
  Map.empty, BytePaths)
val bits = AlphabetTranslation(program, AlphabetEncodings.bytesToBits)
assert(bits.eval().map(AlphabetEncodings.bytesToBits.decode) == program.eval())
```

`AlphabetProgram.eval` accepts native path and space environments and a program
carries its ordinary named functions. `AlphabetRoutine` has the same fields as
`Routine`. Ordered folds, range normalization, ignored `_` binders, and the
symbol evaluator's stable self-union recursion convention are preserved.

`ByteZipper` is an optional read-only projection of
`~/PathMap/lean/PathMapModel/Zipper.lean`. It retains root confinement, arbitrary
off-trie focus, terminals at the focus, byte masks, and unsigned sibling order.
It is a view of the same byte-array set, not another evaluator backend. PathMap's
bare/dangling locations are outside path-set semantics.

## Encodings and translations

`AlphabetTranslation(program, encoding)` emits another `AlphabetProgram`.
`AlphabetTranslation.symbols(expression, encoding, routines)` accepts the
existing symbol syntax directly. `SymbolAlphabet.toSpace` / `toRoutine` convert
symbol injection results back to the existing evaluator/compiler APIs.

| `AlphabetEncodings` member | Encoding of one source edge |
| --- | --- |
| `internedBits(interner)` | Fibonacci code of interned ID + 1, least Fibonacci weight first, terminated by `11` |
| `stringBytes` | String bytes followed by `00`; details below |
| `internedBytes(interner)` | Four bytes of the nonnegative 32-bit ID, most significant byte first |
| `bitsToBytes` | `0` → byte 0, `1` → byte 1 |
| `bytesToBits` | Eight bits, most significant first |
| `bitsToSymbols` | The symbols `false` and `true` |
| `bytesToSymbols` | Exactly `byte000` through `byte255` |

Interned codecs snapshot the interner. Intern all symbols that appear in program
constants, inputs, and callback outputs **before constructing the codec**. Unknown
symbols and incomplete encodings are rejected at the I/O boundary. Snapshotting
provides an actual maximum code width and a fixed source ordering for `Range`
and `Fold`; it also avoids a dependence on later interner mutations.

```scala
val interner = new PathItemInterner
val sourceValue = SpaceValue(Set(PathValue(List(PathItem("hello")))))
sourceValue.paths.foreach(interner.encode)
val codec = AlphabetEncodings.internedBits(interner)
val lowered = AlphabetTranslation.symbols(Space.Literal(sourceValue), codec)
assert(lowered.eval().map(codec.decode) == sourceValue.paths)
```

`stringBytes` supports every JVM string, including the empty symbol, embedded
NULs, and unpaired surrogates. UTF-16 code units use CESU-8; NUL is escaped to
`01 01` and U+0001 to `01 02`, so `00` occurs only as the symbol terminator.
Other ASCII bytes are unchanged. This explicit encoding preserves Java
`String.compareTo` order, unlike ordinary UTF-8 for some supplementary characters.
It allows direct target ranges while retaining the full symbol language's
ordering semantics. It is an order-preserving terminated string encoding, not
an interchange UTF-8 format.

## Generated functions and boundaries

Flattening values alone is insufficient. Iteration, tails, and closures must
observe *source symbol* boundaries, and ordered operations must retain source
order. The compiler handles these cases using the original operations:

- Iteration becomes target digit iteration plus decoder functions. The encoded
  symbol prefix is an ordinary path argument; the remaining subtree is an
  ordinary space argument. Functions capture outer variables through explicit
  parameters, including captures needed across mutual recursion.
- Fixed-width scanners specialize to acyclic functions at each depth: byte to
  bit decoding consumes exactly eight edges. Interned Fibonacci decoding uses
  the snapshot's maximum code width to do the same specialization, with its
  previous-bit state represented by function choice.
- Tails and closures recurse at decoded symbol boundaries. They never emit
  partial encoded symbols, and retain the original distinctions around epsilon.
- Order-preserving encodings use target `Range` directly. Interned encodings
  recode paths to source-order fixed-width ranks using constants, `Unwrap`,
  `Wrap`, and recursive functions; range results are recoded back. `Fold` consumes
  source heads in that order and carries its path accumulator as an argument.
- `PathRef.known(n)` becomes an exact encoded length for fixed-width encodings.
  Variable-width encodings use `PathRef.bounded(max)`; finite scanner parameters
  have exact per-depth lengths. Bounds remain annotations and never affect
  reference identity or occur alongside runtime values.

No generated decoder, boundary, rank, or continuation operation is added to the
language. Generated programs contain no runtime encoding tags or symbol tables.
Pure inputs produce no grounded callbacks. Existing grounded operations remain
explicit host boundaries: their arguments and results are decoded/encoded at
that boundary, using their original operation case.

The reference compiler prioritizes correctness. Interned ordered operations emit
code proportional to the snapshot alphabet, and recursive reference execution
uses the JVM stack. These implementations establish semantics rather than a
production packed-trie backend.

## Verification

`FiniteAlphabetTest` compares every constructor across all seven translations,
checks malformed encodings and unsigned ordering, verifies decoder depth and
closed functions, and exercises 120 reproducible randomized programs plus
composed translations. `AlphabetExamplesTest` runs the actual Aunt query,
semi-naive recursive Datalog, and pure sliding-puzzle moves and full 2x2
reachability on the new
backends. For these suites, compile the complete test tree and select:

```bash
env _JAVA_OPTIONS='-Xmx4g -Xss32m' \
scala-cli test src/main/scala src/test/scala \
  --test-only 'morkl.*Alphabet*Test' --server=false \
  --scala 3.8.1 --source 3.3 \
  --dependency org.scalameta::munit:1.2.1 \
  --dependency org.scala-lang.modules::scala-collection-contrib:0.3.0
```
