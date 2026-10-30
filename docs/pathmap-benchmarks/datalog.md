# Semi-naive Datalog

[All examples](README.md) · Milliseconds per evaluation.

## datalog semi-naive — reference 24-chain

Result: **300 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 333.210 | reference path sets |
| ROG exec | 0.002 | compiled away; use compile+run |
| evalTrie | 5.875 | symbol trie |
| evalZ | 11.797 | zipper evaluator |
| ROG execT | 0.001 | compiled away; use compile+run |
| Zippy_bits reference | 1531.054358 | independent set representation |
| Zippy_bytes reference | 3499.285913 | independent set representation |
| C++ OpenFst symbols | 0.000006 | precomputed literal |
| C++ OpenFst bits | 313.809152 | optimized graph |
| C++ OpenFst bytes | 28.979439 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/programs/09-datalog-semi-naive-reference-24-chain/bytes-interned.rs) | 3.675874 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/programs/09-datalog-semi-naive-reference-24-chain/bytes-strings.rs) | 33.328693 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/programs/09-datalog-semi-naive-reference-24-chain/bits-bytes.rs) | 71.053952 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 20.338 | 420.932 |  |
| C++ OpenFst bits | 521.920 | 9506.267 |  |
| C++ OpenFst bytes | 150.040 | 2328.434 |  |
| Rust PathMap bytes-interned | 132.695 | 2.014 | 3 |
| Rust PathMap bytes-strings | 77.172 | 2.300 | 3 |
| Rust PathMap bits-bytes | 280.947 | 3.347 | 1 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 11.217 | 11.217 | 11.218 |

## datalog semi-naive — process-sc 24-chain

Result: **300 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 210.197 | reference path sets |
| ROG exec | 25.869 | exec fallback Call dispatch to optimized callee graph(s): step394785440_sc1 |
| evalTrie | 15.304 | symbol trie |
| evalZ | 3.242 | zipper evaluator |
| ROG execT | 5.916 | exec fallback Call dispatch to optimized callee graph(s): step394785440_sc1 |
| Zippy_bits reference | 586.183568 | independent set representation |
| Zippy_bytes reference | 2341.774040 | independent set representation |
| C++ OpenFst symbols | 12.962574 | optimized graph |
| C++ OpenFst bits | 88.658537 | optimized graph |
| C++ OpenFst bytes | 17.821853 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/programs/10-datalog-semi-naive-process-sc-24-chain/bytes-interned.rs) | 1.635768 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/programs/10-datalog-semi-naive-process-sc-24-chain/bytes-strings.rs) | 5.230215 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/programs/10-datalog-semi-naive-process-sc-24-chain/bits-bytes.rs) | 9.225569 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 47.038 | 1007.755 |  |
| C++ OpenFst bits | 339.585 | 6118.152 |  |
| C++ OpenFst bytes | 43.816 | 2607.138 |  |
| Rust PathMap bytes-interned | 74.823 | 2.192 | 3 |
| Rust PathMap bytes-strings | 60.984 | 1.954 | 3 |
| Rust PathMap bits-bytes | 159.348 | 3.302 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | 80.570 | 8.164 | 88.735 | 94.651 |

## semi-naive datalog over generated chain graph — 40 nodes, 79 edge facts

Result: **820 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 33.640 | symbol trie |
| evalZ | 20.794 | zipper evaluator |
| C++ OpenFst symbols | 63.194011 | optimized graph |
| C++ OpenFst bits | 1809.060951 | optimized graph |
| C++ OpenFst bytes | 94.905347 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/large/35-semi-naive-datalog-over-generated-chain-graph-40-nodes-79-edge-facts/bytes-interned.rs) | 10.219680 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/large/35-semi-naive-datalog-over-generated-chain-graph-40-nodes-79-edge-facts/bytes-strings.rs) | 156.081513 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/large/35-semi-naive-datalog-over-generated-chain-graph-40-nodes-79-edge-facts/bits-bytes.rs) | 313.798790 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 30.243 | 828.761 |  |
| C++ OpenFst bits | 551.582 | 11162.918 |  |
| C++ OpenFst bytes | 35.579 | 2145.627 |  |
| Rust PathMap bytes-interned | 34.037 | 9.115 | 3 |
| Rust PathMap bytes-strings | 61.446 | 2.342 | 3 |
| Rust PathMap bits-bytes | 607.365 | 8.141 | 1 |

## semi-naive datalog over generated chain graph — 80 nodes, 159 edge facts

Result: **3,240 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 188.522 | symbol trie |
| evalZ | 62.202 | zipper evaluator |
| C++ OpenFst symbols | 448.100248 | optimized graph |
| C++ OpenFst bits | 18619.606848 | optimized graph |
| C++ OpenFst bytes | 549.974549 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/large/36-semi-naive-datalog-over-generated-chain-graph-80-nodes-159-edge-facts/bytes-interned.rs) | 41.891587 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/large/36-semi-naive-datalog-over-generated-chain-graph-80-nodes-159-edge-facts/bytes-strings.rs) | 1168.164191 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/large/36-semi-naive-datalog-over-generated-chain-graph-80-nodes-159-edge-facts/bits-bytes.rs) | 2480.776705 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 18.856 | 1.927 |  |
| C++ OpenFst bits | 697.279 | 12698.712 |  |
| C++ OpenFst bytes | 47.474 | 2050.523 |  |
| Rust PathMap bytes-interned | 27.751 | 1.744 | 3 |
| Rust PathMap bytes-strings | 36.152 | 1.698 | 3 |
| Rust PathMap bits-bytes | 443.885 | 4.810 | 1 |
