# Translation and performance notes

The implementation uses PathMap's persistent native algebra and zippers. It
does not enumerate paths for union, intersection, subtraction, prefix
restriction, wrap, unwrap, or tails operations. Composition and closures use
cached catamorphisms to preserve shared suffixes. The independent model tests
include a compact language with 2^80 accepted paths.

Two optimizations mattered especially in the complete h4d run:

- Exact counting visits shared physical node boundaries inside unary stems.
  Jumping directly between forks can miss the shared suffix root and recount
  it for every left-hand path in a product.
- A proven translated fold orders its matching heads once. Continuations must
  remove exactly the selected prefix; static codewords must be prefix-free.
  This avoids repeatedly scanning the remaining head set or static codebook.

Final measured milliseconds per evaluation, including exact result counting:

| Workload | Interned bytes | String bytes | Fibonacci bytes |
| --- | --- | --- | --- |
| Fold / incremental union, 32,768 heads | 179.709 | 78.574 | 157.781 |
| Composition, 576,000,000 result paths | 8.002 | 22.115 | 43.261 |

The underlying rows are in [construction.csv](../validation/pathmap/construction.csv)
and [algebra.csv](../validation/pathmap/algebra.csv). The fold outputs are checked
exactly; the product compares exact cardinality against the Scala trie.
These are shared-load measurements, not isolated latency guarantees.

The compiler also drops owned intermediates after their last graph use so that
PathMap can exploit copy-on-write, removes singleton prefixes directly through
a write zipper, and emits byte loops over a four-word child mask. Large graph
regions become typed native functions to bound Rust compilation work;
persistent compiler binding maps avoid quadratic copying during emission.

The [per-example tables](README.md) distinguish precomputed literals from
executed graphs and retain graph compilation and native session setup costs.
Generated-code optimization levels are shown explicitly; native trie kernels
always use release level 3. The [backend description](../PATHMAP_BACKEND.md)
documents the conservative proofs and callback evaluation rules.
