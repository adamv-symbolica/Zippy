# Sliding puzzle

[All examples](README.md) · Milliseconds per evaluation.

## sliding puzzle — 2x2 pure source full frontier step

Result: **12 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 1.780 | reference path sets |
| ROG exec | 0.001 | compiled away; use compile+run |
| evalTrie | 4.451 | symbol trie |
| evalZ | 6.310 | zipper evaluator |
| ROG execT | 0.001 | compiled away; use compile+run |
| Zippy_bits reference | 48.138688 | independent set representation |
| Zippy_bytes reference | 25.962524 | independent set representation |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/programs/16-sliding-puzzle-2x2-pure-source-full-frontier-step/symbols.cpp) | 0.000006 | precomputed literal |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/programs/16-sliding-puzzle-2x2-pure-source-full-frontier-step/bits.cpp) | 17.777056 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/programs/16-sliding-puzzle-2x2-pure-source-full-frontier-step/bytes.cpp) | 6.842514 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/programs/16-sliding-puzzle-2x2-pure-source-full-frontier-step/bytes-interned.rs) | 0.972796 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/programs/16-sliding-puzzle-2x2-pure-source-full-frontier-step/bytes-strings.rs) | 3.086924 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/programs/16-sliding-puzzle-2x2-pure-source-full-frontier-step/bytes-length.rs) | 3.082967 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/programs/16-sliding-puzzle-2x2-pure-source-full-frontier-step/bits-bytes.rs) | 14.271958 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 3.822 | 1.866 |  |
| C++ OpenFst bits | 373.194 | 21615.681 |  |
| C++ OpenFst bytes | 20.381 | 1978.130 |  |
| Rust PathMap bytes-interned | 28.710 | 3518.647 | 3 |
| Rust PathMap bytes-strings | 117.837 | 7883.475 | 3 |
| Rust PathMap bytes-length | 1895.421 | 7429.645 | 3 |
| Rust PathMap bits-bytes | 891.427 | 4700.709 | 0 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 2.882 | 2.882 | 2.883 |

## sliding puzzle — 2x2 pure compile-pass full frontier step

Result: **12 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.001 | reference path sets |
| ROG exec | 0.001 | compiled away; use compile+run |
| evalTrie | 0.006 | symbol trie |
| evalZ | 0.007 | zipper evaluator |
| ROG execT | 0.001 | compiled away; use compile+run |
| Zippy_bits reference | 0.003095 | independent set representation |
| Zippy_bytes reference | 0.003355 | independent set representation |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/programs/17-sliding-puzzle-2x2-pure-compile-pass-full-frontier-step/symbols.cpp) | 0.000006 | precomputed literal |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/programs/17-sliding-puzzle-2x2-pure-compile-pass-full-frontier-step/bits.cpp) | 0.000006 | precomputed literal |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/programs/17-sliding-puzzle-2x2-pure-compile-pass-full-frontier-step/bytes.cpp) | 0.000006 | precomputed literal |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/programs/17-sliding-puzzle-2x2-pure-compile-pass-full-frontier-step/bytes-interned.rs) | 0.000147 | precomputed literal |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/programs/17-sliding-puzzle-2x2-pure-compile-pass-full-frontier-step/bytes-strings.rs) | 0.000122 | precomputed literal |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/programs/17-sliding-puzzle-2x2-pure-compile-pass-full-frontier-step/bytes-length.rs) | 0.000132 | precomputed literal |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/programs/17-sliding-puzzle-2x2-pure-compile-pass-full-frontier-step/bits-bytes.rs) | 0.000085 | precomputed literal |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 0.724 | 564.056 |  |
| C++ OpenFst bits | 1.192 | 439.486 |  |
| C++ OpenFst bytes | 1.169 | 444.007 |  |
| Rust PathMap bytes-interned | 0.740 | 92.131 | 3 |
| Rust PathMap bytes-strings | 0.782 | 91.857 | 3 |
| Rust PathMap bytes-length | 0.700 | 89.566 | 3 |
| Rust PathMap bits-bytes | 0.778 | 91.428 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | 9.334 | 0.364 | 9.697 | 9.698 |

## sliding puzzle — 3x3 pure source depth-8 step

