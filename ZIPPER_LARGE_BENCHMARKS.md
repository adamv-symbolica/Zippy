# Large Zipper Benchmarks

These rows are intentionally larger and more asymptotic than the mixed publication table. They compare direct `evalTrie` against direct `evalZ` after checking both produce the same `TrieSpace` result. The product rows are the key zipper stress tests: the source expression denotes an `n x n` product, but the consumer asks for one prefix or one exact path, so a zipper traversal should avoid materializing the full intermediate product.

| benchmark | size | result paths | evalTrie ms | evalZ ms | evalTrie / evalZ | note |
|---|---:|---:|---:|---:|---:|---|
| product intersected by one exact path | 2000 x 2000 product, 1 result path | 1 | 1.464 | 2.272 | 0.64 x | large intermediate product should not be materialized by zipper traversal |
| product intersected by one exact path | 10000 x 10000 product, 1 result path | 1 | 3.588 | 2.537 | 1.41 x | large intermediate product should not be materialized by zipper traversal |
| product intersected by one exact path | 30000 x 30000 product, 1 result path | 1 | 8.794 | 3.758 | 2.34 x | large intermediate product should not be materialized by zipper traversal |
| product restricted by one first-level prefix | 2000 x 2000 product, 2000 result paths | 2,000 | 0.370 | 0.258 | 1.43 x | large intermediate product should not be materialized by zipper traversal |
| product restricted by one first-level prefix | 10000 x 10000 product, 10000 result paths | 10,000 | 2.465 | 1.192 | 2.07 x | large intermediate product should not be materialized by zipper traversal |
| product restricted by one first-level prefix | 30000 x 30000 product, 30000 result paths | 30,000 | 9.242 | 3.980 | 2.32 x | large intermediate product should not be materialized by zipper traversal |
| aunt query over large generated family, one queried person | 9 generations x 80 people (2720 facts) | 0 | 0.087 | 0.135 | 0.65 x | large query-shaped dataset with small queried person set |
| aunt query over large generated family, one queried person | 11 generations x 160 people (6720 facts) | 0 | 0.083 | 0.121 | 0.69 x | large query-shaped dataset with small queried person set |
| semi-naive datalog over generated chain graph | 40 nodes, 79 edge facts | 820 | 19.174 | unsupported | n/a | evalZ unsupported: zipper transpile cannot lower recursive top-level self-union call step603856241; refuse the old evalTrie materialization fallback |
| semi-naive datalog over generated chain graph | 80 nodes, 159 edge facts | 3,240 | 90.336 | unsupported | n/a | evalZ unsupported: zipper transpile cannot lower recursive top-level self-union call step603856241; refuse the old evalTrie materialization fallback |

A ratio above `1.00 x` means the zipper evaluator is faster. Recursive datalog is included as a large generated control case; top-level union-saturating self recursion is rejected as unsupported instead of falling back to the concrete trie evaluator, so unsupported rows are reported explicitly rather than timed as zipper execution.
