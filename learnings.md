Techniques worth retaining:

- Share an atom-parameterized AST and representation-parameterized evaluator, and maintain exhaustive conversions to the actual source enum. Constructor parity should be checked against this checkout, not a different revision's language.
- A leading one bit preserves epsilon and arbitrary leading zeroes in a BigInt word without an external length table. Aligned integer-prefix comparisons avoid reconstructing boxed bit vectors.
- Filtering a decoder's input before Iteration makes control flow conditional in every executor; Composition is not a general conditional for recursive continuations.
- Pass an arbitrary decoder source as an evaluated-once routine argument. Reused aggregates such as the union of tails should be lifted the same way. Count source evaluations as well as timing pure programs.
- Reserve all source names before generating helper names. Preserve binding order, ignored local binders, and ordinary routine formals named `_`. Check exact path hints at actual bindings; variable-length maxima are not exact lengths.
- Generalized UTF-8 per UTF-16 code unit, with in-band NUL/escape handling, is a simple total order-preserving Java-string encoding. It is not standard code-point UTF-8; state the byte-image contract explicitly.
- Keep stable interner-ID codecs distinct from sorted dictionary specializations. Closed ordering knowledge can be compiled into ordinary target operations, but lexical order of arbitrary future symbol names cannot be recovered from their integer IDs alone.
- Bound computation should scale with the number of bits, not the numeric size of the admissible ID range. Monotone code lengths make the last admissible Fibonacci argument sufficient.
- Immutable content-hashed byte keys need private storage and defensive public construction/extraction. Test hashed sets larger than four elements.
- Bounded decoder recursion does not make the evaluator stack-safe for many source groups or long unbounded symbols. Test both cardinality and encoded head length, using the real compiler/evaluator and original source eval.
- A green corpus with an explicit `if ordered then skip actual-codecs` branch is not evidence for every requested translation. Assert a coverage matrix and connect it to the acceptance gate.

Runnable probes and raw output for this review: `/home/ubuntu/polemic-notes/zippy-coded-review-next/`.
