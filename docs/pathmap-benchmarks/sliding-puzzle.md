# Sliding puzzle

[All examples](README.md) · Milliseconds per evaluation.

## sliding puzzle — 2x2 pure source full frontier step

Result: **12 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 1.861 | reference path sets |
| ROG exec | 0.001 | compiled away; use compile+run |
| evalTrie | 4.305 | symbol trie |
| evalZ | 6.144 | zipper evaluator |
| ROG execT | 0.001 | compiled away; use compile+run |
| Zippy_bits reference | 47.461503 | independent set representation |
| Zippy_bytes reference | 28.004447 | independent set representation |
| C++ OpenFst symbols | 0.000006 | precomputed literal |
| C++ OpenFst bits | 16.613301 | optimized graph |
| C++ OpenFst bytes | 6.872508 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/programs/16-sliding-puzzle-2x2-pure-source-full-frontier-step/bytes-interned.rs) | 1.482482 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/programs/16-sliding-puzzle-2x2-pure-source-full-frontier-step/bytes-strings.rs) | 3.191918 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/programs/16-sliding-puzzle-2x2-pure-source-full-frontier-step/bits-bytes.rs) | 15.944197 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 2.877 | 329.536 |  |
| C++ OpenFst bits | 293.018 | 18751.756 |  |
| C++ OpenFst bytes | 25.113 | 1549.153 |  |
| Rust PathMap bytes-interned | 30.105 | 3.295 | 3 |
| Rust PathMap bytes-strings | 112.334 | 6.442 | 3 |
| Rust PathMap bits-bytes | 466.027 | 53.604 | 0 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 2.841 | 2.841 | 2.841 |

## sliding puzzle — 2x2 pure compile-pass full frontier step

Result: **12 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.001 | reference path sets |
| ROG exec | 0.001 | compiled away; use compile+run |
| evalTrie | 0.007 | symbol trie |
| evalZ | 0.008 | zipper evaluator |
| ROG execT | 0.001 | compiled away; use compile+run |
| Zippy_bits reference | 0.003134 | independent set representation |
| Zippy_bytes reference | 0.003616 | independent set representation |
| C++ OpenFst symbols | 0.000006 | precomputed literal |
| C++ OpenFst bits | 0.000006 | precomputed literal |
| C++ OpenFst bytes | 0.000006 | precomputed literal |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/programs/17-sliding-puzzle-2x2-pure-compile-pass-full-frontier-step/bytes-interned.rs) | 0.004152 | precomputed literal |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/programs/17-sliding-puzzle-2x2-pure-compile-pass-full-frontier-step/bytes-strings.rs) | 0.002768 | precomputed literal |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/programs/17-sliding-puzzle-2x2-pure-compile-pass-full-frontier-step/bits-bytes.rs) | 0.004955 | precomputed literal |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 0.439 | 1.615 |  |
| C++ OpenFst bits | 0.579 | 333.805 |  |
| C++ OpenFst bytes | 0.521 | 329.837 |  |
| Rust PathMap bytes-interned | 0.628 | 1.336 | 3 |
| Rust PathMap bytes-strings | 0.360 | 0.681 | 3 |
| Rust PathMap bits-bytes | 0.651 | 0.945 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | 8.933 | 0.353 | 9.286 | 9.286 |

## sliding puzzle — 3x3 pure source depth-8 step

