# Temperature

[All examples](README.md) · Milliseconds per evaluation.

## temperature — NOAA committed slice

Result: **12 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.003 | reference path sets |
| ROG exec | 0.003 |  |
| evalTrie | 0.012 | symbol trie |
| evalZ | 0.024 | zipper evaluator |
| ROG execT | 0.009 |  |
| Zippy_bits reference | 0.142544 | independent set representation |
| Zippy_bytes reference | 0.550486 | independent set representation |
| C++ OpenFst symbols | 0.009210 | optimized graph |
| C++ OpenFst bits | 0.024539 | optimized graph |
| C++ OpenFst bytes | 0.047576 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/programs/14-temperature-noaa-committed-slice/bytes-interned.rs) | 0.012305 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/programs/14-temperature-noaa-committed-slice/bytes-strings.rs) | 0.006895 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/programs/14-temperature-noaa-committed-slice/bits-bytes.rs) | 0.008790 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 1.484 | 458.901 |  |
| C++ OpenFst bits | 1.222 | 450.793 |  |
| C++ OpenFst bytes | 1.256 | 438.744 |  |
| Rust PathMap bytes-interned | 2.510 | 1.752 | 3 |
| Rust PathMap bytes-strings | 1.077 | 1.391 | 3 |
| Rust PathMap bits-bytes | 0.984 | 1.841 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 0.551 | 0.551 | 0.559 |

## temperature — synthetic 32x64

Result: **448 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 1.112 | reference path sets |
| ROG exec | 1.107 |  |
| evalTrie | 0.016 | symbol trie |
| evalZ | 0.046 | zipper evaluator |
| ROG execT | 0.010 |  |
| Zippy_bits reference | 4.850917 | independent set representation |
| Zippy_bytes reference | 63.915253 | independent set representation |
| C++ OpenFst symbols | 0.068406 | optimized graph |
| C++ OpenFst bits | 0.199157 | optimized graph |
| C++ OpenFst bytes | 0.309102 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/programs/15-temperature-synthetic-32x64/bytes-interned.rs) | 0.363494 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/programs/15-temperature-synthetic-32x64/bytes-strings.rs) | 0.206392 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/programs/15-temperature-synthetic-32x64/bits-bytes.rs) | 0.267835 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 1.052 | 443.591 |  |
| C++ OpenFst bits | 2.013 | 465.255 |  |
| C++ OpenFst bytes | 5.706 | 459.490 |  |
| Rust PathMap bytes-interned | 2.151 | 1.793 | 3 |
| Rust PathMap bytes-strings | 1.470 | 2.017 | 3 |
| Rust PathMap bits-bytes | 1.368 | 2.090 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 0.343 | 0.343 | 0.353 |
