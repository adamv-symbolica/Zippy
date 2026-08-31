# Large Zipper Benchmarks

These rows are intentionally larger and more asymptotic than the mixed publication table. They compare direct `evalTrie` against direct `evalZ` after checking both produce the same `TrieSpace` result. The product rows are the key zipper stress tests: the source expression denotes an `n x n` product, but the consumer asks for one prefix or one exact path, so a zipper traversal should avoid materializing the full intermediate product.

| benchmark | size | result paths | evalTrie ms | evalZ ms | evalTrie / evalZ | note |
|---|---:|---:|---:|---:|---:|---|
| product intersected by one exact path | 2000 x 2000 product, 1 result path | 1 | 1.382 | 0.195 | 7.08 x | large intermediate product should not be materialized by zipper traversal |
| product intersected by one exact path | 10000 x 10000 product, 1 result path | 1 | 3.752 | 0.452 | 8.30 x | large intermediate product should not be materialized by zipper traversal |
| product intersected by one exact path | 30000 x 30000 product, 1 result path | 1 | 9.578 | 1.218 | 7.86 x | large intermediate product should not be materialized by zipper traversal |
| product restricted by one first-level prefix | 2000 x 2000 product, 2000 result paths | 2,000 | 0.685 | 0.058 | 11.77 x | large intermediate product should not be materialized by zipper traversal |
| product restricted by one first-level prefix | 10000 x 10000 product, 10000 result paths | 10,000 | 3.355 | 0.060 | 55.49 x | large intermediate product should not be materialized by zipper traversal |
| product restricted by one first-level prefix | 30000 x 30000 product, 30000 result paths | 30,000 | 14.824 | 0.061 | 241.30 x | large intermediate product should not be materialized by zipper traversal |
| aunt query over large generated family, one queried person | 9 generations x 80 people (2720 facts) | 0 | 0.638 | 0.620 | 1.03 x | large query-shaped dataset with small queried person set |
| aunt query over large generated family, one queried person | 11 generations x 160 people (6720 facts) | 0 | 0.275 | 0.352 | 0.78 x | large query-shaped dataset with small queried person set |
| SCC mutual reachability | 6 directed edges, 8 mutually reachable pairs | 8 | 3.137 | 5.978 | 0.52 x | state-dependent tail projection uses the lazy exact synchronous fixpoint fallback |
| semi-naive datalog over generated chain graph | 40 nodes, 79 edge facts | 820 | 44.390 | 950.284 | 0.05 x | recursive union-saturating routine lowered to zipper-local execution |
| semi-naive datalog over generated chain graph | 80 nodes, 159 edge facts | 3,240 | 202.770 | 13808.144 | 0.01 x | recursive union-saturating routine lowered to zipper-local execution |

A ratio above `1.00 x` means the zipper evaluator is faster. Recursive Datalog is included as a large generated control case and is timed through direct `evalZ` lowering; its state-negative delta subtraction selects the lazy exact synchronous fixpoint fallback, while structurally positive, productive recursive steps use prefix-demand cells. An unsupported cell is reserved for a genuine lowering failure rather than a concrete-trie fallback.
