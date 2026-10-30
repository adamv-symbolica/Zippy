# Game of Life

[All examples](README.md) · Milliseconds per evaluation.

## life — reference random 24x24

Result: **125 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 27.201 | reference path sets |
| ROG exec | 16.192 | exec fallback Call dispatch to optimized callee graph(s): neigh |
| evalTrie | 365.170 | symbol trie |
| evalZ | 496.924 | zipper evaluator |
| ROG execT | 6.426 | exec fallback Call dispatch to optimized callee graph(s): neigh |
| Zippy_bits reference | 743.866825 | independent set representation |
| Zippy_bytes reference | 855.531472 | independent set representation |
| C++ OpenFst symbols | 6.205979 | optimized graph |
| C++ OpenFst bits | 182.678890 | optimized graph |
| C++ OpenFst bytes | 70.225368 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/programs/11-life-reference-random-24x24/bytes-interned.rs) | 43.517406 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/programs/11-life-reference-random-24x24/bytes-strings.rs) | 9.077957 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/programs/11-life-reference-random-24x24/bits-bytes.rs) | 147.232039 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 2877.912 | 13637.730 |  |
| C++ OpenFst bits | 421.439 | 16566.172 |  |
| C++ OpenFst bytes | 228.885 | 1295.916 |  |
| Rust PathMap bytes-interned | 293.409 | 3856.859 | 3 |
| Rust PathMap bytes-strings | 177.101 | 4555.060 | 3 |
| Rust PathMap bits-bytes | 792.266 | 3310.813 | 0 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 2992.006 | 2992.006 | 2998.431 |

## life — compile-pass random 24x24

Result: **125 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 23.854 | reference path sets |
| ROG exec | 0.002 | compiled away; use compile+run |
| evalTrie | 392.367 | symbol trie |
| evalZ | 273.223 | zipper evaluator |
| ROG execT | 0.001 | compiled away; use compile+run |
| Zippy_bits reference | 625.772253 | independent set representation |
| Zippy_bytes reference | 803.795988 | independent set representation |
| C++ OpenFst symbols | 0.000006 | precomputed literal |
| C++ OpenFst bits | 144.614573 | optimized graph |
| C++ OpenFst bytes | 71.277156 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/programs/12-life-compile-pass-random-24x24/bytes-interned.rs) | 42.698458 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/programs/12-life-compile-pass-random-24x24/bytes-strings.rs) | 5.007802 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/programs/12-life-compile-pass-random-24x24/bits-bytes.rs) | 56.897256 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 21.305 | 327.428 |  |
| C++ OpenFst bits | 612.688 | 13195.302 |  |
| C++ OpenFst bytes | 281.896 | 1245.194 |  |
| Rust PathMap bytes-interned | 300.746 | 3.399 | 3 |
| Rust PathMap bytes-strings | 205.497 | 3.567 | 3 |
| Rust PathMap bits-bytes | 931.606 | 44.093 | 1 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | 82.323 | 17.758 | 100.081 | 100.082 |

## life — compile-pass random 24x24 initial literal

Result: **125 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.001 | reference path sets |
| ROG exec | 0.001 | compiled away; use compile+run |
| evalTrie | 0.030 | symbol trie |
| evalZ | 0.039 | zipper evaluator |
| ROG execT | 0.001 | compiled away; use compile+run |
| Zippy_bits reference | 0.003445 | independent set representation |
| Zippy_bytes reference | 0.003806 | independent set representation |
| C++ OpenFst symbols | 0.000006 | precomputed literal |
| C++ OpenFst bits | 0.000006 | precomputed literal |
| C++ OpenFst bytes | 0.000006 | precomputed literal |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/programs/13-life-compile-pass-random-24x24-initial-literal/bytes-interned.rs) | 0.006521 | precomputed literal |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/programs/13-life-compile-pass-random-24x24-initial-literal/bytes-strings.rs) | 0.011202 | precomputed literal |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/programs/13-life-compile-pass-random-24x24-initial-literal/bits-bytes.rs) | 0.022895 | precomputed literal |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 0.624 | 1.533 |  |
| C++ OpenFst bits | 1.774 | 338.454 |  |
| C++ OpenFst bytes | 1.587 | 332.794 |  |
| Rust PathMap bytes-interned | 1.798 | 1.517 | 3 |
| Rust PathMap bytes-strings | 1.297 | 0.926 | 3 |
| Rust PathMap bits-bytes | 1.407 | 1.458 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | 72.123 | 0.367 | 72.490 | 72.491 |
