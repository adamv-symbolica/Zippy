# Supplement: large selective products

[All examples](README.md) · Milliseconds per evaluation.

## product intersected by one exact path — 2000 x 2000 product, 1 result path

Result: **1 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 0.566 | symbol trie |
| evalZ | 0.076 | zipper evaluator |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/large/26-product-intersected-by-one-exact-path-2000-x-2000-product-1-result-path/symbols.cpp) | 0.295316 | optimized graph |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/large/26-product-intersected-by-one-exact-path-2000-x-2000-product-1-result-path/bits.cpp) | 0.009546 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/large/26-product-intersected-by-one-exact-path-2000-x-2000-product-1-result-path/bytes.cpp) | 0.024817 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/large/26-product-intersected-by-one-exact-path-2000-x-2000-product-1-result-path/bytes-interned.rs) | 0.230211 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/large/26-product-intersected-by-one-exact-path-2000-x-2000-product-1-result-path/bytes-strings.rs) | 0.418570 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/large/26-product-intersected-by-one-exact-path-2000-x-2000-product-1-result-path/bytes-length.rs) | 0.411454 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/large/26-product-intersected-by-one-exact-path-2000-x-2000-product-1-result-path/bits-bytes.rs) | 0.651182 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 70.232 | 38.674 |  |
| C++ OpenFst bits | 3.114 | 3.252 |  |
| C++ OpenFst bytes | 2.772 | 2.248 |  |
| Rust PathMap bytes-interned | 68.115 | 71.867 | 3 |
| Rust PathMap bytes-strings | 3.070 | 1.557 | 3 |
| Rust PathMap bytes-length | 2.763 | 1.528 | 3 |
| Rust PathMap bits-bytes | 2.570 | 1.594 | 3 |

## product intersected by one exact path — 10000 x 10000 product, 1 result path

Result: **1 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 5.632 | symbol trie |
| evalZ | 0.070 | zipper evaluator |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/large/27-product-intersected-by-one-exact-path-10000-x-10000-product-1-result-path/symbols.cpp) | 1.640146 | optimized graph |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/large/27-product-intersected-by-one-exact-path-10000-x-10000-product-1-result-path/bits.cpp) | 0.019700 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/large/27-product-intersected-by-one-exact-path-10000-x-10000-product-1-result-path/bytes.cpp) | 0.049016 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/large/27-product-intersected-by-one-exact-path-10000-x-10000-product-1-result-path/bytes-interned.rs) | 1.148007 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/large/27-product-intersected-by-one-exact-path-10000-x-10000-product-1-result-path/bytes-strings.rs) | 2.217378 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/large/27-product-intersected-by-one-exact-path-10000-x-10000-product-1-result-path/bytes-length.rs) | 2.121802 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/large/27-product-intersected-by-one-exact-path-10000-x-10000-product-1-result-path/bits-bytes.rs) | 3.405368 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 2.382 | 392.316 |  |
| C++ OpenFst bits | 1.168 | 421.284 |  |
| C++ OpenFst bytes | 1.158 | 2.679 |  |
| Rust PathMap bytes-interned | 2.654 | 1580.785 | 3 |
| Rust PathMap bytes-strings | 1.816 | 1.691 | 3 |
| Rust PathMap bytes-length | 2.005 | 2.112 | 3 |
| Rust PathMap bits-bytes | 1.656 | 1.946 | 3 |

## product intersected by one exact path — 30000 x 30000 product, 1 result path

Result: **1 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 7.388 | symbol trie |
| evalZ | 0.069 | zipper evaluator |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/large/28-product-intersected-by-one-exact-path-30000-x-30000-product-1-result-path/symbols.cpp) | 4.900080 | optimized graph |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/large/28-product-intersected-by-one-exact-path-30000-x-30000-product-1-result-path/bits.cpp) | 0.020793 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/large/28-product-intersected-by-one-exact-path-30000-x-30000-product-1-result-path/bytes.cpp) | 0.063192 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/large/28-product-intersected-by-one-exact-path-30000-x-30000-product-1-result-path/bytes-interned.rs) | 3.915198 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/large/28-product-intersected-by-one-exact-path-30000-x-30000-product-1-result-path/bytes-strings.rs) | 6.768673 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/large/28-product-intersected-by-one-exact-path-30000-x-30000-product-1-result-path/bytes-length.rs) | 6.784767 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/large/28-product-intersected-by-one-exact-path-30000-x-30000-product-1-result-path/bits-bytes.rs) | 10.103765 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 3.237 | 4.957 |  |
| C++ OpenFst bits | 1.405 | 2.460 |  |
| C++ OpenFst bytes | 0.970 | 2.195 |  |
| Rust PathMap bytes-interned | 3.297 | 2.855 | 3 |
| Rust PathMap bytes-strings | 2.080 | 2.240 | 3 |
| Rust PathMap bytes-length | 2.132 | 2.443 | 3 |
| Rust PathMap bits-bytes | 1.832 | 2.414 | 3 |

## product restricted by one first-level prefix — 2000 x 2000 product, 2000 result paths

