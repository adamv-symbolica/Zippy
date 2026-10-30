# N-queens

[All examples](README.md) · Milliseconds per evaluation.

## n-queens — MORKL 8x8 source

Result: **92 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 639.345 | reference path sets |
| ROG exec | 0.003 | compiled away; use compile+run |
| evalTrie | 1767.540 | symbol trie |
| evalZ | 49227.311569 | zipper evaluator |
| ROG execT | 0.001 | compiled away; use compile+run |
| Zippy_bits reference | 1969.337224 | independent set representation |
| Zippy_bytes reference | 6251.526215 | independent set representation |
| C++ OpenFst symbols | 0.000007 | precomputed literal |
| C++ OpenFst bits | 879.570230 | optimized graph |
| C++ OpenFst bytes | 618.539135 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/programs/21-n-queens-morkl-8x8-source/bytes-interned.rs) | 38.642314 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/programs/21-n-queens-morkl-8x8-source/bytes-strings.rs) | 56.047651 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/programs/21-n-queens-morkl-8x8-source/bits-bytes.rs) | 355.721394 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 463.813 | 468.972 |  |
| C++ OpenFst bits | 966.646 | 49513.513 |  |
| C++ OpenFst bytes | 61.570 | 3597.493 |  |
| Rust PathMap bytes-interned | 82.973 | 2.055 | 3 |
| Rust PathMap bytes-strings | 135.238 | 2.586 | 3 |
| Rust PathMap bits-bytes | 745.681 | 7.433 | 0 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 374.853 | 374.853 | 374.854 |

## n-queens — MORKL 8x8 compile-pass

Result: **92 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.001 | reference path sets |
| ROG exec | 0.001 | compiled away; use compile+run |
| evalTrie | 0.049 | symbol trie |
| evalZ | 0.054 | zipper evaluator |
| ROG execT | 0.001 | compiled away; use compile+run |
| Zippy_bits reference | 0.004046 | independent set representation |
| Zippy_bytes reference | 0.003766 | independent set representation |
| C++ OpenFst symbols | 0.000006 | precomputed literal |
| C++ OpenFst bits | 0.000008 | precomputed literal |
| C++ OpenFst bytes | 0.000006 | precomputed literal |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/programs/22-n-queens-morkl-8x8-compile-pass/bytes-interned.rs) | 0.052123 | precomputed literal |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/programs/22-n-queens-morkl-8x8-compile-pass/bytes-strings.rs) | 0.029874 | precomputed literal |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/programs/22-n-queens-morkl-8x8-compile-pass/bits-bytes.rs) | 0.063179 | precomputed literal |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 0.591 | 2.815 |  |
| C++ OpenFst bits | 1.775 | 447.129 |  |
| C++ OpenFst bytes | 1.701 | 457.178 |  |
| Rust PathMap bytes-interned | 1.962 | 1.605 | 3 |
| Rust PathMap bytes-strings | 1.343 | 1.342 | 3 |
| Rust PathMap bits-bytes | 1.900 | 0.984 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | 479.732 | 0.378 | 480.110 | 480.111 |

Independent bitboard reference from the original report (not a Zippy evaluator):

| Benchmark | Variant | Result | Runtime ms |
| --- | --- | --- | --- |
| n-queens bit reference | n=8 | 92 | 0.031 |
| n-queens bit reference | n=9 | 352 | 0.042 |
| n-queens bit reference | n=10 | 724 | 0.280 |
| n-queens bit reference | n=11 | 2680 | 1.371 |
| n-queens bit reference | n=12 | 14200 | 5.261 |
