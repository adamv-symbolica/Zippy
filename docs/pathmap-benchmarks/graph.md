# Graph queries

[All examples](README.md) · Milliseconds per evaluation.

## graph two-hop — reference 90-chain

Result: **264 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 2.736 | reference path sets |
| ROG exec | 1.021 |  |
| evalTrie | 0.633 | symbol trie |
| evalZ | 2.477 | zipper evaluator |
| ROG execT | 0.594 |  |
| Zippy_bits reference | 31.243155 | independent set representation |
| Zippy_bytes reference | 35.463635 | independent set representation |
| C++ OpenFst symbols | 0.489844 | optimized graph |
| C++ OpenFst bits | 6.508813 | optimized graph |
| C++ OpenFst bytes | 1.024782 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/programs/06-graph-two-hop-reference-90-chain/bytes-interned.rs) | 0.299661 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/programs/06-graph-two-hop-reference-90-chain/bytes-strings.rs) | 1.085831 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/programs/06-graph-two-hop-reference-90-chain/bits-bytes.rs) | 2.858831 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 1.390 | 477.360 |  |
| C++ OpenFst bits | 107.836 | 3996.673 |  |
| C++ OpenFst bytes | 4.844 | 656.355 |  |
| Rust PathMap bytes-interned | 6.601 | 1.240 | 3 |
| Rust PathMap bytes-strings | 24.610 | 2.476 | 3 |
| Rust PathMap bits-bytes | 243.759 | 11.986 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 2.019 | 2.019 | 2.613 |

## graph mutual — reference 90-chain

Result: **0 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 2.507 | reference path sets |
| ROG exec | 0.969 |  |
| evalTrie | 0.345 | symbol trie |
| evalZ | 0.922 | zipper evaluator |
| ROG execT | 0.310 |  |
| Zippy_bits reference | 24.367160 | independent set representation |
| Zippy_bytes reference | 33.285147 | independent set representation |
| C++ OpenFst symbols | 0.052132 | optimized graph |
| C++ OpenFst bits | 1.685169 | optimized graph |
| C++ OpenFst bytes | 0.085665 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/programs/07-graph-mutual-reference-90-chain/bytes-interned.rs) | 0.088941 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/programs/07-graph-mutual-reference-90-chain/bytes-strings.rs) | 0.303856 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/programs/07-graph-mutual-reference-90-chain/bits-bytes.rs) | 0.697046 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 1.408 | 461.388 |  |
| C++ OpenFst bits | 55.997 | 2466.386 |  |
| C++ OpenFst bytes | 5.099 | 588.058 |  |
| Rust PathMap bytes-interned | 4.999 | 1.563 | 3 |
| Rust PathMap bytes-strings | 10.159 | 1.732 | 3 |
| Rust PathMap bits-bytes | 118.773 | 6.976 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 1.697 | 1.697 | 2.007 |

## nonrecursive graph two-hop — 180 nodes, 359 edge facts

Result: **534 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 3.944 | symbol trie |
| evalZ | 2.204 | zipper evaluator |
| C++ OpenFst symbols | 1.089570 | optimized graph |
| C++ OpenFst bits | 14.921492 | optimized graph |
| C++ OpenFst bytes | 2.031921 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/large/23-nonrecursive-graph-two-hop-180-nodes-359-edge-facts/bytes-interned.rs) | 0.697400 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/large/23-nonrecursive-graph-two-hop-180-nodes-359-edge-facts/bytes-strings.rs) | 2.350771 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/large/23-nonrecursive-graph-two-hop-180-nodes-359-edge-facts/bits-bytes.rs) | 6.747201 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 65.636 | 29.365 |  |
| C++ OpenFst bits | 354.880 | 4449.630 |  |
| C++ OpenFst bytes | 11.240 | 666.610 |  |
| Rust PathMap bytes-interned | 111.844 | 76.308 | 3 |
| Rust PathMap bytes-strings | 86.031 | 2.476 | 3 |
| Rust PathMap bits-bytes | 409.602 | 15.518 | 3 |

## nonrecursive graph two-hop — 360 nodes, 719 edge facts

Result: **1,074 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 2.581 | symbol trie |
| evalZ | 2.937 | zipper evaluator |
| C++ OpenFst symbols | 2.252318 | optimized graph |
| C++ OpenFst bits | 33.418858 | optimized graph |
| C++ OpenFst bytes | 4.044142 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/large/24-nonrecursive-graph-two-hop-360-nodes-719-edge-facts/bytes-interned.rs) | 1.450237 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/large/24-nonrecursive-graph-two-hop-360-nodes-719-edge-facts/bytes-strings.rs) | 4.930325 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/large/24-nonrecursive-graph-two-hop-360-nodes-719-edge-facts/bits-bytes.rs) | 15.415583 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 65.689 | 30.181 |  |
| C++ OpenFst bits | 390.093 | 5194.363 |  |
| C++ OpenFst bytes | 11.783 | 683.498 |  |
| Rust PathMap bytes-interned | 99.982 | 92.613 | 3 |
| Rust PathMap bytes-strings | 84.376 | 2.370 | 3 |
| Rust PathMap bits-bytes | 422.292 | 21.341 | 1 |

## nonrecursive graph two-hop — 720 nodes, 1439 edge facts

Result: **2,154 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 3.521 | symbol trie |
| evalZ | 6.207 | zipper evaluator |
| C++ OpenFst symbols | 4.277060 | optimized graph |
| C++ OpenFst bits | 84.506178 | optimized graph |
| C++ OpenFst bytes | 6.359960 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/large/25-nonrecursive-graph-two-hop-720-nodes-1439-edge-facts/bytes-interned.rs) | 3.040667 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/large/25-nonrecursive-graph-two-hop-720-nodes-1439-edge-facts/bytes-strings.rs) | 10.045258 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/large/25-nonrecursive-graph-two-hop-720-nodes-1439-edge-facts/bits-bytes.rs) | 34.801583 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 1.943 | 2.100 |  |
| C++ OpenFst bits | 154.940 | 5987.974 |  |
| C++ OpenFst bytes | 6.325 | 692.845 |  |
| Rust PathMap bytes-interned | 101.915 | 80.245 | 3 |
| Rust PathMap bytes-strings | 89.475 | 2.498 | 3 |
| Rust PathMap bits-bytes | 472.313 | 23.893 | 1 |