Result: **2,000 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 0.432 | symbol trie |
| evalZ | 0.015 | zipper evaluator |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/large/29-product-restricted-by-one-first-level-prefix-2000-x-2000-product-2000-result-paths/symbols.cpp) | 0.325988 | optimized graph |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/large/29-product-restricted-by-one-first-level-prefix-2000-x-2000-product-2000-result-paths/bits.cpp) | 0.028473 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/large/29-product-restricted-by-one-first-level-prefix-2000-x-2000-product-2000-result-paths/bytes.cpp) | 0.071641 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/large/29-product-restricted-by-one-first-level-prefix-2000-x-2000-product-2000-result-paths/bytes-interned.rs) | 0.226743 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/large/29-product-restricted-by-one-first-level-prefix-2000-x-2000-product-2000-result-paths/bytes-strings.rs) | 0.433779 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/large/29-product-restricted-by-one-first-level-prefix-2000-x-2000-product-2000-result-paths/bytes-length.rs) | 0.409636 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/large/29-product-restricted-by-one-first-level-prefix-2000-x-2000-product-2000-result-paths/bits-bytes.rs) | 0.698040 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 2.263 | 2.150 |  |
| C++ OpenFst bits | 1.333 | 2.364 |  |
| C++ OpenFst bytes | 1.108 | 2.250 |  |
| Rust PathMap bytes-interned | 1.823 | 1.346 | 3 |
| Rust PathMap bytes-strings | 1.344 | 1.279 | 3 |
| Rust PathMap bytes-length | 1.296 | 1.380 | 3 |
| Rust PathMap bits-bytes | 1.095 | 1.336 | 3 |

## product restricted by one first-level prefix — 10000 x 10000 product, 10000 result paths

Result: **10,000 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 2.111 | symbol trie |
| evalZ | 0.013 | zipper evaluator |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/large/30-product-restricted-by-one-first-level-prefix-10000-x-10000-product-10000-result-paths/symbols.cpp) | 1.723877 | optimized graph |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/large/30-product-restricted-by-one-first-level-prefix-10000-x-10000-product-10000-result-paths/bits.cpp) | 0.043542 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/large/30-product-restricted-by-one-first-level-prefix-10000-x-10000-product-10000-result-paths/bytes.cpp) | 0.082778 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/large/30-product-restricted-by-one-first-level-prefix-10000-x-10000-product-10000-result-paths/bytes-interned.rs) | 1.158373 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/large/30-product-restricted-by-one-first-level-prefix-10000-x-10000-product-10000-result-paths/bytes-strings.rs) | 2.261585 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/large/30-product-restricted-by-one-first-level-prefix-10000-x-10000-product-10000-result-paths/bytes-length.rs) | 2.110976 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/large/30-product-restricted-by-one-first-level-prefix-10000-x-10000-product-10000-result-paths/bits-bytes.rs) | 3.703139 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 3.388 | 2.771 |  |
| C++ OpenFst bits | 2.469 | 2.450 |  |
| C++ OpenFst bytes | 2.708 | 2.571 |  |
| Rust PathMap bytes-interned | 2.424 | 1.803 | 3 |
| Rust PathMap bytes-strings | 1.985 | 1.837 | 3 |
| Rust PathMap bytes-length | 2.045 | 2.083 | 3 |
| Rust PathMap bits-bytes | 2.027 | 2.020 | 3 |

## product restricted by one first-level prefix — 30000 x 30000 product, 30000 result paths

Result: **30,000 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 11.410 | symbol trie |
| evalZ | 0.013 | zipper evaluator |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/large/31-product-restricted-by-one-first-level-prefix-30000-x-30000-product-30000-result-paths/symbols.cpp) | 5.416615 | optimized graph |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/large/31-product-restricted-by-one-first-level-prefix-30000-x-30000-product-30000-result-paths/bits.cpp) | 0.049356 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/large/31-product-restricted-by-one-first-level-prefix-30000-x-30000-product-30000-result-paths/bytes.cpp) | 0.102558 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/large/31-product-restricted-by-one-first-level-prefix-30000-x-30000-product-30000-result-paths/bytes-interned.rs) | 3.497055 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/large/31-product-restricted-by-one-first-level-prefix-30000-x-30000-product-30000-result-paths/bytes-strings.rs) | 7.193620 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/large/31-product-restricted-by-one-first-level-prefix-30000-x-30000-product-30000-result-paths/bytes-length.rs) | 6.564177 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/large/31-product-restricted-by-one-first-level-prefix-30000-x-30000-product-30000-result-paths/bits-bytes.rs) | 12.296505 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 1.843 | 432.171 |  |
| C++ OpenFst bits | 2.009 | 539.472 |  |
| C++ OpenFst bytes | 1.710 | 2.221 |  |
| Rust PathMap bytes-interned | 2.471 | 1965.764 | 3 |
| Rust PathMap bytes-strings | 1.616 | 1.993 | 3 |
| Rust PathMap bytes-length | 1.637 | 3.143 | 3 |
| Rust PathMap bits-bytes | 1.614 | 2.001 | 3 |
