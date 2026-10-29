Techniques worth retaining (from reviewing `.review` round 2):

- Use filter-based dispatch, `Iteration(Intersection({h}, {c}), _, _, k)`, instead of `Composition` guards. It is correct on eager backends, and it gives each branch only its own sub-space.
- Trampoline the reference evaluator with `TailCalls`, so that deep generated recursion (scanners, fold loops) cannot overflow the stack.
- Use one binder-aware analysis pass to supply both the free variables and every reserved name. Seed fresh-name generation with all names in the program and its routines.
- Lift `TailsIntersection` into an outer routine that evaluates the source once and passes both the source and its tails union as mentions to an inner routine.
- Order-preserving codes keep `Range` native, so assign codes in source order whenever the code is a closed dictionary. Name such dictionaries separately from the real interner-id codecs.
- Pair the `validateHints` mode in the evaluator (exact and maximum) with corpus runs. It catches false length annotations that result equality cannot.
- Measure *target program size* as well as time. Inlining a body per codeword compounds as k^depth with nested iterations, and fast compilation hides it because subtrees are shared.
- Scale tests must cover the general operator (for example, `Fold` with a changing accumulator), not only an optimized special case. Compare against the source `eval` time, and use growth exponents rather than absolute budgets.
- Encode strings as UTF-8 per UTF-16 code unit (CESU-8 style), with NUL/SOH escapes. This is total over lone surrogates and byte-ordered exactly like `String.compareTo`, with no order-repair transducer.
