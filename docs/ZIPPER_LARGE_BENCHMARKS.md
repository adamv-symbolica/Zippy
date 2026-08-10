# Large Zipper Benchmarks

These rows are intentionally larger and more asymptotic than the mixed publication table. They compare direct `evalTrie` against direct `evalZ` after checking both produce the same `TrieSpace` result. The product rows are the key zipper stress tests: the source expression denotes an `n x n` product, but the consumer asks for one prefix or one exact path, so a zipper traversal should avoid materializing the full intermediate product.

| benchmark | size | result paths | evalTrie ms | evalZ ms | evalTrie / evalZ | note |
|---|---:|---:|---:|---:|---:|---|
| product intersected by one exact path | 2000 x 2000 product, 1 result path | 1 | 1.832 | 1.801 | 1.02 x | large intermediate product should not be materialized by zipper traversal |
| product intersected by one exact path | 10000 x 10000 product, 1 result path | 1 | 2.544 | 2.433 | 1.05 x | large intermediate product should not be materialized by zipper traversal |
| product intersected by one exact path | 30000 x 30000 product, 1 result path | 1 | 4.919 | 4.262 | 1.15 x | large intermediate product should not be materialized by zipper traversal |
| product restricted by one first-level prefix | 2000 x 2000 product, 2000 result paths | 2,000 | 0.624 | 0.533 | 1.17 x | large intermediate product should not be materialized by zipper traversal |
| product restricted by one first-level prefix | 10000 x 10000 product, 10000 result paths | 10,000 | 1.578 | 1.479 | 1.07 x | large intermediate product should not be materialized by zipper traversal |
| product restricted by one first-level prefix | 30000 x 30000 product, 30000 result paths | 30,000 | 17.306 | 8.813 | 1.96 x | large intermediate product should not be materialized by zipper traversal |
| aunt query over large generated family, one queried person | 9 generations x 80 people (2720 facts) | 0 | 0.121 | 0.335 | 0.36 x | large query-shaped dataset with small queried person set |
| aunt query over large generated family, one queried person | 11 generations x 160 people (6720 facts) | 0 | 0.175 | 0.236 | 0.74 x | large query-shaped dataset with small queried person set |
| semi-naive datalog over generated chain graph | 40 nodes, 79 edge facts | 820 | 31.520 | unsupported | n/a | evalZ unsupported: zipper transpile cannot lower recursive top-level self-union call step441187469; refuse the old evalTrie materialization fallback |
| semi-naive datalog over generated chain graph | 80 nodes, 159 edge facts | 3,240 | 110.226 | unsupported | n/a | evalZ unsupported: zipper transpile cannot lower recursive top-level self-union call step441187469; refuse the old evalTrie materialization fallback |

A ratio above `1.00 x` means the zipper evaluator is faster. Recursive datalog is included as a large generated control case; top-level union-saturating self recursion is rejected as unsupported instead of falling back to the concrete trie evaluator, so unsupported rows are reported explicitly rather than timed as zipper execution.
