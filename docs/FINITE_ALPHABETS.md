# Zippy over bits and bytes

`WordPath[P]` and `WordSpace[P]` mirror the symbol language's constructors.
Public aliases are `BitPath`, `BitSpace`, `BytePath`, and `ByteSpace`; the
`Zippy_bits` and `Zippy_bytes` namespaces expose the corresponding evaluators.
All translations use these operations and ordinary routines. Pure programs
introduce no host callbacks or encoding-specific language operations.

Bits use `Set[BigInt]`: a leading one retains length and leading zeroes, so
ε = 1, bit 0 = 2, bit 1 = 3, and word 00 = 4. Bytes use immutable,
content-hashed `BytePathValue` arrays with defensive construction/extraction.
The set reference evaluator indexes ordered work with persistent sorted sets;
encoded values carry no symbol-boundary metadata. Exact and maximum reference
hints are distinct, and `eval(validateHints = true)` checks their actual values.

## Encodings and ordering

| Factory in `WordEncodings` | Contract |
| --- | --- |
| `internedSymbolsToBits` | Open, stable Fibonacci code of literal interner ID + 1 |
| `internedSymbolsToBytes` | Open, stable four big-endian bytes of literal interner ID |
| `stringSymbolsToBytes` | Open, total CESU-8 code units, escaped controls, NUL terminator |
| `sortedDictionaryBits` | Closed sorted symbol dictionary assigned a sorted Fibonacci code set |
| `sortedDictionaryBytes` | Closed sorted symbol dictionary assigned four-byte ranks |
| `bitsToBytes` | false → byte 0; true → byte 1 |
| `bytesToBits` | Eight bits per byte, most significant first |
| `bitsToSymbols` | Two distinct configurable symbol names |
| `bytesToSymbols` | 256 distinct configurable symbol names |

The literal interner codecs never reassign IDs. Encoded data can be shared
across compilations using the same interner, and a program compiled before a
symbol is interned accepts it later. Their maximum Fibonacci length is computed
from `Int.MaxValue` directly, without enumerating the ID range.

**Literal IDs cannot recover lexical order of arbitrary future names.**
`Range` and `Fold` therefore raise `UnsupportedEncodedOrdering` at compilation
for these two codecs. Use CESU-8 or an explicitly named sorted dictionary when
ordered operations are required. This is an explicit capability boundary;
there is no silent ordering change or fallback.

Sorted dictionaries preserve source order by construction. Register all input,
literal and callback-produced symbols before constructing one. A rejected
encode does not intern the unknown symbol or mutate the caller's interner.
Their assignments may change when constructing a new dictionary over an
expanded alphabet; they are not literal-ID encodings.

`ZippyTranslations.symbolsToBits` and `internedStringsToBytes` use literal IDs.
`sortedDictionaryToBits` and `sortedDictionaryToBytes` expose the closed
specializations. The facade registers syntactic constants first. All return
`(codec, WordProgram)`; pass encoded free argument maps to `program.eval`.
Injections into symbols return an ordinary symbol `Space`/`Routine` program.
Custom injection names with reversed lexical order use one finite recoder.

## Total Java-string byte image

The string codec is **CESU-8, not standard code-point UTF-8**: each UTF-16 code
unit uses its own canonical one-, two-, or three-byte encoding. Lone surrogates
are valid, and a surrogate pair occupies six bytes. NUL and SOH payload bytes
are escaped as `1,1` and `1,2`; a final zero terminates each symbol. Empty
symbols, embedded NUL, and arbitrary Java strings are distinct and reversible.
Malformed escapes, overlong encodings, truncated sequences and four-byte
code-point UTF-8 sequences are rejected as noncanonical.

This image sorts exactly like `String.compareTo`, including lone surrogates.
`Range` stays native; the old UTF-8/UTF-16 repair transducer has been deleted.
`maxSymbolBytes`, when supplied, bounds escaped payload bytes excluding the
terminator. The new CESU-8 image differs from previous releases for
supplementary characters; persisted old values must be re-encoded.

## Decoder and fold compilation

Each decoder has one lifted user body. Small closed Fibonacci alphabets unroll
the code trie and call that body at the leaves, so nested iterations do not
multiply copies of an inner program. Bounded larger scanners specialize a
(state, depth) DAG under a compiler-wide 512-state budget; excess decoders use
bounded recursive routines. Fixed-width scans expand to their known depth.
Unbounded strings use recursive scanners. `unrollBounded = false` disables
optional specialization. All dispatch filters input before `Iteration`, with
inactive continuations never entered on eager backends.

General `Fold` computes complete heads once, then sequences over their sorted
persistent ranges. The original input stays fixed, and tail groups use indexed
prefix lookup. It never sorts or recodes the remaining input at each step.
Closed Fibonacci heads can bind through balanced complete-word membership
instead of decoding the same word again. An invariant accumulator needs only
one symbol iteration. Arbitrary reused sources and aggregates, including those
for tails intersection, are evaluated once through routine arguments.

The reference interpreter trampolines recursion. `WordEvaluationStats` records
executed nodes/calls; `WordProgram.nodeCount` counts entry plus routine bodies.
The corpus gates AST growth and execution work, normalizing by encoding width
and input/output cells because native bulk operations visit data within one
syntax node. Raw per-program counts and coverage totals are recorded too.

## Byte-trie evaluator

`Zippy_bytes.evalZ(expression, paths, spaces, routines, ...)` returns `ByteTrie`.
This is a persistent 256-ary trie with terminal flags and a child bitmask.
Iteration groups use child subtries directly; union, intersection, subtraction,
prefix restriction, composition, wrap/unwrap, and rank ranges use trie algebra.
Structural path counts support rank slicing. No symbol tables or boundary
metadata are stored in the trie. `Head` observes at most the 256 mask bits.
Some closure projections enumerate paths in this reference implementation.

The old `ByteZipper` remains a compatibility cursor over a set. Byte-trie
focus is available through `ByteTrie.subtree`; `evalZ` is the execution backend.
The symbol backend's own zipper compiler still rejects arbitrary recursive
routines outside its union-saturating fragment; bounded scanner injections
are tested there, and both scanner forms are tested on its eager executor.

## Checks and measurements

`WordEncodingReviewTest` covers polynomial nesting, stable/open IDs, rejected
closed encodes, all UTF-16 code units, 10,000 random string comparisons, long
unbounded symbols, byte-trie masks, and the exact changing-accumulator fold
probe through 16,000 groups. Its timing gate requires ≤20× source time and a
growth exponent ≤1.3. The scale suite also exercises 20,000-path ranges.

`WordEncodingCorpusTest` compiles 1,000 repository-fuzzer programs once and runs
two frames each. All programs run through the sorted/CESU routes and their
injections/composition, and through the byte trie. Literal-ID routes either
execute or assert the named ordered-operation error; the acceptance matrix
requires both outcomes and accounts for all 1,000 programs, without silently
skipping unsupported cases. See [round-three results](FINITE_ALPHABET_REVIEW_R3.md).

```bash
env _JAVA_OPTIONS='-Xmx12g -Xss8m' \
scala-cli test src/main/scala src/test/scala --server=false \
  --scala 3.8.1 --source 3.3 \
  --dependency org.scalameta::munit:1.2.1 \
  --dependency org.scala-lang.modules::scala-collection-contrib:0.3.0 \
  --test-only 'morkl.WordEncoding*Test'
```