Result: **420 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 325.267 | reference path sets |
| ROG exec | 0.002 | compiled away; use compile+run |
| evalTrie | 601.496 | symbol trie |
| evalZ | 2092.114634 | zipper evaluator |
| ROG execT | 0.001 | compiled away; use compile+run |
| Zippy_bits reference | 7347.974199 | independent set representation |
| Zippy_bytes reference | 3504.745862 | independent set representation |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/programs/18-sliding-puzzle-3x3-pure-source-depth-8-step/symbols.cpp) | 0.000006 | precomputed literal |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/programs/18-sliding-puzzle-3x3-pure-source-depth-8-step/bits.cpp) | 3034.619101 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/programs/18-sliding-puzzle-3x3-pure-source-depth-8-step/bytes.cpp) | 494.782504 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/programs/18-sliding-puzzle-3x3-pure-source-depth-8-step/bytes-interned.rs) | 121.929714 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/programs/18-sliding-puzzle-3x3-pure-source-depth-8-step/bytes-strings.rs) | 537.861243 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/programs/18-sliding-puzzle-3x3-pure-source-depth-8-step/bytes-length.rs) | 598.328484 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/programs/18-sliding-puzzle-3x3-pure-source-depth-8-step/bits-bytes.rs) | 2660.452952 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 54.381 | 524.250 |  |
| C++ OpenFst bits | 2984.910 | 20000.739 |  |
| C++ OpenFst bytes | 152.836 | 6043.492 |  |
| Rust PathMap bytes-interned | 184.244 | 7566.760 | 3 |
| Rust PathMap bytes-strings | 396.513 | 14278.651 | 1 |
| Rust PathMap bytes-length | 6445.793 | 9099.538 | 1 |
| Rust PathMap bits-bytes | 3876.384 | 26902.499 | 0 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 34.644 | 34.644 | 34.645 |

## sliding puzzle — 3x3 pure compile-pass depth-8 step

Result: **420 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.001 | reference path sets |
| ROG exec | 0.001 | compiled away; use compile+run |
| evalTrie | 0.259 | symbol trie |
| evalZ | 0.264 | zipper evaluator |
| ROG execT | 0.001 | compiled away; use compile+run |
| Zippy_bits reference | 0.003255 | independent set representation |
| Zippy_bytes reference | 0.003886 | independent set representation |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/programs/19-sliding-puzzle-3x3-pure-compile-pass-depth-8-step/symbols.cpp) | 0.000006 | precomputed literal |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/programs/19-sliding-puzzle-3x3-pure-compile-pass-depth-8-step/bits.cpp) | 0.000007 | precomputed literal |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/programs/19-sliding-puzzle-3x3-pure-compile-pass-depth-8-step/bytes.cpp) | 0.000006 | precomputed literal |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/programs/19-sliding-puzzle-3x3-pure-compile-pass-depth-8-step/bytes-interned.rs) | 0.003981 | precomputed literal |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/programs/19-sliding-puzzle-3x3-pure-compile-pass-depth-8-step/bytes-strings.rs) | 0.003950 | precomputed literal |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/programs/19-sliding-puzzle-3x3-pure-compile-pass-depth-8-step/bytes-length.rs) | 0.003913 | precomputed literal |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/programs/19-sliding-puzzle-3x3-pure-compile-pass-depth-8-step/bits-bytes.rs) | 0.002470 | precomputed literal |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 0.823 | 1.672 |  |
| C++ OpenFst bits | 7.863 | 397.169 |  |
| C++ OpenFst bytes | 7.399 | 378.751 |  |
| Rust PathMap bytes-interned | 5.101 | 93.074 | 3 |
| Rust PathMap bytes-strings | 3.799 | 90.411 | 3 |
| Rust PathMap bytes-length | 3.879 | 91.254 | 3 |
| Rust PathMap bits-bytes | 5.807 | 89.587 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | 84.091 | 0.349 | 84.441 | 84.442 |

## sliding puzzle — 4x4 pure source depth-5 step

Result: **202 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 546.082 | reference path sets |
| ROG exec | 0.032 | compiled away; use compile+run |
| evalTrie | 1443.198 | symbol trie |
| evalZ | 4222.271933 | zipper evaluator |
| ROG execT | 0.009 | compiled away; use compile+run |
| Zippy_bits reference | 12888.927149 | independent set representation |
| Zippy_bytes reference | 5002.568738 | independent set representation |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/programs/20-sliding-puzzle-4x4-pure-source-depth-5-step/symbols.cpp) | 0.000006 | precomputed literal |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/programs/20-sliding-puzzle-4x4-pure-source-depth-5-step/bits.cpp) | 3812.122566 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/programs/20-sliding-puzzle-4x4-pure-source-depth-5-step/bytes.cpp) | 739.494923 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/programs/20-sliding-puzzle-4x4-pure-source-depth-5-step/bytes-interned.rs) | 150.025128 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/programs/20-sliding-puzzle-4x4-pure-source-depth-5-step/bytes-strings.rs) | 1550.452440 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/programs/20-sliding-puzzle-4x4-pure-source-depth-5-step/bytes-length.rs) | 1708.964518 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/programs/20-sliding-puzzle-4x4-pure-source-depth-5-step/bits-bytes.rs) | 3683.562857 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 97.661 | 381.752 |  |
| C++ OpenFst bits | 26573.848 | 62301.181 |  |
| C++ OpenFst bytes | 238.082 | 15586.287 |  |
| Rust PathMap bytes-interned | 472.753 | 19815.993 | 3 |
| Rust PathMap bytes-strings | 1394.142 | 7037.374 | 0 |
| Rust PathMap bytes-length | 13759.172 | 4199.371 | 0 |
| Rust PathMap bits-bytes | 23563.941 | 87783.283 | 0 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 55.468 | 55.468 | 55.476 |
