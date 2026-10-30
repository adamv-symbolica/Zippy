# Sliding puzzle

[All examples](README.md) · Milliseconds per evaluation.

## sliding puzzle — 2x2 pure source full frontier step

Result: **12 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 1.963 | reference path sets |
| ROG exec | 0.001 | compiled away; use compile+run |
| evalTrie | 4.152 | symbol trie |
| evalZ | 6.993 | zipper evaluator |
| ROG execT | 0.001 | compiled away; use compile+run |
| Zippy_bits reference | 107.391408 | independent set representation |
| Zippy_bytes reference | 35.219229 | independent set representation |
| C++ OpenFst symbols | 0.000006 | precomputed literal |
| C++ OpenFst bits | 21.203495 | optimized graph |
| C++ OpenFst bytes | 7.736415 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/programs/16-sliding-puzzle-2x2-pure-source-full-frontier-step/bytes-interned.rs) | 1.486019 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/programs/16-sliding-puzzle-2x2-pure-source-full-frontier-step/bytes-strings.rs) | 2.832231 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/programs/16-sliding-puzzle-2x2-pure-source-full-frontier-step/bits-bytes.rs) | 6.079499 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 3.374 | 4.532 |  |
| C++ OpenFst bits | 436.141 | 23972.029 |  |
| C++ OpenFst bytes | 24.530 | 2268.411 |  |
| Rust PathMap bytes-interned | 26.245 | 1.881 | 3 |
| Rust PathMap bytes-strings | 77.781 | 3.352 | 3 |
| Rust PathMap bits-bytes | 504.525 | 7.900 | 1 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 2.846 | 2.846 | 2.847 |

## sliding puzzle — 2x2 pure compile-pass full frontier step

Result: **12 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.001 | reference path sets |
| ROG exec | 0.001 | compiled away; use compile+run |
| evalTrie | 0.006 | symbol trie |
| evalZ | 0.006 | zipper evaluator |
| ROG execT | 0.001 | compiled away; use compile+run |
| Zippy_bits reference | 0.004146 | independent set representation |
| Zippy_bytes reference | 0.004336 | independent set representation |
| C++ OpenFst symbols | 0.000006 | precomputed literal |
| C++ OpenFst bits | 0.000006 | precomputed literal |
| C++ OpenFst bytes | 0.000006 | precomputed literal |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/programs/17-sliding-puzzle-2x2-pure-compile-pass-full-frontier-step/bytes-interned.rs) | 0.003486 | precomputed literal |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/programs/17-sliding-puzzle-2x2-pure-compile-pass-full-frontier-step/bytes-strings.rs) | 0.002190 | precomputed literal |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/programs/17-sliding-puzzle-2x2-pure-compile-pass-full-frontier-step/bits-bytes.rs) | 0.004172 | precomputed literal |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 0.478 | 451.262 |  |
| C++ OpenFst bits | 0.726 | 458.111 |  |
| C++ OpenFst bytes | 0.742 | 451.448 |  |
| Rust PathMap bytes-interned | 0.418 | 1.038 | 3 |
| Rust PathMap bytes-strings | 0.286 | 0.644 | 3 |
| Rust PathMap bits-bytes | 0.963 | 1.425 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | 13.355 | 0.332 | 13.687 | 13.688 |

## sliding puzzle — 3x3 pure source depth-8 step

