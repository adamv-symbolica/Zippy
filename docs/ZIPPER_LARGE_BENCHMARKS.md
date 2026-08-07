# Large Zipper Benchmarks

These rows are intentionally larger and more asymptotic than the mixed publication table. They compare direct `evalTrie` against direct `evalZ` after checking both produce the same `TrieSpace` result. The product rows are the key zipper stress tests: the source expression denotes an `n x n` product, but the consumer asks for one prefix or one exact path, so a zipper traversal should avoid materializing the full intermediate product.

| benchmark | size | result paths | evalTrie ms | evalZ ms | evalTrie / evalZ | note |
|---|---:|---:|---:|---:|---:|---|
| product intersected by one exact path | 2000 x 2000 product, 1 result path | 1 | 2.203 | 1.125 | 1.96 x | large intermediate product should not be materialized by zipper traversal |
| product intersected by one exact path | 10000 x 10000 product, 1 result path | 1 | 3.889 | 6.453 | 0.60 x | large intermediate product should not be materialized by zipper traversal |
| product intersected by one exact path | 30000 x 30000 product, 1 result path | 1 | 10.218 | 4.023 | 2.54 x | large intermediate product should not be materialized by zipper traversal |
| product restricted by one first-level prefix | 2000 x 2000 product, 2000 result paths | 2,000 | 0.416 | 0.277 | 1.50 x | large intermediate product should not be materialized by zipper traversal |
| product restricted by one first-level prefix | 10000 x 10000 product, 10000 result paths | 10,000 | 2.812 | 1.223 | 2.30 x | large intermediate product should not be materialized by zipper traversal |
| product restricted by one first-level prefix | 30000 x 30000 product, 30000 result paths | 30,000 | 10.442 | 4.058 | 2.57 x | large intermediate product should not be materialized by zipper traversal |
| aunt query over large generated family, one queried person | 9 generations x 80 people (2720 facts) | 0 | 0.173 | 0.142 | 1.22 x | large query-shaped dataset with small queried person set |
| aunt query over large generated family, one queried person | 11 generations x 160 people (6720 facts) | 0 | 0.085 | 0.126 | 0.67 x | large query-shaped dataset with small queried person set |
| semi-naive datalog over generated chain graph | 40 nodes, 79 edge facts | 820 | 21.272 | unsupported | n/a | evalZ unsupported: zipper transpile cannot lower recursive top-level self-union call step1582071873; refuse the old evalTrie materialization fallback |
| semi-naive datalog over generated chain graph | 80 nodes, 159 edge facts | 3,240 | 94.599 | unsupported | n/a | evalZ unsupported: zipper transpile cannot lower recursive top-level self-union call step1582071873; refuse the old evalTrie materialization fallback |

A ratio above `1.00 x` means the zipper evaluator is faster. Recursive datalog is included as a large generated control case; top-level union-saturating self recursion is rejected as unsupported instead of falling back to the concrete trie evaluator, so unsupported rows are reported explicitly rather than timed as zipper execution.
