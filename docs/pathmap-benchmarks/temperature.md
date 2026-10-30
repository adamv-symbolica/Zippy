# Temperature

[All examples](README.md) · Milliseconds per evaluation.

## temperature — NOAA committed slice

Result: **12 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.003 | reference path sets |
| ROG exec | 0.003 |  |
| evalTrie | 0.011 | symbol trie |
| evalZ | 0.021 | zipper evaluator |
| ROG execT | 0.010 |  |
| Zippy_bits reference | 0.057417 | independent set representation |
| Zippy_bytes reference | 0.491627 | independent set representation |
| C++ OpenFst symbols | 0.008314 | optimized graph |
| C++ OpenFst bits | 0.022219 | optimized graph |
| C++ OpenFst bytes | 0.042686 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/programs/14-temperature-noaa-committed-slice/bytes-interned.rs) | 0.014992 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/programs/14-temperature-noaa-committed-slice/bytes-strings.rs) | 0.008658 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/programs/14-temperature-noaa-committed-slice/bits-bytes.rs) | 0.010429 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 0.793 | 332.893 |  |
| C++ OpenFst bits | 0.608 | 332.578 |  |
| C++ OpenFst bytes | 0.612 | 332.192 |  |
| Rust PathMap bytes-interned | 1.751 | 1.254 | 3 |
| Rust PathMap bytes-strings | 1.079 | 0.945 | 3 |
| Rust PathMap bits-bytes | 0.423 | 1.039 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 0.512 | 0.512 | 0.522 |

## temperature — synthetic 32x64

Result: **448 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 1.075 | reference path sets |
| ROG exec | 1.095 |  |
| evalTrie | 0.013 | symbol trie |
| evalZ | 0.033 | zipper evaluator |
| ROG execT | 0.010 |  |
| Zippy_bits reference | 3.259038 | independent set representation |
| Zippy_bytes reference | 61.577657 | independent set representation |
| C++ OpenFst symbols | 0.063129 | optimized graph |
| C++ OpenFst bits | 0.184261 | optimized graph |
| C++ OpenFst bytes | 0.283696 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/programs/15-temperature-synthetic-32x64/bytes-interned.rs) | 0.427265 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/programs/15-temperature-synthetic-32x64/bytes-strings.rs) | 0.265912 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/programs/15-temperature-synthetic-32x64/bits-bytes.rs) | 0.320585 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 1.012 | 338.467 |  |
| C++ OpenFst bits | 1.437 | 331.330 |  |
| C++ OpenFst bytes | 2.089 | 345.513 |  |
| Rust PathMap bytes-interned | 1.671 | 1.689 | 3 |
| Rust PathMap bytes-strings | 0.851 | 1.404 | 3 |
| Rust PathMap bits-bytes | 0.909 | 1.733 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 0.307 | 0.307 | 0.317 |
