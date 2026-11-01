# Graph queries

[All examples](README.md) · Milliseconds per evaluation.

## graph two-hop — reference 90-chain

Result: **264 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 3.179 | reference path sets |
| ROG exec | 1.144 |  |
| evalTrie | 0.740 | symbol trie |
| evalZ | 2.211 | zipper evaluator |
| ROG execT | 0.634 |  |
| Zippy_bits reference | 28.654242 | independent set representation |
| Zippy_bytes reference | 33.853140 | independent set representation |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/programs/06-graph-two-hop-reference-90-chain/symbols.cpp) | 0.499576 | optimized graph |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/programs/06-graph-two-hop-reference-90-chain/bits.cpp) | 7.705198 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/programs/06-graph-two-hop-reference-90-chain/bytes.cpp) | 1.089905 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/programs/06-graph-two-hop-reference-90-chain/bytes-interned.rs) | 0.230275 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/programs/06-graph-two-hop-reference-90-chain/bytes-strings.rs) | 0.858213 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/programs/06-graph-two-hop-reference-90-chain/bytes-length.rs) | 0.945144 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/programs/06-graph-two-hop-reference-90-chain/bits-bytes.rs) | 2.385586 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 2.354 | 615.440 |  |
| C++ OpenFst bits | 246.508 | 4994.224 |  |
| C++ OpenFst bytes | 7.539 | 880.541 |  |
| Rust PathMap bytes-interned | 10.955 | 1190.075 | 3 |
| Rust PathMap bytes-strings | 12.788 | 2565.033 | 3 |
| Rust PathMap bytes-length | 24.073 | 2499.911 | 3 |
| Rust PathMap bits-bytes | 143.634 | 14681.226 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 2.274 | 2.274 | 2.909 |

## graph mutual — reference 90-chain

Result: **0 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 2.552 | reference path sets |
| ROG exec | 1.076 |  |
| evalTrie | 0.352 | symbol trie |
| evalZ | 0.951 | zipper evaluator |
| ROG execT | 0.305 |  |
| Zippy_bits reference | 20.349845 | independent set representation |
| Zippy_bytes reference | 33.622614 | independent set representation |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/programs/07-graph-mutual-reference-90-chain/symbols.cpp) | 0.052926 | optimized graph |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/programs/07-graph-mutual-reference-90-chain/bits.cpp) | 1.726950 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/programs/07-graph-mutual-reference-90-chain/bytes.cpp) | 0.089915 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/programs/07-graph-mutual-reference-90-chain/bytes-interned.rs) | 0.029488 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/programs/07-graph-mutual-reference-90-chain/bytes-strings.rs) | 0.188060 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/programs/07-graph-mutual-reference-90-chain/bytes-length.rs) | 0.217060 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/programs/07-graph-mutual-reference-90-chain/bits-bytes.rs) | 0.480572 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 2.938 | 643.346 |  |
| C++ OpenFst bits | 111.894 | 2879.650 |  |
| C++ OpenFst bytes | 7.268 | 824.175 |  |
| Rust PathMap bytes-interned | 6.571 | 1097.528 | 3 |
| Rust PathMap bytes-strings | 10.030 | 2224.822 | 3 |
| Rust PathMap bytes-length | 21.950 | 2357.777 | 3 |
| Rust PathMap bits-bytes | 222.077 | 8166.333 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 1.855 | 1.855 | 2.160 |

## nonrecursive graph two-hop — 180 nodes, 359 edge facts

