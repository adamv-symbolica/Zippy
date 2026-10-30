# N-queens

[All examples](README.md) · Milliseconds per evaluation.

## n-queens — MORKL 8x8 source

Result: **92 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 679.901 | reference path sets |
| ROG exec | 0.004 | compiled away; use compile+run |
| evalTrie | 2255.114 | symbol trie |
| evalZ | 45229.384059 | zipper evaluator |
| ROG execT | 0.002 | compiled away; use compile+run |
| Zippy_bits reference | 1669.551700 | independent set representation |
| Zippy_bytes reference | 6163.870405 | independent set representation |
| C++ OpenFst symbols | 0.000006 | precomputed literal |
| C++ OpenFst bits | 738.916389 | optimized graph |
| C++ OpenFst bytes | 466.847544 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/programs/21-n-queens-morkl-8x8-source/bytes-interned.rs) | 38.793230 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/programs/21-n-queens-morkl-8x8-source/bytes-strings.rs) | 56.795353 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/programs/21-n-queens-morkl-8x8-source/bits-bytes.rs) | 218.174593 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 365.225 | 328.069 |  |
| C++ OpenFst bits | 654.633 | 39314.849 |  |
| C++ OpenFst bytes | 43.354 | 2712.627 |  |
| Rust PathMap bytes-interned | 45.710 | 4.055 | 3 |
| Rust PathMap bytes-strings | 256.095 | 11.776 | 3 |
| Rust PathMap bits-bytes | 1165.870 | 119.634 | 0 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 443.373 | 443.373 | 443.375 |

## n-queens — MORKL 8x8 compile-pass

Result: **92 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.001 | reference path sets |
| ROG exec | 0.002 | compiled away; use compile+run |
| evalTrie | 0.125 | symbol trie |
| evalZ | 0.112 | zipper evaluator |
| ROG execT | 0.002 | compiled away; use compile+run |
| Zippy_bits reference | 0.003546 | independent set representation |
| Zippy_bytes reference | 0.003335 | independent set representation |
| C++ OpenFst symbols | 0.000006 | precomputed literal |
| C++ OpenFst bits | 0.000006 | precomputed literal |
| C++ OpenFst bytes | 0.000006 | precomputed literal |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/programs/22-n-queens-morkl-8x8-compile-pass/bytes-interned.rs) | 0.062317 | precomputed literal |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/programs/22-n-queens-morkl-8x8-compile-pass/bytes-strings.rs) | 0.039318 | precomputed literal |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/programs/22-n-queens-morkl-8x8-compile-pass/bits-bytes.rs) | 0.078595 | precomputed literal |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 0.414 | 1.386 |  |
| C++ OpenFst bits | 1.316 | 333.580 |  |
| C++ OpenFst bytes | 1.395 | 330.714 |  |
| Rust PathMap bytes-interned | 1.862 | 1.510 | 3 |
| Rust PathMap bytes-strings | 0.998 | 0.874 | 3 |
| Rust PathMap bits-bytes | 1.361 | 1.368 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | 494.671 | 1.009 | 495.681 | 495.682 |

Independent bitboard reference from the original report (not a Zippy evaluator):

| Benchmark | Variant | Result | Runtime ms |
| --- | --- | --- | --- |
| n-queens bit reference | n=8 | 92 | 0.106 |
| n-queens bit reference | n=9 | 352 | 0.043 |
| n-queens bit reference | n=10 | 724 | 0.238 |
| n-queens bit reference | n=11 | 2680 | 0.992 |
| n-queens bit reference | n=12 | 14200 | 5.231 |
