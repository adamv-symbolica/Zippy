- Lowering a one-letter op (TailsIntersection) per head through a recursive "peel the least head" loop
  turns O(width) depth into O(#heads) stack depth. Push the per-letter combination into the target's
  own TailsIntersection, using an incomplete-codeword junk path to keep empty groups present.
- Assigning sorted symbols to lexicographically sorted codewords makes any prefix-free code
  order-preserving, which removes the need for runtime recoding before Range/Fold.
- Conditionals built from `Composition(guard, body)` are lazy only by evaluator accident. A guard is
  lazy by semantics when written as an Iteration over a wrapped guard space.
- Probe translated programs at n ∈ {2, 25, 200}, not only at test sizes: the stack overflow appeared
  at n=25.