Result: **534 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 3.901 | symbol trie |
| evalZ | 2.632 | zipper evaluator |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/large/23-nonrecursive-graph-two-hop-180-nodes-359-edge-facts/symbols.cpp) | 1.094579 | optimized graph |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/large/23-nonrecursive-graph-two-hop-180-nodes-359-edge-facts/bits.cpp) | 15.251007 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/large/23-nonrecursive-graph-two-hop-180-nodes-359-edge-facts/bytes.cpp) | 2.033022 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/large/23-nonrecursive-graph-two-hop-180-nodes-359-edge-facts/bytes-interned.rs) | 0.628381 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/large/23-nonrecursive-graph-two-hop-180-nodes-359-edge-facts/bytes-strings.rs) | 1.880693 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/large/23-nonrecursive-graph-two-hop-180-nodes-359-edge-facts/bytes-length.rs) | 2.103062 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/large/23-nonrecursive-graph-two-hop-180-nodes-359-edge-facts/bits-bytes.rs) | 5.558858 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 78.300 | 36.775 |  |
| C++ OpenFst bits | 498.094 | 5151.704 |  |
| C++ OpenFst bytes | 11.310 | 932.570 |  |
| Rust PathMap bytes-interned | 109.115 | 1260.785 | 3 |
| Rust PathMap bytes-strings | 98.125 | 3.379 | 3 |
| Rust PathMap bytes-length | 79.718 | 4.443 | 3 |
| Rust PathMap bits-bytes | 379.231 | 16091.044 | 3 |

## nonrecursive graph two-hop — 360 nodes, 719 edge facts

Result: **1,074 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 2.014 | symbol trie |
| evalZ | 4.031 | zipper evaluator |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/large/24-nonrecursive-graph-two-hop-360-nodes-719-edge-facts/symbols.cpp) | 4.151412 | optimized graph |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/large/24-nonrecursive-graph-two-hop-360-nodes-719-edge-facts/bits.cpp) | 37.071357 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/large/24-nonrecursive-graph-two-hop-360-nodes-719-edge-facts/bytes.cpp) | 4.081868 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/large/24-nonrecursive-graph-two-hop-360-nodes-719-edge-facts/bytes-interned.rs) | 1.411620 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/large/24-nonrecursive-graph-two-hop-360-nodes-719-edge-facts/bytes-strings.rs) | 3.938598 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/large/24-nonrecursive-graph-two-hop-360-nodes-719-edge-facts/bytes-length.rs) | 4.513275 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/large/24-nonrecursive-graph-two-hop-360-nodes-719-edge-facts/bits-bytes.rs) | 13.171697 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 99.034 | 42.271 |  |
| C++ OpenFst bits | 509.083 | 5922.454 |  |
| C++ OpenFst bytes | 10.158 | 721.758 |  |
| Rust PathMap bytes-interned | 113.568 | 1261.631 | 3 |
| Rust PathMap bytes-strings | 90.715 | 2.510 | 3 |
| Rust PathMap bytes-length | 82.057 | 3.271 | 3 |
| Rust PathMap bits-bytes | 400.432 | 7443.641 | 1 |

## nonrecursive graph two-hop — 720 nodes, 1439 edge facts

Result: **2,154 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 2.302 | symbol trie |
| evalZ | 6.023 | zipper evaluator |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/large/25-nonrecursive-graph-two-hop-720-nodes-1439-edge-facts/symbols.cpp) | 4.416755 | optimized graph |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/large/25-nonrecursive-graph-two-hop-720-nodes-1439-edge-facts/bits.cpp) | 89.859389 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/large/25-nonrecursive-graph-two-hop-720-nodes-1439-edge-facts/bytes.cpp) | 7.784146 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/large/25-nonrecursive-graph-two-hop-720-nodes-1439-edge-facts/bytes-interned.rs) | 3.140901 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/large/25-nonrecursive-graph-two-hop-720-nodes-1439-edge-facts/bytes-strings.rs) | 8.056344 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/large/25-nonrecursive-graph-two-hop-720-nodes-1439-edge-facts/bytes-length.rs) | 9.238087 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/large/25-nonrecursive-graph-two-hop-720-nodes-1439-edge-facts/bits-bytes.rs) | 30.546690 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 74.387 | 34.846 |  |
| C++ OpenFst bits | 499.745 | 6452.212 |  |
| C++ OpenFst bytes | 10.667 | 773.388 |  |
| Rust PathMap bytes-interned | 106.484 | 1247.579 | 3 |
| Rust PathMap bytes-strings | 84.769 | 2.280 | 3 |
| Rust PathMap bytes-length | 78.665 | 4.225 | 3 |
| Rust PathMap bits-bytes | 453.231 | 8438.898 | 1 |
