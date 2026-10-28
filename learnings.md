# Techniques worth retaining

- Keep one alphabet-parameterized AST/evaluator whose constructor labels are checked against the symbol language with Scala `Mirror`. This is a useful drift check; complement it with semantic binder/annotation tests.
- Store a bit path as a positive BigInt with a leading-one sentinel. Epsilon, leading zeros, and path concatenation then have native representations without a separate runtime length field. Preserve lexicographic rather than numeric ordering.
- Use a content-equal byte-array wrapper and unsigned lexicographic order. Make array ownership airtight: copying factories alone do not make a public retaining constructor safe.
- Recognize encoded symbol boundaries with a small automaton and emit ordinary target-language iteration/functions. Specialize finite decoder states by sound code-length bounds, but retain recursion when strings have no finite static bound.
- Use an existing iteration over a wrapped guard to make conditional evaluation lazy, including epsilon-only guards, without adding a conditional opcode.
- Separate the two mathematical obligations: prefix-free codes preserve prefix/monoid operations; source ordering must also be preserved for Range and Fold. Do not force every useful stable-ID codec to be order-preserving merely to simplify the latter two operators.
- Differentially compose all seven translations and compare with the existing symbol evaluator. Build compilation facts from the source and declared inputs, not from expected outputs.
- Measure generated execution with a delegating representation that counts traversals and copied payload. Single-head/long-codeword inputs expose repeated accumulator traversal that ordinary result-size checks and broad random corpora miss.
- Test immutable hash keys in a hash-backed set with more than four entries: small-set specializations can conceal mutation defects.
