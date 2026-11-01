# N-queens

[All examples](README.md) · Milliseconds per evaluation.

## n-queens — MORKL 8x8 source

Result: **92 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 525.092 | reference path sets |
| ROG exec | 0.003 | compiled away; use compile+run |
| evalTrie | 2403.647 | symbol trie |
| evalZ | 67191.532551 | zipper evaluator |
| ROG execT | 0.001 | compiled away; use compile+run |
| Zippy_bits reference | 1700.541954 | independent set representation |
| Zippy_bytes reference | 5792.609811 | independent set representation |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/programs/21-n-queens-morkl-8x8-source/symbols.cpp) | 0.000006 | precomputed literal |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/programs/21-n-queens-morkl-8x8-source/bits.cpp) | 823.213140 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/programs/21-n-queens-morkl-8x8-source/bytes.cpp) | 574.314572 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/programs/21-n-queens-morkl-8x8-source/bytes-interned.rs) | 37.285138 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/programs/21-n-queens-morkl-8x8-source/bytes-strings.rs) | 58.312840 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/programs/21-n-queens-morkl-8x8-source/bytes-length.rs) | 45.756395 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/programs/21-n-queens-morkl-8x8-source/bits-bytes.rs) | 211.753761 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 389.493 | 390.046 |  |
| C++ OpenFst bits | 767.196 | 45357.506 |  |
| C++ OpenFst bytes | 55.321 | 3113.296 |  |
| Rust PathMap bytes-interned | 43.787 | 4379.683 | 3 |
| Rust PathMap bytes-strings | 318.952 | 6503.333 | 1 |
| Rust PathMap bytes-length | 291.164 | 9092.394 | 3 |
| Rust PathMap bits-bytes | 1213.202 | 8204.799 | 0 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 442.931 | 442.931 | 442.932 |

## n-queens — MORKL 8x8 compile-pass

Result: **92 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.001 | reference path sets |
| ROG exec | 0.001 | compiled away; use compile+run |
| evalTrie | 0.061 | symbol trie |
| evalZ | 0.064 | zipper evaluator |
| ROG execT | 0.001 | compiled away; use compile+run |
| Zippy_bits reference | 0.003685 | independent set representation |
| Zippy_bytes reference | 0.003786 | independent set representation |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/programs/22-n-queens-morkl-8x8-compile-pass/symbols.cpp) | 0.000006 | precomputed literal |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/programs/22-n-queens-morkl-8x8-compile-pass/bits.cpp) | 0.000006 | precomputed literal |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/programs/22-n-queens-morkl-8x8-compile-pass/bytes.cpp) | 0.000006 | precomputed literal |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/programs/22-n-queens-morkl-8x8-compile-pass/bytes-interned.rs) | 0.000640 | precomputed literal |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/programs/22-n-queens-morkl-8x8-compile-pass/bytes-strings.rs) | 0.000638 | precomputed literal |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/programs/22-n-queens-morkl-8x8-compile-pass/bytes-length.rs) | 0.000664 | precomputed literal |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/programs/22-n-queens-morkl-8x8-compile-pass/bits-bytes.rs) | 0.000466 | precomputed literal |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 0.785 | 2.206 |  |
| C++ OpenFst bits | 1.766 | 439.800 |  |
| C++ OpenFst bytes | 1.628 | 390.294 |  |
| Rust PathMap bytes-interned | 1.786 | 93.360 | 3 |
| Rust PathMap bytes-strings | 1.210 | 88.077 | 3 |
| Rust PathMap bytes-length | 1.033 | 89.401 | 3 |
| Rust PathMap bits-bytes | 1.513 | 88.882 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | 565.305 | 0.440 | 565.745 | 565.746 |

Independent bitboard reference from the original report (not a Zippy evaluator):

| Benchmark | Variant | Result | Runtime ms |
| --- | --- | --- | --- |
| n-queens bit reference | n=8 | 92 | 0.026 |
| n-queens bit reference | n=9 | 352 | 0.042 |
| n-queens bit reference | n=10 | 724 | 0.263 |
| n-queens bit reference | n=11 | 2680 | 0.997 |
| n-queens bit reference | n=12 | 14200 | 5.266 |
