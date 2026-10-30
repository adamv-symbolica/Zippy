# Supplement: large selective products

[All examples](README.md) · Milliseconds per evaluation.

## product intersected by one exact path — 2000 x 2000 product, 1 result path

Result: **1 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 0.568 | symbol trie |
| evalZ | 0.077 | zipper evaluator |
| C++ OpenFst symbols | 0.284763 | optimized graph |
| C++ OpenFst bits | 0.009268 | optimized graph |
| C++ OpenFst bytes | 0.025197 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/large/26-product-intersected-by-one-exact-path-2000-x-2000-product-1-result-path/bytes-interned.rs) | 0.243390 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/large/26-product-intersected-by-one-exact-path-2000-x-2000-product-1-result-path/bytes-strings.rs) | 0.424633 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/large/26-product-intersected-by-one-exact-path-2000-x-2000-product-1-result-path/bits-bytes.rs) | 0.694480 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 1.211 | 1.686 |  |
| C++ OpenFst bits | 1.254 | 1.715 |  |
| C++ OpenFst bytes | 0.938 | 1.924 |  |
| Rust PathMap bytes-interned | 64.289 | 64.282 | 3 |
| Rust PathMap bytes-strings | 2.828 | 1.682 | 3 |
| Rust PathMap bits-bytes | 2.533 | 1.848 | 3 |

## product intersected by one exact path — 10000 x 10000 product, 1 result path

Result: **1 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 2.650 | symbol trie |
| evalZ | 0.071 | zipper evaluator |
| C++ OpenFst symbols | 1.544992 | optimized graph |
| C++ OpenFst bits | 0.019703 | optimized graph |
| C++ OpenFst bytes | 0.048399 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/large/27-product-intersected-by-one-exact-path-10000-x-10000-product-1-result-path/bytes-interned.rs) | 1.218988 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/large/27-product-intersected-by-one-exact-path-10000-x-10000-product-1-result-path/bytes-strings.rs) | 2.264369 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/large/27-product-intersected-by-one-exact-path-10000-x-10000-product-1-result-path/bits-bytes.rs) | 3.675162 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 1.123 | 342.012 |  |
| C++ OpenFst bits | 0.838 | 340.319 |  |
| C++ OpenFst bytes | 0.845 | 1.689 |  |
| Rust PathMap bytes-interned | 2.432 | 1.756 | 3 |
| Rust PathMap bytes-strings | 1.693 | 1.688 | 3 |
| Rust PathMap bits-bytes | 1.351 | 1.671 | 3 |

## product intersected by one exact path — 30000 x 30000 product, 1 result path

Result: **1 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 7.214 | symbol trie |
| evalZ | 0.072 | zipper evaluator |
| C++ OpenFst symbols | 4.814416 | optimized graph |
| C++ OpenFst bits | 0.020707 | optimized graph |
| C++ OpenFst bytes | 0.063762 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/large/28-product-intersected-by-one-exact-path-30000-x-30000-product-1-result-path/bytes-interned.rs) | 3.683264 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/large/28-product-intersected-by-one-exact-path-30000-x-30000-product-1-result-path/bytes-strings.rs) | 6.771097 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/large/28-product-intersected-by-one-exact-path-30000-x-30000-product-1-result-path/bits-bytes.rs) | 11.049544 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 1.153 | 1.758 |  |
| C++ OpenFst bits | 1.031 | 1.789 |  |
| C++ OpenFst bytes | 0.967 | 1.755 |  |
| Rust PathMap bytes-interned | 2.029 | 1.813 | 3 |
| Rust PathMap bytes-strings | 1.292 | 1.714 | 3 |
| Rust PathMap bits-bytes | 1.707 | 1.550 | 3 |

## product restricted by one first-level prefix — 2000 x 2000 product, 2000 result paths

Result: **2,000 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 0.535 | symbol trie |
| evalZ | 0.017 | zipper evaluator |
| C++ OpenFst symbols | 0.320836 | optimized graph |
| C++ OpenFst bits | 0.027800 | optimized graph |
| C++ OpenFst bytes | 0.071510 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/large/29-product-restricted-by-one-first-level-prefix-2000-x-2000-product-2000-result-paths/bytes-interned.rs) | 0.288556 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/large/29-product-restricted-by-one-first-level-prefix-2000-x-2000-product-2000-result-paths/bytes-strings.rs) | 0.564490 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/large/29-product-restricted-by-one-first-level-prefix-2000-x-2000-product-2000-result-paths/bits-bytes.rs) | 1.079072 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 1.252 | 361.186 |  |
| C++ OpenFst bits | 0.948 | 371.419 |  |
| C++ OpenFst bytes | 0.790 | 1.288 |  |
| Rust PathMap bytes-interned | 1.764 | 1.620 | 3 |
| Rust PathMap bytes-strings | 1.238 | 1.688 | 3 |
| Rust PathMap bits-bytes | 1.052 | 1.440 | 3 |

## product restricted by one first-level prefix — 10000 x 10000 product, 10000 result paths

Result: **10,000 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 2.024 | symbol trie |
| evalZ | 0.014 | zipper evaluator |
| C++ OpenFst symbols | 1.701440 | optimized graph |
| C++ OpenFst bits | 0.043241 | optimized graph |
| C++ OpenFst bytes | 0.080984 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/large/30-product-restricted-by-one-first-level-prefix-10000-x-10000-product-10000-result-paths/bytes-interned.rs) | 1.449556 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/large/30-product-restricted-by-one-first-level-prefix-10000-x-10000-product-10000-result-paths/bytes-strings.rs) | 2.997596 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/large/30-product-restricted-by-one-first-level-prefix-10000-x-10000-product-10000-result-paths/bits-bytes.rs) | 5.529494 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 1.378 | 1.446 |  |
| C++ OpenFst bits | 0.897 | 1.696 |  |
| C++ OpenFst bytes | 0.934 | 1.460 |  |
| Rust PathMap bytes-interned | 2.373 | 2.070 | 3 |
| Rust PathMap bytes-strings | 2.302 | 1.865 | 3 |
| Rust PathMap bits-bytes | 1.992 | 1.775 | 3 |

## product restricted by one first-level prefix — 30000 x 30000 product, 30000 result paths

Result: **30,000 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 11.087 | symbol trie |
| evalZ | 0.014 | zipper evaluator |
| C++ OpenFst symbols | 5.289835 | optimized graph |
| C++ OpenFst bits | 0.048785 | optimized graph |
| C++ OpenFst bytes | 0.102994 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/large/31-product-restricted-by-one-first-level-prefix-30000-x-30000-product-30000-result-paths/bytes-interned.rs) | 4.345703 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/large/31-product-restricted-by-one-first-level-prefix-30000-x-30000-product-30000-result-paths/bytes-strings.rs) | 9.380812 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/large/31-product-restricted-by-one-first-level-prefix-30000-x-30000-product-30000-result-paths/bits-bytes.rs) | 16.765999 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 1.309 | 1.869 |  |
| C++ OpenFst bits | 0.943 | 1.796 |  |
| C++ OpenFst bytes | 0.824 | 1.292 |  |
| Rust PathMap bytes-interned | 3.148 | 2.393 | 3 |
| Rust PathMap bytes-strings | 1.409 | 1.771 | 3 |
| Rust PathMap bits-bytes | 1.190 | 2.267 | 3 |
