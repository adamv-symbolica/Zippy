# Translation and performance notes

The backend uses PathMap's native persistent algebra and borrowed read zippers.
Iteration and fold tails stay borrowed until an owning result or routine boundary
requires a map. Head grafts a ByteMask in bulk, Range bounds are const generics,
and proven ordered selectors stream their heads without collecting path vectors.
Small acyclic path helpers, including GOL's `neigh`, inline into their callers;
[all three native symbol audits](../validation/pathmap-zipper/inline-audit.json)
confirm that `neigh` has no separate machine-code symbol.

Fixpoint convergence uses in-place `join_into` and its algebraic status. Standalone
equality first checks shared roots and native physical nodes, then compares read
zippers exactly if physical layouts differ. It constructs no union or difference
maps. The fallback memoizes shared-state pairs, including positions inside
compressed nodes. Opaque fixpoints retain exact comparison when an extra
stabilization step would change callback counts.

The [paired equality microbenchmarks](../validation/pathmap-zipper/acceptor-micro.csv)
show a tradeoff. At size 16,384 on the h4d, nanoseconds per comparison were:

| Operands | Structural + zipper | Previous two differences |
| --- | ---: | ---: |
| Shared map | 2 | 3 |
| Independently built equal maps | 2,381,519 | 1,144,859 |
| Unequal subset | 457 | 277,904 |
| Disjoint maps | 117 | 17 |

This removes result-map construction and greatly improves early mismatches in
subset cases. It does not make every equality case faster. Independently built
equal maps remain a performance target; shared inputs and fixpoint convergence
have cheaper paths than a complete language comparison.

Measured milliseconds per evaluation, including exact result counting:

| Workload | Interned bytes | String bytes | Fibonacci bytes |
| --- | ---: | ---: | ---: |
| Fold / incremental union, 32,768 heads | 169.917 | 79.115 | 187.398 |
| Composition, 576,000,000 paths, 1% case | 9.362 | 25.348 | 48.726 |
| Composition, 576,000,000 paths, 50% case | 9.297 | 26.718 | 48.076 |
| Composition, 576,000,000 paths, 90% case | 9.319 | 27.765 | 45.718 |

The underlying rows are in [construction.csv](../validation/pathmap-zipper/construction.csv)
and [algebra.csv](../validation/pathmap-zipper/algebra.csv). Fold outputs are checked
exactly; these huge composition cases compare exact cardinality against the
Scala trie. The [per-example tables](README.md) record every evaluator and
encoding separately, including constant folding and compiler/setup costs.
Benchmarks share the node with other validation work; small timing differences
are noise, and these measurements do not establish a universal speedup over the
previous backend.

Composition retains cached jumping catamorphisms. PathMap's `ProductZipper`
exposes intermediate factor values and only stitches at leaves, so it does not
implement Zippy concatenation for valued prefixes. The
[small reproducer](../../src/main/rust/pathmap/examples/product_semantics.rs)
shows the difference. Native union also repeats work for some distinct shared
operand pairs: joining binary languages of depths 16 and 15 takes about 1.975 ms,
while depths 20 and 19 take 32.353 ms despite O(depth) input states.
[Raw measurements](../validation/pathmap-zipper/dag-union.csv) and a
[reproducer](../../src/main/rust/pathmap/examples/dag_union.rs) are retained.
This upstream performance limit can dominate very large suffix closures; no
additional PathMap correctness bug was found by the model or upstream tests.

The compiler drops owned intermediates after their last graph use so PathMap
can exploit copy-on-write. Small generated units inline typed adapters; large
units use concrete kernels compiled at release level 3 to avoid paying for
unoptimized generic trie operations. Generated-code levels are recorded in the
manifest and tables. Size thresholds exclude concrete kernel namespace
qualification. Helper expansion is bounded to avoid explosive unrolling.