Result: **420 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 385.872 | reference path sets |
| ROG exec | 0.002 | compiled away; use compile+run |
| evalTrie | 686.239 | symbol trie |
| evalZ | 2310.671998 | zipper evaluator |
| ROG execT | 0.001 | compiled away; use compile+run |
| Zippy_bits reference | 9609.610475 | independent set representation |
| Zippy_bytes reference | 4914.662614 | independent set representation |
| C++ OpenFst symbols | 0.000007 | precomputed literal |
| C++ OpenFst bits | 3247.786472 | optimized graph |
| C++ OpenFst bytes | 509.204674 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/programs/18-sliding-puzzle-3x3-pure-source-depth-8-step/bytes-interned.rs) | 149.963776 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/programs/18-sliding-puzzle-3x3-pure-source-depth-8-step/bytes-strings.rs) | 589.795706 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/programs/18-sliding-puzzle-3x3-pure-source-depth-8-step/bits-bytes.rs) | 4099.917834 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 54.317 | 428.689 |  |
| C++ OpenFst bits | 3590.272 | 20295.444 |  |
| C++ OpenFst bytes | 174.970 | 6209.862 |  |
| Rust PathMap bytes-interned | 310.131 | 2.888 | 3 |
| Rust PathMap bytes-strings | 289.960 | 3.750 | 1 |
| Rust PathMap bits-bytes | 2423.030 | 19.465 | 0 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 36.307 | 36.307 | 36.308 |

## sliding puzzle — 3x3 pure compile-pass depth-8 step

Result: **420 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.001 | reference path sets |
| ROG exec | 0.001 | compiled away; use compile+run |
| evalTrie | 0.300 | symbol trie |
| evalZ | 0.429 | zipper evaluator |
| ROG execT | 0.001 | compiled away; use compile+run |
| Zippy_bits reference | 0.006550 | independent set representation |
| Zippy_bytes reference | 0.018298 | independent set representation |
| C++ OpenFst symbols | 0.000007 | precomputed literal |
| C++ OpenFst bits | 0.000007 | precomputed literal |
| C++ OpenFst bytes | 0.000007 | precomputed literal |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/programs/19-sliding-puzzle-3x3-pure-compile-pass-depth-8-step/bytes-interned.rs) | 0.211383 | precomputed literal |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/programs/19-sliding-puzzle-3x3-pure-compile-pass-depth-8-step/bytes-strings.rs) | 0.127341 | precomputed literal |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/programs/19-sliding-puzzle-3x3-pure-compile-pass-depth-8-step/bits-bytes.rs) | 0.311811 | precomputed literal |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 0.974 | 5.761 |  |
| C++ OpenFst bits | 8.077 | 433.005 |  |
| C++ OpenFst bytes | 6.496 | 465.664 |  |
| Rust PathMap bytes-interned | 6.760 | 1.595 | 3 |
| Rust PathMap bytes-strings | 4.307 | 0.985 | 3 |
| Rust PathMap bits-bytes | 7.537 | 1.388 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | 113.265 | 0.386 | 113.651 | 113.652 |

## sliding puzzle — 4x4 pure source depth-5 step

Result: **202 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 538.834 | reference path sets |
| ROG exec | 0.029 | compiled away; use compile+run |
| evalTrie | 1222.265 | symbol trie |
| evalZ | 8635.440986 | zipper evaluator |
| ROG execT | 0.008 | compiled away; use compile+run |
| Zippy_bits reference | 13265.155538 | independent set representation |
| Zippy_bytes reference | 5414.382366 | independent set representation |
| C++ OpenFst symbols | 0.000006 | precomputed literal |
| C++ OpenFst bits | 3892.308882 | optimized graph |
| C++ OpenFst bytes | 742.547758 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/programs/20-sliding-puzzle-4x4-pure-source-depth-5-step/bytes-interned.rs) | 249.723597 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/programs/20-sliding-puzzle-4x4-pure-source-depth-5-step/bytes-strings.rs) | 2527.328804 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/programs/20-sliding-puzzle-4x4-pure-source-depth-5-step/bits-bytes.rs) | 5872.393145 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 89.817 | 455.469 |  |
| C++ OpenFst bits | 34457.951 | 65844.510 |  |
| C++ OpenFst bytes | 294.763 | 15490.691 |  |
| Rust PathMap bytes-interned | 430.473 | 5.395 | 1 |
| Rust PathMap bytes-strings | 730.442 | 9.018 | 0 |
| Rust PathMap bits-bytes | 14964.807 | 95.824 | 0 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 61.568 | 61.568 | 61.575 |
