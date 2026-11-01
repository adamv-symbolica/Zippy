# Game of Life

[All examples](README.md) · Milliseconds per evaluation.

## life — reference random 24x24

Result: **125 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 27.447 | reference path sets |
| ROG exec | 16.750 | exec fallback Call dispatch to optimized callee graph(s): neigh |
| evalTrie | 475.318 | symbol trie |
| evalZ | 454.806 | zipper evaluator |
| ROG execT | 6.973 | exec fallback Call dispatch to optimized callee graph(s): neigh |
| Zippy_bits reference | 855.724137 | independent set representation |
| Zippy_bytes reference | 852.390306 | independent set representation |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/programs/11-life-reference-random-24x24/symbols.cpp) | 6.340480 | optimized graph |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/programs/11-life-reference-random-24x24/bits.cpp) | 186.368943 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/programs/11-life-reference-random-24x24/bytes.cpp) | 68.303886 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/programs/11-life-reference-random-24x24/bytes-interned.rs) | 45.039129 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/programs/11-life-reference-random-24x24/bytes-strings.rs) | 8.623906 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/programs/11-life-reference-random-24x24/bytes-length.rs) | 14.309644 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/programs/11-life-reference-random-24x24/bits-bytes.rs) | 145.807081 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 4062.694 | 17813.323 |  |
| C++ OpenFst bits | 853.600 | 19383.243 |  |
| C++ OpenFst bytes | 238.194 | 1485.298 |  |
| Rust PathMap bytes-interned | 265.505 | 3612.749 | 3 |
| Rust PathMap bytes-strings | 146.790 | 4800.148 | 3 |
| Rust PathMap bytes-length | 1384.902 | 4922.610 | 3 |
| Rust PathMap bits-bytes | 950.239 | 3739.771 | 0 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 2616.121 | 2616.121 | 2623.094 |

## life — compile-pass random 24x24

Result: **125 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 22.781 | reference path sets |
| ROG exec | 0.002 | compiled away; use compile+run |
| evalTrie | 229.963 | symbol trie |
| evalZ | 283.343 | zipper evaluator |
| ROG execT | 0.001 | compiled away; use compile+run |
| Zippy_bits reference | 616.679310 | independent set representation |
| Zippy_bytes reference | 784.288474 | independent set representation |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/programs/12-life-compile-pass-random-24x24/symbols.cpp) | 0.000006 | precomputed literal |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/programs/12-life-compile-pass-random-24x24/bits.cpp) | 149.175403 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/programs/12-life-compile-pass-random-24x24/bytes.cpp) | 68.920801 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/programs/12-life-compile-pass-random-24x24/bytes-interned.rs) | 45.723035 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/programs/12-life-compile-pass-random-24x24/bytes-strings.rs) | 5.021883 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/programs/12-life-compile-pass-random-24x24/bytes-length.rs) | 12.893360 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/programs/12-life-compile-pass-random-24x24/bits-bytes.rs) | 61.634382 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 17.643 | 398.460 |  |
| C++ OpenFst bits | 1460.263 | 16548.199 |  |
| C++ OpenFst bytes | 228.939 | 1432.727 |  |
| Rust PathMap bytes-interned | 250.182 | 3366.798 | 3 |
| Rust PathMap bytes-strings | 142.219 | 4100.643 | 3 |
| Rust PathMap bytes-length | 1252.798 | 4508.114 | 3 |
| Rust PathMap bits-bytes | 895.027 | 22248.483 | 1 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | 87.555 | 17.379 | 104.934 | 104.935 |

## life — compile-pass random 24x24 initial literal

Result: **125 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.001 | reference path sets |
| ROG exec | 0.001 | compiled away; use compile+run |
| evalTrie | 0.030 | symbol trie |
| evalZ | 0.032 | zipper evaluator |
| ROG execT | 0.001 | compiled away; use compile+run |
| Zippy_bits reference | 0.003335 | independent set representation |
| Zippy_bytes reference | 0.003366 | independent set representation |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/programs/13-life-compile-pass-random-24x24-initial-literal/symbols.cpp) | 0.000006 | precomputed literal |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/programs/13-life-compile-pass-random-24x24-initial-literal/bits.cpp) | 0.000006 | precomputed literal |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/programs/13-life-compile-pass-random-24x24-initial-literal/bytes.cpp) | 0.000006 | precomputed literal |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/programs/13-life-compile-pass-random-24x24-initial-literal/bytes-interned.rs) | 0.000288 | precomputed literal |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/programs/13-life-compile-pass-random-24x24-initial-literal/bytes-strings.rs) | 0.000799 | precomputed literal |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/programs/13-life-compile-pass-random-24x24-initial-literal/bytes-length.rs) | 0.000411 | precomputed literal |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/programs/13-life-compile-pass-random-24x24-initial-literal/bits-bytes.rs) | 0.000536 | precomputed literal |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 0.976 | 1.974 |  |
| C++ OpenFst bits | 3.473 | 402.277 |  |
| C++ OpenFst bytes | 3.059 | 490.153 |  |
| Rust PathMap bytes-interned | 1.657 | 93.499 | 3 |
| Rust PathMap bytes-strings | 4.148 | 89.785 | 3 |
| Rust PathMap bytes-length | 1.536 | 90.951 | 3 |
| Rust PathMap bits-bytes | 1.816 | 90.001 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | 71.652 | 0.359 | 72.011 | 72.011 |
