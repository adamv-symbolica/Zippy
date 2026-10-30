# Supplement: large selective products

[All examples](README.md) · Milliseconds per evaluation.

## product intersected by one exact path — 2000 x 2000 product, 1 result path

Result: **1 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 0.539 | symbol trie |
| evalZ | 0.078 | zipper evaluator |
| C++ OpenFst symbols | 0.287871 | optimized graph |
| C++ OpenFst bits | 0.009684 | optimized graph |
| C++ OpenFst bytes | 0.025597 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/large/26-product-intersected-by-one-exact-path-2000-x-2000-product-1-result-path/bytes-interned.rs) | 0.239032 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/large/26-product-intersected-by-one-exact-path-2000-x-2000-product-1-result-path/bytes-strings.rs) | 0.424889 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/large/26-product-intersected-by-one-exact-path-2000-x-2000-product-1-result-path/bits-bytes.rs) | 0.670547 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 61.635 | 10696.218 |  |
| C++ OpenFst bits | 3.188 | 403.718 |  |
| C++ OpenFst bytes | 3.928 | 3.116 |  |
| Rust PathMap bytes-interned | 66.727 | 39.737 | 3 |
| Rust PathMap bytes-strings | 3.208 | 1.849 | 3 |
| Rust PathMap bits-bytes | 3.095 | 2.499 | 3 |

## product intersected by one exact path — 10000 x 10000 product, 1 result path

Result: **1 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 2.613 | symbol trie |
| evalZ | 0.081 | zipper evaluator |
| C++ OpenFst symbols | 1.635623 | optimized graph |
| C++ OpenFst bits | 0.021235 | optimized graph |
| C++ OpenFst bytes | 0.051535 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/large/27-product-intersected-by-one-exact-path-10000-x-10000-product-1-result-path/bytes-interned.rs) | 1.221174 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/large/27-product-intersected-by-one-exact-path-10000-x-10000-product-1-result-path/bytes-strings.rs) | 2.245808 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/large/27-product-intersected-by-one-exact-path-10000-x-10000-product-1-result-path/bits-bytes.rs) | 3.483039 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 1.769 | 6.698 |  |
| C++ OpenFst bits | 1.414 | 2.514 |  |
| C++ OpenFst bytes | 1.526 | 3.025 |  |
| Rust PathMap bytes-interned | 2.150 | 1.574 | 3 |
| Rust PathMap bytes-strings | 1.748 | 1.887 | 3 |
| Rust PathMap bits-bytes | 1.606 | 1.859 | 3 |

## product intersected by one exact path — 30000 x 30000 product, 1 result path

Result: **1 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 7.272 | symbol trie |
| evalZ | 0.229 | zipper evaluator |
| C++ OpenFst symbols | 5.239589 | optimized graph |
| C++ OpenFst bits | 0.022537 | optimized graph |
| C++ OpenFst bytes | 0.068283 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/large/28-product-intersected-by-one-exact-path-30000-x-30000-product-1-result-path/bytes-interned.rs) | 3.712158 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/large/28-product-intersected-by-one-exact-path-30000-x-30000-product-1-result-path/bytes-strings.rs) | 7.177486 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/large/28-product-intersected-by-one-exact-path-30000-x-30000-product-1-result-path/bits-bytes.rs) | 10.478227 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 2.031 | 5.020 |  |
| C++ OpenFst bits | 1.401 | 3.058 |  |
| C++ OpenFst bytes | 1.435 | 6.119 |  |
| Rust PathMap bytes-interned | 2.247 | 2.102 | 3 |
| Rust PathMap bytes-strings | 1.805 | 2.322 | 3 |
| Rust PathMap bits-bytes | 2.822 | 2.314 | 3 |

## product restricted by one first-level prefix — 2000 x 2000 product, 2000 result paths

Result: **2,000 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 0.497 | symbol trie |
| evalZ | 0.055 | zipper evaluator |
| C++ OpenFst symbols | 0.332190 | optimized graph |
| C++ OpenFst bits | 0.029538 | optimized graph |
| C++ OpenFst bytes | 0.077080 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/large/29-product-restricted-by-one-first-level-prefix-2000-x-2000-product-2000-result-paths/bytes-interned.rs) | 0.276813 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/large/29-product-restricted-by-one-first-level-prefix-2000-x-2000-product-2000-result-paths/bytes-strings.rs) | 0.512652 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/large/29-product-restricted-by-one-first-level-prefix-2000-x-2000-product-2000-result-paths/bits-bytes.rs) | 0.929739 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 2.158 | 4.342 |  |
| C++ OpenFst bits | 4.036 | 6.266 |  |
| C++ OpenFst bytes | 3.134 | 3.216 |  |
| Rust PathMap bytes-interned | 2.033 | 1.634 | 3 |
| Rust PathMap bytes-strings | 1.338 | 1.632 | 3 |
| Rust PathMap bits-bytes | 1.105 | 1.709 | 3 |

## product restricted by one first-level prefix — 10000 x 10000 product, 10000 result paths

Result: **10,000 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 2.029 | symbol trie |
| evalZ | 0.015 | zipper evaluator |
| C++ OpenFst symbols | 1.738443 | optimized graph |
| C++ OpenFst bits | 0.045659 | optimized graph |
| C++ OpenFst bytes | 0.089612 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/large/30-product-restricted-by-one-first-level-prefix-10000-x-10000-product-10000-result-paths/bytes-interned.rs) | 1.383340 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/large/30-product-restricted-by-one-first-level-prefix-10000-x-10000-product-10000-result-paths/bytes-strings.rs) | 2.731407 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/large/30-product-restricted-by-one-first-level-prefix-10000-x-10000-product-10000-result-paths/bits-bytes.rs) | 4.798868 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 3.348 | 372.816 |  |
| C++ OpenFst bits | 2.983 | 398.606 |  |
| C++ OpenFst bytes | 2.862 | 3.367 |  |
| Rust PathMap bytes-interned | 2.501 | 1.731 | 3 |
| Rust PathMap bytes-strings | 3.640 | 1.718 | 3 |
| Rust PathMap bits-bytes | 2.727 | 1.717 | 3 |

## product restricted by one first-level prefix — 30000 x 30000 product, 30000 result paths

Result: **30,000 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 11.040 | symbol trie |
| evalZ | 0.015 | zipper evaluator |
| C++ OpenFst symbols | 5.845197 | optimized graph |
| C++ OpenFst bits | 0.053720 | optimized graph |
| C++ OpenFst bytes | 0.111908 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/large/31-product-restricted-by-one-first-level-prefix-30000-x-30000-product-30000-result-paths/bytes-interned.rs) | 4.167381 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/large/31-product-restricted-by-one-first-level-prefix-30000-x-30000-product-30000-result-paths/bytes-strings.rs) | 8.494472 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/large/31-product-restricted-by-one-first-level-prefix-30000-x-30000-product-30000-result-paths/bits-bytes.rs) | 15.839269 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 2.364 | 2.417 |  |
| C++ OpenFst bits | 1.651 | 2.517 |  |
| C++ OpenFst bytes | 1.361 | 2.457 |  |
| Rust PathMap bytes-interned | 1.985 | 1.992 | 3 |
| Rust PathMap bytes-strings | 4.294 | 3.209 | 3 |
| Rust PathMap bits-bytes | 1.541 | 1.801 | 3 |
