# Semi-naive Datalog

[All examples](README.md) · Milliseconds per evaluation.

## datalog semi-naive — reference 24-chain

Result: **300 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 289.350 | reference path sets |
| ROG exec | 0.002 | compiled away; use compile+run |
| evalTrie | 5.776 | symbol trie |
| evalZ | 10.304 | zipper evaluator |
| ROG execT | 0.001 | compiled away; use compile+run |
| Zippy_bits reference | 1181.683386 | independent set representation |
| Zippy_bytes reference | 3011.438115 | independent set representation |
| C++ OpenFst symbols | 0.000006 | precomputed literal |
| C++ OpenFst bits | 224.777067 | optimized graph |
| C++ OpenFst bytes | 18.681262 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/programs/09-datalog-semi-naive-reference-24-chain/bytes-interned.rs) | 3.966570 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/programs/09-datalog-semi-naive-reference-24-chain/bytes-strings.rs) | 36.532169 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/programs/09-datalog-semi-naive-reference-24-chain/bits-bytes.rs) | 73.616074 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 30.010 | 326.277 |  |
| C++ OpenFst bits | 312.548 | 7394.645 |  |
| C++ OpenFst bytes | 80.090 | 1448.628 |  |
| Rust PathMap bytes-interned | 129.097 | 2.700 | 3 |
| Rust PathMap bytes-strings | 94.173 | 3.774 | 3 |
| Rust PathMap bits-bytes | 368.399 | 30.900 | 1 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 15.416 | 15.416 | 15.418 |

## datalog semi-naive — process-sc 24-chain

Result: **300 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 199.211 | reference path sets |
| ROG exec | 19.373 | exec fallback Call dispatch to optimized callee graph(s): step394785440_sc1 |
| evalTrie | 10.745 | symbol trie |
| evalZ | 3.393 | zipper evaluator |
| ROG execT | 11.072 | exec fallback Call dispatch to optimized callee graph(s): step394785440_sc1 |
| Zippy_bits reference | 363.140768 | independent set representation |
| Zippy_bytes reference | 2441.972982 | independent set representation |
| C++ OpenFst symbols | 9.373159 | optimized graph |
| C++ OpenFst bits | 62.785829 | optimized graph |
| C++ OpenFst bytes | 12.898288 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/programs/10-datalog-semi-naive-process-sc-24-chain/bytes-interned.rs) | 1.779699 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/programs/10-datalog-semi-naive-process-sc-24-chain/bytes-strings.rs) | 5.618999 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/programs/10-datalog-semi-naive-process-sc-24-chain/bits-bytes.rs) | 12.115143 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 6.802 | 726.455 |  |
| C++ OpenFst bits | 147.100 | 4416.928 |  |
| C++ OpenFst bytes | 34.235 | 1560.382 |  |
| Rust PathMap bytes-interned | 67.032 | 3.005 | 3 |
| Rust PathMap bytes-strings | 64.117 | 3.941 | 3 |
| Rust PathMap bits-bytes | 208.430 | 15.210 | 1 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | 68.394 | 9.052 | 77.446 | 88.518 |

## semi-naive datalog over generated chain graph — 40 nodes, 79 edge facts

Result: **820 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 31.163 | symbol trie |
| evalZ | 17.983 | zipper evaluator |
| C++ OpenFst symbols | 43.224918 | optimized graph |
| C++ OpenFst bits | 1401.892529 | optimized graph |
| C++ OpenFst bytes | 78.532428 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/large/35-semi-naive-datalog-over-generated-chain-graph-40-nodes-79-edge-facts/bytes-interned.rs) | 12.143805 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/large/35-semi-naive-datalog-over-generated-chain-graph-40-nodes-79-edge-facts/bytes-strings.rs) | 172.151318 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/large/35-semi-naive-datalog-over-generated-chain-graph-40-nodes-79-edge-facts/bits-bytes.rs) | 333.427292 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 10.587 | 622.748 |  |
| C++ OpenFst bits | 229.433 | 9286.119 |  |
| C++ OpenFst bytes | 16.593 | 1552.420 |  |
| Rust PathMap bytes-interned | 23.588 | 2.150 | 3 |
| Rust PathMap bytes-strings | 66.411 | 2.815 | 3 |
| Rust PathMap bits-bytes | 443.397 | 37.289 | 1 |

## semi-naive datalog over generated chain graph — 80 nodes, 159 edge facts

Result: **3,240 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 137.295 | symbol trie |
| evalZ | 62.835 | zipper evaluator |
| C++ OpenFst symbols | 318.001560 | optimized graph |
| C++ OpenFst bits | 14611.708096 | optimized graph |
| C++ OpenFst bytes | 461.959446 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/large/36-semi-naive-datalog-over-generated-chain-graph-80-nodes-159-edge-facts/bytes-interned.rs) | 53.966851 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/large/36-semi-naive-datalog-over-generated-chain-graph-80-nodes-159-edge-facts/bytes-strings.rs) | 1281.442320 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/large/36-semi-naive-datalog-over-generated-chain-graph-80-nodes-159-edge-facts/bits-bytes.rs) | 2683.263049 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 5.110 | 1.405 |  |
| C++ OpenFst bits | 295.131 | 10467.801 |  |
| C++ OpenFst bytes | 18.999 | 1433.220 |  |
| Rust PathMap bytes-interned | 18.590 | 2.079 | 3 |
| Rust PathMap bytes-strings | 68.253 | 3.784 | 3 |
| Rust PathMap bits-bytes | 481.456 | 49.103 | 1 |