Result: **420 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 522.414 | reference path sets |
| ROG exec | 0.006 | compiled away; use compile+run |
| evalTrie | 1184.658 | symbol trie |
| evalZ | 2705.873738 | zipper evaluator |
| ROG execT | 0.003 | compiled away; use compile+run |
| Zippy_bits reference | 7638.233061 | independent set representation |
| Zippy_bytes reference | 3698.101949 | independent set representation |
| C++ OpenFst symbols | 0.000006 | precomputed literal |
| C++ OpenFst bits | 2897.656884 | optimized graph |
| C++ OpenFst bytes | 397.781926 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/programs/18-sliding-puzzle-3x3-pure-source-depth-8-step/bytes-interned.rs) | 157.048614 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/programs/18-sliding-puzzle-3x3-pure-source-depth-8-step/bytes-strings.rs) | 572.809618 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/programs/18-sliding-puzzle-3x3-pure-source-depth-8-step/bits-bytes.rs) | 2898.093256 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 35.745 | 331.278 |  |
| C++ OpenFst bits | 2032.748 | 15587.068 |  |
| C++ OpenFst bytes | 139.611 | 4565.981 |  |
| Rust PathMap bytes-interned | 251.697 | 8.128 | 3 |
| Rust PathMap bytes-strings | 380.491 | 30.251 | 1 |
| Rust PathMap bits-bytes | 3526.863 | 331.462 | 0 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 35.837 | 35.837 | 35.841 |

## sliding puzzle — 3x3 pure compile-pass depth-8 step

Result: **420 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.004 | reference path sets |
| ROG exec | 0.001 | compiled away; use compile+run |
| evalTrie | 0.947 | symbol trie |
| evalZ | 1.177 | zipper evaluator |
| ROG execT | 0.001 | compiled away; use compile+run |
| Zippy_bits reference | 0.003355 | independent set representation |
| Zippy_bytes reference | 0.003265 | independent set representation |
| C++ OpenFst symbols | 0.000006 | precomputed literal |
| C++ OpenFst bits | 0.000006 | precomputed literal |
| C++ OpenFst bytes | 0.000006 | precomputed literal |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/programs/19-sliding-puzzle-3x3-pure-compile-pass-depth-8-step/bytes-interned.rs) | 0.248726 | precomputed literal |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/programs/19-sliding-puzzle-3x3-pure-compile-pass-depth-8-step/bytes-strings.rs) | 0.158677 | precomputed literal |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/programs/19-sliding-puzzle-3x3-pure-compile-pass-depth-8-step/bits-bytes.rs) | 0.381019 | precomputed literal |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 0.886 | 1.172 |  |
| C++ OpenFst bits | 6.762 | 327.659 |  |
| C++ OpenFst bytes | 5.816 | 327.222 |  |
| Rust PathMap bytes-interned | 4.905 | 2.512 | 3 |
| Rust PathMap bytes-strings | 3.022 | 1.588 | 3 |
| Rust PathMap bits-bytes | 7.087 | 2.889 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | 98.273 | 2.527 | 100.799 | 100.800 |

## sliding puzzle — 4x4 pure source depth-5 step

Result: **202 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 860.387 | reference path sets |
| ROG exec | 0.050 | compiled away; use compile+run |
| evalTrie | 1460.891 | symbol trie |
| evalZ | 5372.772964 | zipper evaluator |
| ROG execT | 0.017 | compiled away; use compile+run |
| Zippy_bits reference | 12484.109963 | independent set representation |
| Zippy_bytes reference | 5619.033909 | independent set representation |
| C++ OpenFst symbols | 0.000006 | precomputed literal |
| C++ OpenFst bits | 3704.032642 | optimized graph |
| C++ OpenFst bytes | 724.821738 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/programs/20-sliding-puzzle-4x4-pure-source-depth-5-step/bytes-interned.rs) | 226.026411 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/programs/20-sliding-puzzle-4x4-pure-source-depth-5-step/bytes-strings.rs) | 1641.114850 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/programs/20-sliding-puzzle-4x4-pure-source-depth-5-step/bits-bytes.rs) | 4159.592931 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 54.348 | 334.217 |  |
| C++ OpenFst bits | 14137.419 | 57342.026 |  |
| C++ OpenFst bytes | 265.549 | 14416.933 |  |
| Rust PathMap bytes-interned | 330.677 | 26.912 | 1 |
| Rust PathMap bytes-strings | 1364.522 | 94.219 | 0 |
| Rust PathMap bits-bytes | 23344.662 | 1347.997 | 0 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 61.587 | 61.587 | 61.605 |
