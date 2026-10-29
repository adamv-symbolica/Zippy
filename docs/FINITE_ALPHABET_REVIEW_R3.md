# Round-three finite-alphabet review

This implements the requests in the round-two `feedback.md` and applies the
techniques retained in `learnings.md`. The referenced scratch probes were not
present in this checkout; the durable tests reproduce their inputs against
the production compiler, set reference, byte trie and symbol executors.

## Implemented changes

- Decoder leaves call one lifted body. At 32 symbols, depths 1–6 contain 734,
  1,496, 2,258, 3,020, 3,782 and 4,544 nodes, rather than multiplying by 32.
  Larger bounded decoders use state/depth DAGs with a global state budget.
- Changing-accumulator folds compute heads once and keep the original tail
  space fixed. Persistent sorted head ranges and indexed prefix lookup replace
  repeated scans of the full remaining input. A closed Fibonacci dictionary
  uses balanced complete-word membership to bind already-extracted heads.
- Strings use total per-UTF-16-code-unit CESU-8, including lone surrogates.
  NUL/SOH escapes and NUL termination remain in-band. Byte order is Java string
  order, so the UTF-8/UTF-16 recoder has been deleted.
- Literal stable interner-ID codecs are distinct from `sortedDictionaryBits`
  and `sortedDictionaryBytes`. Literal-ID codecs accept symbols interned after
  compilation. Closed dictionary failures perform no interning.
- Literal-ID ordered operations raise `UnsupportedEncodedOrdering`; source
  lexical order of arbitrary future names cannot be inferred from IDs alone.
  This limitation is explicit in the API documentation and the corpus matrix.
- `Zippy_bytes.evalZ` executes over a 256-ary `ByteTrie`, whose groups and Head
  use actual child subtries/masks and whose rank ranges use structural counts.
  The full 1,000-program × two-frame corpus compares it to the set reference.

The AST is unchanged: no language feature or runtime symbol-boundary metadata
was added. The reference set backend uses standard persistent sorted-set
indexes, while literals can retain hash membership for finite dispatch tables.
Native array and small-integer operations avoid unnecessary boxing, copying
and singleton group allocation. Ignored dispatch binders preserve outer `_`
references and avoid binding work.

## Acceptance evidence

RESULTS_PENDING

The corpus records raw entry/routine AST counts and executed-node counts per
program. Its outlier bounds normalize by encoding width and the input/output
cells visited by native bulk operations; a one-node source closure can still
visit many paths. The nesting regression is independent of runtime data and
would reject the previous exponential code expansion.

The literal-ID matrix is not a claim of ordered-codec support: every one of
its unsupported rows must raise the named error. The sorted dictionary and
CESU-8 rows exercise the ordered semantics on those same programs.

Run all finite-alphabet acceptance tests using the command in
[FINITE_ALPHABETS.md](FINITE_ALPHABETS.md). `WordEncodingReviewTest` contains
Probe B with source parity, median timing, the 20× budget and the growth gate;
`WordEncodingBenchmarks.scala` retains the other size/time sweeps. Historical
round-two results in `FINITE_ALPHABET_REVIEW.md` are superseded by this report.
