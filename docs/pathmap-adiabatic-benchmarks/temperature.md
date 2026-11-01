# Temperature

[All examples](README.md) · Milliseconds per evaluation.

## temperature — NOAA committed slice

Result: **12 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.003 | reference path sets |
| ROG exec | 0.003 |  |
| evalTrie | 0.005 | symbol trie |
| evalZ | 0.021 | zipper evaluator |
| ROG execT | 0.004 |  |
| Zippy_bits reference | 0.058168 | independent set representation |
| Zippy_bytes reference | 0.387060 | independent set representation |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/programs/14-temperature-noaa-committed-slice/symbols.cpp) | 0.008314 | optimized graph |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/programs/14-temperature-noaa-committed-slice/bits.cpp) | 0.022098 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/programs/14-temperature-noaa-committed-slice/bytes.cpp) | 0.043835 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/programs/14-temperature-noaa-committed-slice/bytes-interned.rs) | 0.000326 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/programs/14-temperature-noaa-committed-slice/bytes-strings.rs) | 0.000225 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/programs/14-temperature-noaa-committed-slice/bytes-length.rs) | 0.000219 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/programs/14-temperature-noaa-committed-slice/bits-bytes.rs) | 0.000259 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 2.238 | 427.161 |  |
| C++ OpenFst bits | 1.431 | 405.836 |  |
| C++ OpenFst bytes | 1.293 | 377.650 |  |
| Rust PathMap bytes-interned | 1.623 | 1509.885 | 3 |
| Rust PathMap bytes-strings | 0.977 | 1511.604 | 3 |
| Rust PathMap bytes-length | 0.918 | 1501.251 | 3 |
| Rust PathMap bits-bytes | 1.028 | 1503.069 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 0.548 | 0.548 | 0.552 |

## temperature — synthetic 32x64

Result: **448 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 1.100 | reference path sets |
| ROG exec | 1.095 |  |
| evalTrie | 0.013 | symbol trie |
| evalZ | 0.050 | zipper evaluator |
| ROG execT | 0.010 |  |
| Zippy_bits reference | 3.812038 | independent set representation |
| Zippy_bytes reference | 61.758528 | independent set representation |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/programs/15-temperature-synthetic-32x64/symbols.cpp) | 0.063515 | optimized graph |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/programs/15-temperature-synthetic-32x64/bits.cpp) | 0.183409 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/programs/15-temperature-synthetic-32x64/bytes.cpp) | 0.292900 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/programs/15-temperature-synthetic-32x64/bytes-interned.rs) | 0.005431 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/programs/15-temperature-synthetic-32x64/bytes-strings.rs) | 0.003651 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/programs/15-temperature-synthetic-32x64/bytes-length.rs) | 0.003632 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/programs/15-temperature-synthetic-32x64/bits-bytes.rs) | 0.004096 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 1.240 | 425.310 |  |
| C++ OpenFst bits | 1.619 | 401.065 |  |
| C++ OpenFst bytes | 1.560 | 394.059 |  |
| Rust PathMap bytes-interned | 1.835 | 1496.165 | 3 |
| Rust PathMap bytes-strings | 0.859 | 1495.914 | 3 |
| Rust PathMap bytes-length | 0.815 | 1494.623 | 3 |
| Rust PathMap bits-bytes | 0.834 | 1498.466 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 0.352 | 0.352 | 0.362 |
