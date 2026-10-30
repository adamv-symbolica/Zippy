# Game of Life

[All examples](README.md) · Milliseconds per evaluation.

## life — reference random 24x24

Result: **125 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 27.328 | reference path sets |
| ROG exec | 23.199 | exec fallback Call dispatch to optimized callee graph(s): neigh |
| evalTrie | 387.738 | symbol trie |
| evalZ | 471.878 | zipper evaluator |
| ROG execT | 8.971 | exec fallback Call dispatch to optimized callee graph(s): neigh |
| Zippy_bits reference | 1210.373084 | independent set representation |
| Zippy_bytes reference | 1359.321127 | independent set representation |
| C++ OpenFst symbols | 6.625699 | optimized graph |
| C++ OpenFst bits | 195.714761 | optimized graph |
| C++ OpenFst bytes | 74.116655 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/programs/11-life-reference-random-24x24/bytes-interned.rs) | 44.773540 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/programs/11-life-reference-random-24x24/bytes-strings.rs) | 5.896274 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/programs/11-life-reference-random-24x24/bits-bytes.rs) | 84.447742 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 3764.613 | 21222.637 |  |
| C++ OpenFst bits | 912.225 | 21798.194 |  |
| C++ OpenFst bytes | 449.125 | 1789.911 |  |
| Rust PathMap bytes-interned | 278.918 | 5.809 | 3 |
| Rust PathMap bytes-strings | 233.559 | 2.326 | 3 |
| Rust PathMap bits-bytes | 447.386 | 4.723 | 1 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 3043.051 | 3043.051 | 3052.021 |

## life — compile-pass random 24x24

Result: **125 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 24.541 | reference path sets |
| ROG exec | 0.002 | compiled away; use compile+run |
| evalTrie | 297.085 | symbol trie |
| evalZ | 274.311 | zipper evaluator |
| ROG execT | 0.001 | compiled away; use compile+run |
| Zippy_bits reference | 821.311782 | independent set representation |
| Zippy_bytes reference | 950.212322 | independent set representation |
| C++ OpenFst symbols | 0.000007 | precomputed literal |
| C++ OpenFst bits | 160.934301 | optimized graph |
| C++ OpenFst bytes | 77.345588 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/programs/12-life-compile-pass-random-24x24/bytes-interned.rs) | 44.133139 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/programs/12-life-compile-pass-random-24x24/bytes-strings.rs) | 5.245108 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/programs/12-life-compile-pass-random-24x24/bits-bytes.rs) | 63.558047 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 27.653 | 463.089 |  |
| C++ OpenFst bits | 1350.721 | 16801.796 |  |
| C++ OpenFst bytes | 260.199 | 1733.518 |  |
| Rust PathMap bytes-interned | 281.085 | 2.700 | 3 |
| Rust PathMap bytes-strings | 237.523 | 1.948 | 3 |
| Rust PathMap bits-bytes | 621.476 | 4.414 | 1 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | 92.627 | 18.439 | 111.067 | 111.067 |

## life — compile-pass random 24x24 initial literal

Result: **125 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.000 | reference path sets |
| ROG exec | 0.001 | compiled away; use compile+run |
| evalTrie | 0.048 | symbol trie |
| evalZ | 0.055 | zipper evaluator |
| ROG execT | 0.001 | compiled away; use compile+run |
| Zippy_bits reference | 0.006059 | independent set representation |
| Zippy_bytes reference | 0.018588 | independent set representation |
| C++ OpenFst symbols | 0.000006 | precomputed literal |
| C++ OpenFst bits | 0.000007 | precomputed literal |
| C++ OpenFst bytes | 0.000006 | precomputed literal |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/programs/13-life-compile-pass-random-24x24-initial-literal/bytes-interned.rs) | 0.004796 | precomputed literal |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/programs/13-life-compile-pass-random-24x24-initial-literal/bytes-strings.rs) | 0.008148 | precomputed literal |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/programs/13-life-compile-pass-random-24x24-initial-literal/bits-bytes.rs) | 0.017989 | precomputed literal |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 0.945 | 2.118 |  |
| C++ OpenFst bits | 2.912 | 449.022 |  |
| C++ OpenFst bytes | 1.594 | 431.451 |  |
| Rust PathMap bytes-interned | 3.263 | 2.197 | 3 |
| Rust PathMap bytes-strings | 2.250 | 1.754 | 3 |
| Rust PathMap bits-bytes | 1.519 | 1.780 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | 86.100 | 0.414 | 86.514 | 86.514 |
