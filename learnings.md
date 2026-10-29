# Learnings

- **Order-preserving interning.** For prefix-free codes over a closed alphabet, give symbols to
  codewords in sorted order: sort the k smallest Fibonacci codewords lexicographically, or use sorted
  rank for fixed-width big-endian IDs. Concatenation then preserves both prefixes and path order, so
  `Range`/`Fold` translate natively and need no runtime recoding.
- **Dispatch by filtering, not by guarding.** `Iteration(Restriction(X, {c}), _, r, k)` enters `k`
  only if a branch exists. Guarding with `Composition(nonEmpty(test), body)` depends on one evaluator
  short-circuiting `Composition`, and evaluates wrong branches on any eager backend.
- **Sum-of-intersections identity.** ⋂ᵢ Xᵢ = U \ ⋃ᵢ (U \ Xᵢ) with U = ⋃ᵢ Xᵢ expresses
  `TailsIntersection` through `Iteration`. Lift U into a routine argument so it is computed once.
- **`Fold` without an accumulator-returning function:** repeatedly take the least non-empty path
  (`Range(·,1,2)`), bind its head, run the template, and recurse on `Raffination(S, {h})` with the
  updated accumulator.
- **Review technique:** green tests on ≤ 10-path inputs say nothing about a reference translation.
  A size sweep (n = 200/1000/4000) of each ordered operator against symbol `eval` exposed a quadratic
  blowup and a `StackOverflowError` within two minutes. Run experiments as a separate `scala-cli run`
  over `src/main/scala` plus a scratch file kept outside the product tree.
