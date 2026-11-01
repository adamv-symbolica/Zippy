# Semi-naive Datalog

[All examples](README.md) · Milliseconds per evaluation.

## datalog semi-naive — reference 24-chain

Result: **300 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 221.402 | reference path sets |
| ROG exec | 0.002 | compiled away; use compile+run |
| evalTrie | 5.947 | symbol trie |
| evalZ | 10.117 | zipper evaluator |
| ROG execT | 0.001 | compiled away; use compile+run |
| Zippy_bits reference | 1071.099030 | independent set representation |
| Zippy_bytes reference | 2948.043248 | independent set representation |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/programs/09-datalog-semi-naive-reference-24-chain/symbols.cpp) | 0.000006 | precomputed literal |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/programs/09-datalog-semi-naive-reference-24-chain/bits.cpp) | 270.429975 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/programs/09-datalog-semi-naive-reference-24-chain/bytes.cpp) | 21.393390 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/programs/09-datalog-semi-naive-reference-24-chain/bytes-interned.rs) | 4.375053 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/programs/09-datalog-semi-naive-reference-24-chain/bytes-strings.rs) | 33.710266 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/programs/09-datalog-semi-naive-reference-24-chain/bytes-length.rs) | 26.049885 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/programs/09-datalog-semi-naive-reference-24-chain/bits-bytes.rs) | 65.191337 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 41.200 | 415.848 |  |
| C++ OpenFst bits | 406.410 | 8825.893 |  |
| C++ OpenFst bytes | 124.633 | 2058.707 |  |
| Rust PathMap bytes-interned | 135.281 | 2699.188 | 3 |
| Rust PathMap bytes-strings | 84.693 | 4090.032 | 3 |
| Rust PathMap bytes-length | 112.175 | 4407.012 | 3 |
| Rust PathMap bits-bytes | 773.887 | 15723.243 | 1 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 12.538 | 12.538 | 12.539 |

## datalog semi-naive — process-sc 24-chain

Result: **300 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 198.335 | reference path sets |
| ROG exec | 17.345 | exec fallback Call dispatch to optimized callee graph(s): step394785440_sc1 |
| evalTrie | 10.599 | symbol trie |
| evalZ | 3.250 | zipper evaluator |
| ROG execT | 11.035 | exec fallback Call dispatch to optimized callee graph(s): step394785440_sc1 |
| Zippy_bits reference | 363.852969 | independent set representation |
| Zippy_bytes reference | 2242.944799 | independent set representation |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/programs/10-datalog-semi-naive-process-sc-24-chain/symbols.cpp) | 20.442543 | optimized graph |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/programs/10-datalog-semi-naive-process-sc-24-chain/bits.cpp) | 95.056995 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/programs/10-datalog-semi-naive-process-sc-24-chain/bytes.cpp) | 17.006089 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/programs/10-datalog-semi-naive-process-sc-24-chain/bytes-interned.rs) | 1.071102 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/programs/10-datalog-semi-naive-process-sc-24-chain/bytes-strings.rs) | 4.906685 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/programs/10-datalog-semi-naive-process-sc-24-chain/bytes-length.rs) | 3.544812 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/programs/10-datalog-semi-naive-process-sc-24-chain/bits-bytes.rs) | 9.903375 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 14.977 | 1169.089 |  |
| C++ OpenFst bits | 254.411 | 5393.887 |  |
| C++ OpenFst bytes | 26.274 | 2417.575 |  |
| Rust PathMap bytes-interned | 23.040 | 2236.903 | 3 |
| Rust PathMap bytes-strings | 109.290 | 3439.375 | 3 |
| Rust PathMap bytes-length | 174.373 | 3539.593 | 3 |
| Rust PathMap bits-bytes | 490.059 | 6915.587 | 1 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | 71.586 | 10.169 | 81.755 | 92.790 |

## semi-naive datalog over generated chain graph — 40 nodes, 79 edge facts

Result: **820 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 33.787 | symbol trie |
| evalZ | 21.317 | zipper evaluator |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/large/35-semi-naive-datalog-over-generated-chain-graph-40-nodes-79-edge-facts/symbols.cpp) | 49.424187 | optimized graph |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/large/35-semi-naive-datalog-over-generated-chain-graph-40-nodes-79-edge-facts/bits.cpp) | 1568.791047 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/large/35-semi-naive-datalog-over-generated-chain-graph-40-nodes-79-edge-facts/bytes.cpp) | 87.948814 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/large/35-semi-naive-datalog-over-generated-chain-graph-40-nodes-79-edge-facts/bytes-interned.rs) | 17.036285 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/large/35-semi-naive-datalog-over-generated-chain-graph-40-nodes-79-edge-facts/bytes-strings.rs) | 162.945719 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/large/35-semi-naive-datalog-over-generated-chain-graph-40-nodes-79-edge-facts/bytes-length.rs) | 111.975103 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/large/35-semi-naive-datalog-over-generated-chain-graph-40-nodes-79-edge-facts/bits-bytes.rs) | 303.380311 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 12.356 | 692.873 |  |
| C++ OpenFst bits | 407.963 | 10558.007 |  |
| C++ OpenFst bytes | 28.273 | 1816.059 |  |
| Rust PathMap bytes-interned | 24.204 | 2342.461 | 3 |
| Rust PathMap bytes-strings | 75.333 | 4064.674 | 3 |
| Rust PathMap bytes-length | 100.335 | 4883.278 | 3 |
| Rust PathMap bits-bytes | 824.551 | 19659.744 | 1 |

## semi-naive datalog over generated chain graph — 80 nodes, 159 edge facts

Result: **3,240 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 111.596 | symbol trie |
| evalZ | 65.303 | zipper evaluator |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/large/36-semi-naive-datalog-over-generated-chain-graph-80-nodes-159-edge-facts/symbols.cpp) | 342.413731 | optimized graph |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/large/36-semi-naive-datalog-over-generated-chain-graph-80-nodes-159-edge-facts/bits.cpp) | 16979.928364 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/large/36-semi-naive-datalog-over-generated-chain-graph-80-nodes-159-edge-facts/bytes.cpp) | 484.410426 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/large/36-semi-naive-datalog-over-generated-chain-graph-80-nodes-159-edge-facts/bytes-interned.rs) | 102.471126 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/large/36-semi-naive-datalog-over-generated-chain-graph-80-nodes-159-edge-facts/bytes-strings.rs) | 1218.411298 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/large/36-semi-naive-datalog-over-generated-chain-graph-80-nodes-159-edge-facts/bytes-length.rs) | 809.294131 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/large/36-semi-naive-datalog-over-generated-chain-graph-80-nodes-159-edge-facts/bits-bytes.rs) | 2380.811349 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 14.381 | 2.264 |  |
| C++ OpenFst bits | 383.012 | 11597.396 |  |
| C++ OpenFst bytes | 14.917 | 1795.603 |  |
| Rust PathMap bytes-interned | 22.055 | 2282.364 | 3 |
| Rust PathMap bytes-strings | 56.309 | 3.583 | 3 |
| Rust PathMap bytes-length | 75.156 | 3.968 | 3 |
| Rust PathMap bits-bytes | 404.886 | 20953.542 | 1 |
