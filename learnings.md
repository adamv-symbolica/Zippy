Techniques worth retaining from this attempt and review:

- A generic atom-parameterized AST and one representation-parameterized reference evaluator keep the bit and byte constructor sets aligned. Preserve explicit, exhaustive converters to the existing symbol AST.
- A leading delimiter bit makes a `Set[BigInt]` reference backend distinguish epsilon and arbitrary leading zeroes without an external length table. Native shift/mask prefix operations are a good fit.
- Compile codeword recognition into ordinary `Iteration` and lambda-lifted routines. Fixed-width byte expansion can use eight nested bit iterations without adding language features.
- The finite-set identity `intersection(groups) = U minus union(U minus group)` expresses tails intersection using the existing algebra. Its evaluate-once helper pattern should also protect decoder input evaluation.
- Differential tests across all encoding chains are valuable, but must include source-domain boundaries, lexical scope, actual annotation conformance, and compile-once inputs. Small ASCII corpora alone can make invalid codec assumptions look universal.
- Count source evaluations as well as measuring elapsed time. A single instrumented input established exact exponential duplication; a separate pure-literal benchmark confirmed that the same defect affects ordinary programs.
- Treat exact length hints as contracts. Inspect generated routine formals and actual argument lengths; result equality alone cannot detect false hints that the interpreter ignores.
- Test immutable array wrappers with hashed sets larger than four elements. Mutating a stored key can leave iteration and membership disagreeing even when singleton examples appear sound.

Runnable review probes and observations are preserved in `/home/ubuntu/polemic-notes/zippy-coded-review-2026-10-29/`.
