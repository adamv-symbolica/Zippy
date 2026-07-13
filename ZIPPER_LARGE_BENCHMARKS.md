# Large Zipper Benchmarks

These rows are intentionally larger and more asymptotic than the mixed publication table. They compare direct `evalTrie` against direct `evalZ` after checking both produce the same `TrieSpace` result. The product rows are the key zipper stress tests: the source expression denotes an `n x n` product, but the consumer asks for one prefix or one exact path, so a zipper traversal should avoid materializing the full intermediate product.

| benchmark | size | result paths | evalTrie ms | evalZ ms | evalTrie / evalZ | note |
|---|---:|---:|---:|---:|---:|---|
| product intersected by one exact path | 2000 x 2000 product, 1 result path | 1 | 2.294 | 1.150 | 1.99 x | large intermediate product should not be materialized by zipper traversal |
| product intersected by one exact path | 10000 x 10000 product, 1 result path | 1 | 4.569 | 4.079 | 1.12 x | large intermediate product should not be materialized by zipper traversal |
| product intersected by one exact path | 30000 x 30000 product, 1 result path | 1 | 21.569 | 4.329 | 4.98 x | large intermediate product should not be materialized by zipper traversal |
| product restricted by one first-level prefix | 2000 x 2000 product, 2000 result paths | 2,000 | 0.581 | 0.273 | 2.13 x | large intermediate product should not be materialized by zipper traversal |
| product restricted by one first-level prefix | 10000 x 10000 product, 10000 result paths | 10,000 | 2.996 | 1.239 | 2.42 x | large intermediate product should not be materialized by zipper traversal |
| product restricted by one first-level prefix | 30000 x 30000 product, 30000 result paths | 30,000 | 10.244 | 4.117 | 2.49 x | large intermediate product should not be materialized by zipper traversal |
| aunt query over large generated family, one queried person | 9 generations x 80 people (2720 facts) | 0 | 0.150 | 0.216 | 0.69 x | large query-shaped dataset with small queried person set |
| aunt query over large generated family, one queried person | 11 generations x 160 people (6720 facts) | 0 | 0.100 | 0.132 | 0.76 x | large query-shaped dataset with small queried person set |
| semi-naive datalog over generated chain graph | 40 nodes, 79 edge facts | 820 | 20.471 | unsupported | n/a | evalZ unsupported: zipper transpile cannot lower recursive top-level self-union call step603856241; refuse the old evalTrie materialization fallback |
| semi-naive datalog over generated chain graph | 80 nodes, 159 edge facts | 3,240 | 94.908 | unsupported | n/a | evalZ unsupported: zipper transpile cannot lower recursive top-level self-union call step603856241; refuse the old evalTrie materialization fallback |

A ratio above `1.00 x` means the zipper evaluator is faster. Recursive datalog is included as a large generated control case; top-level union-saturating self recursion is rejected as unsupported instead of falling back to the concrete trie evaluator, so unsupported rows are reported explicitly rather than timed as zipper execution.
