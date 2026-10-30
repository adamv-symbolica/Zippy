# Graph queries

[All examples](README.md) · Milliseconds per evaluation.

## graph two-hop — reference 90-chain

Result: **264 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 2.696 | reference path sets |
| ROG exec | 1.096 |  |
| evalTrie | 0.502 | symbol trie |
| evalZ | 1.387 | zipper evaluator |
| ROG execT | 0.445 |  |
| Zippy_bits reference | 47.050897 | independent set representation |
| Zippy_bytes reference | 51.931317 | independent set representation |
| C++ OpenFst symbols | 0.559124 | optimized graph |
| C++ OpenFst bits | 7.754982 | optimized graph |
| C++ OpenFst bytes | 1.123777 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/programs/06-graph-two-hop-reference-90-chain/bytes-interned.rs) | 0.302891 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/programs/06-graph-two-hop-reference-90-chain/bytes-strings.rs) | 0.943036 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/programs/06-graph-two-hop-reference-90-chain/bits-bytes.rs) | 2.419812 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 2.947 | 2.285 |  |
| C++ OpenFst bits | 317.439 | 4937.866 |  |
| C++ OpenFst bytes | 13.472 | 855.506 |  |
| Rust PathMap bytes-interned | 11.188 | 1.785 | 3 |
| Rust PathMap bytes-strings | 23.399 | 1.408 | 3 |
| Rust PathMap bits-bytes | 126.104 | 3.045 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 1.625 | 1.625 | 2.070 |

## graph mutual — reference 90-chain

Result: **0 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 2.503 | reference path sets |
| ROG exec | 1.065 |  |
| evalTrie | 0.505 | symbol trie |
| evalZ | 0.700 | zipper evaluator |
| ROG execT | 0.293 |  |
| Zippy_bits reference | 16.281913 | independent set representation |
| Zippy_bytes reference | 32.806469 | independent set representation |
| C++ OpenFst symbols | 0.057671 | optimized graph |
| C++ OpenFst bits | 1.799922 | optimized graph |
| C++ OpenFst bytes | 0.089743 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/programs/07-graph-mutual-reference-90-chain/bytes-interned.rs) | 0.076648 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/programs/07-graph-mutual-reference-90-chain/bytes-strings.rs) | 0.237030 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/programs/07-graph-mutual-reference-90-chain/bits-bytes.rs) | 0.510438 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 2.110 | 613.803 |  |
| C++ OpenFst bits | 121.139 | 3003.812 |  |
| C++ OpenFst bytes | 9.643 | 831.564 |  |
| Rust PathMap bytes-interned | 8.223 | 3.223 | 3 |
| Rust PathMap bytes-strings | 27.692 | 1.468 | 3 |
| Rust PathMap bits-bytes | 61.557 | 2.440 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 1.506 | 1.506 | 1.799 |

## nonrecursive graph two-hop — 180 nodes, 359 edge facts

Result: **534 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 1.790 | symbol trie |
| evalZ | 2.322 | zipper evaluator |
| C++ OpenFst symbols | 1.159814 | optimized graph |
| C++ OpenFst bits | 17.332909 | optimized graph |
| C++ OpenFst bytes | 2.073832 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/large/23-nonrecursive-graph-two-hop-180-nodes-359-edge-facts/bytes-interned.rs) | 0.696540 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/large/23-nonrecursive-graph-two-hop-180-nodes-359-edge-facts/bytes-strings.rs) | 2.040778 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/large/23-nonrecursive-graph-two-hop-180-nodes-359-edge-facts/bits-bytes.rs) | 5.570816 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 73.152 | 10884.534 |  |
| C++ OpenFst bits | 447.418 | 5388.455 |  |
| C++ OpenFst bytes | 26.973 | 835.244 |  |
| Rust PathMap bytes-interned | 112.794 | 40.385 | 3 |
| Rust PathMap bytes-strings | 95.776 | 2.891 | 3 |
| Rust PathMap bits-bytes | 236.282 | 6.378 | 3 |

## nonrecursive graph two-hop — 360 nodes, 719 edge facts

Result: **1,074 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 2.730 | symbol trie |
| evalZ | 2.767 | zipper evaluator |
| C++ OpenFst symbols | 2.532236 | optimized graph |
| C++ OpenFst bits | 41.596234 | optimized graph |
| C++ OpenFst bytes | 6.484191 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/large/24-nonrecursive-graph-two-hop-360-nodes-719-edge-facts/bytes-interned.rs) | 1.454019 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/large/24-nonrecursive-graph-two-hop-360-nodes-719-edge-facts/bytes-strings.rs) | 4.274611 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/large/24-nonrecursive-graph-two-hop-360-nodes-719-edge-facts/bits-bytes.rs) | 12.539860 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 79.974 | 10891.669 |  |
| C++ OpenFst bits | 583.269 | 5895.787 |  |
| C++ OpenFst bytes | 12.920 | 813.840 |  |
| Rust PathMap bytes-interned | 119.083 | 36.960 | 3 |
| Rust PathMap bytes-strings | 86.675 | 2.381 | 3 |
| Rust PathMap bits-bytes | 261.176 | 7.187 | 3 |

## nonrecursive graph two-hop — 720 nodes, 1439 edge facts

Result: **2,154 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 3.985 | symbol trie |
| evalZ | 5.395 | zipper evaluator |
| C++ OpenFst symbols | 4.373811 | optimized graph |
| C++ OpenFst bits | 116.062603 | optimized graph |
| C++ OpenFst bytes | 7.548583 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/large/25-nonrecursive-graph-two-hop-720-nodes-1439-edge-facts/bytes-interned.rs) | 3.016685 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/large/25-nonrecursive-graph-two-hop-720-nodes-1439-edge-facts/bytes-strings.rs) | 8.667582 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/large/25-nonrecursive-graph-two-hop-720-nodes-1439-edge-facts/bits-bytes.rs) | 27.852288 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 73.302 | 10853.888 |  |
| C++ OpenFst bits | 546.293 | 7146.638 |  |
| C++ OpenFst bytes | 16.469 | 891.655 |  |
| Rust PathMap bytes-interned | 111.034 | 36.337 | 3 |
| Rust PathMap bytes-strings | 86.049 | 2.400 | 3 |
| Rust PathMap bits-bytes | 289.421 | 3.966 | 3 |
