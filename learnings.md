Techniques worth retaining from reviewing `.review` at `4f8fc08`:

- Keep one atom-parameterized AST and shared evaluator, with exhaustive conversions to the actual symbol enum; check constructor parity against this checkout.
- A leading sentinel bit gives BigInt words reversible length and leading zeroes without external symbol-boundary metadata. Byte keys need private immutable storage, defensive public construction/extraction, content hashing and unsigned order.
- Use stable literal interner IDs for interoperability. Name sorted dictionaries separately. Supply finite source-order knowledge as an explicit frozen projection compiled into ordinary routines, and enforce its admissibility before accepting host inputs.
- CESU-8 per UTF-16 code unit, escaped NUL/SOH and NUL termination give a total order-preserving Java-string code, including lone surrogates.
- Lift decoder bodies and evaluate arbitrary decoder sources once. Build depth-indexed state DAGs from maximum code length, and compute the Fibonacci maximum directly from the bound rather than enumerating the ID space.
- Reserve source names before generating helpers. Preserve ignored local binders, ordinary `_` formals, head-over-accumulator shadowing, and exact length annotation semantics.
- Filtering an Iteration's input provides portable branch control. Composition is an algebraic product, not a generally lazy conditional.
- Trampolining solves JVM-stack growth, not allocation or retained heap. Test long single words with a fixed heap; count copied bytes and inspect pending continuations as well as stack depth.
- Use a bounded-output changing Fold (`body={a.h}`, `update=h`) to expose avoidable quadratic sequencing. A fold that emits every growing accumulator prefix already has quadratic output and is a poor complexity witness.
- Minimum scans are still O(n). Extract heads once, keep an ordered persistent head index and reuse a prefix index over the fixed original input. Apply the same algorithm to ordered and projected codecs.
- AST dispatch counts can stay linear while bulk set operations take quadratic time. Compare the real compiler/evaluator against original symbol `eval`, and measure comparisons, allocation and peak live memory too.
- Ignoring a binder does not erase evaluation of its initializer/update. Restrict dead-code/order-free rewrites to proven purity/totality conditions, or preserve grounded callback sequencing.
- Give caches explicit program lifetimes. Strong identity caches on a reusable public evaluator otherwise retain every discarded entry AST and index.
- Keep probes and raw output outside the product tree. This review's sources/logs are in `/home/ubuntu/polemic-notes/zippy-coded-review-r4/`.
