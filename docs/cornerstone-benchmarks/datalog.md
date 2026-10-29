# Semi-naive Datalog

[All examples](README.md) · Runtime measurements are **milliseconds per evaluation**.

The five original Scala evaluator columns, two digit-set reference interpreters, and three C++ dialects are listed separately below. Large cases retain their two originally measured Scala evaluators. See the [measurement notes](README.md#measurement-notes) before comparing engines.

## datalog semi-naive — reference 24-chain

Fixture family: `programs`. Result: **300 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 356.941 |  |
| ROG exec | 0.002 | precomputed literal |
| evalTrie | 5.883 |  |
| evalZ | 8.272 |  |
| ROG execT | 0.001 | precomputed literal |
| Zippy_bits reference | 1306.802856 | BigInt path sets; 1 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 2714.498090 | byte-array path sets; 1 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.000006 | symbol labels; standalone; precomputed literal |
| C++ OpenFst bits | 324.659940 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 38.152569 | four-byte big-endian intern IDs; standalone; optimized graph |

Original benchmark note: compiled away; use compile+run.

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 11.936 |
| total compile | 11.936 |
| compile+ROG execT | 11.937 |
| source pass | 10.381 |
| lower+inline | 0.139 |
| graph optimize | 0.174 |
| graph transpile | 0.010 |
| const-fold eval | 0.292 |
| const-fold evalTrie | 8.302 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/09-datalog-semi-naive-reference-24-chain/symbols.cpp) · [graph](../../output/cornerstone-review/programs/09-datalog-semi-naive-reference-24-chain/symbols.graph.txt) | 26.805 | 1 | 3200 | 365.020 | 26 | 324 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/09-datalog-semi-naive-reference-24-chain/bits.cpp) · [graph](../../output/cornerstone-review/programs/09-datalog-semi-naive-reference-24-chain/bits.graph.txt) | 353.511 | 6956 | 482020 | 8702.643 | 1053 | 1242 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/09-datalog-semi-naive-reference-24-chain/bytes.cpp) · [graph](../../output/cornerstone-review/programs/09-datalog-semi-naive-reference-24-chain/bytes.graph.txt) | 171.701 | 399 | 33494 | 2264.350 | 377 | 399 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## datalog semi-naive — process-sc 24-chain

Fixture family: `programs`. Result: **300 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 234.370 |  |
| ROG exec | 19.624 |  |
| evalTrie | 10.676 |  |
| evalZ | 3.280 |  |
| ROG execT | 4.675 |  |
| Zippy_bits reference | 532.859620 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 2516.128886 | byte-array path sets; 1 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 9.701953 | symbol labels; standalone; optimized graph |
| C++ OpenFst bits | 78.179179 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 13.478277 | four-byte big-endian intern IDs; standalone; optimized graph |

Original benchmark note: exec fallback Call dispatch to optimized callee graph(s): step1926673338_sc1.

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | 109.395 |
| graph compile | 8.227 |
| total compile | 117.622 |
| compile+ROG execT | 122.297 |
| source pass | 1.652 |
| lower+inline | 0.317 |
| graph optimize | 4.433 |
| graph transpile | 0.361 |
| const-fold eval | 0.006 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/10-datalog-semi-naive-process-sc-24-chain/symbols.cpp) · [graph](../../output/cornerstone-review/programs/10-datalog-semi-naive-process-sc-24-chain/symbols.graph.txt) | 13.206 | 116 | 13395 | 918.277 | 325 | 324 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/10-datalog-semi-naive-process-sc-24-chain/bits.cpp) · [graph](../../output/cornerstone-review/programs/10-datalog-semi-naive-process-sc-24-chain/bits.graph.txt) | 182.044 | 3504 | 246723 | 5012.137 | 1310 | 1309 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/10-datalog-semi-naive-process-sc-24-chain/bytes.cpp) · [graph](../../output/cornerstone-review/programs/10-datalog-semi-naive-process-sc-24-chain/bytes.graph.txt) | 36.740 | 305 | 43646 | 2200.192 | 400 | 399 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## semi-naive datalog over generated chain graph — 40 nodes, 79 edge facts

Fixture family: `large`. Result: **820 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 36.189 |  |
| evalZ | 22.946 |  |
| C++ OpenFst symbols | 51.548983 | symbol labels; standalone; optimized graph |
| C++ OpenFst bits | 3376.576317 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 102.212389 | four-byte big-endian intern IDs; standalone; optimized graph |

Original benchmark note: recursive union-saturating routine lowered to lazy native-trie synchronous rounds.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/35-semi-naive-datalog-over-generated-chain-graph-40-nodes-79-edge-facts/symbols.cpp) · [graph](../../output/cornerstone-review/large/35-semi-naive-datalog-over-generated-chain-graph-40-nodes-79-edge-facts/symbols.graph.txt) | 16.087 | 71 | 6146 | 788.249 | 861 | 860 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/large/35-semi-naive-datalog-over-generated-chain-graph-40-nodes-79-edge-facts/bits.cpp) · [graph](../../output/cornerstone-review/large/35-semi-naive-datalog-over-generated-chain-graph-40-nodes-79-edge-facts/bits.graph.txt) | 495.413 | 8175 | 563624 | 10607.815 | 2528 | 3307 | 1 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/35-semi-naive-datalog-over-generated-chain-graph-40-nodes-79-edge-facts/bytes.cpp) · [graph](../../output/cornerstone-review/large/35-semi-naive-datalog-over-generated-chain-graph-40-nodes-79-edge-facts/bytes.graph.txt) | 45.169 | 267 | 29220 | 2563.715 | 945 | 983 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## semi-naive datalog over generated chain graph — 80 nodes, 159 edge facts

Fixture family: `large`. Result: **3,240 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 169.075 |  |
| evalZ | 62.031 |  |
| C++ OpenFst symbols | 396.223546 | symbol labels; standalone; optimized graph |
| C++ OpenFst bits | 27503.689819 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 871.173200 | four-byte big-endian intern IDs; standalone; optimized graph |

Original benchmark note: recursive union-saturating routine lowered to lazy native-trie synchronous rounds.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/36-semi-naive-datalog-over-generated-chain-graph-80-nodes-159-edge-facts/symbols.cpp) · [graph](../../output/cornerstone-review/large/36-semi-naive-datalog-over-generated-chain-graph-80-nodes-159-edge-facts/symbols.graph.txt) | 14.542 | 71 | 6146 | 2.207 | 3321 | 3320 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/large/36-semi-naive-datalog-over-generated-chain-graph-80-nodes-159-edge-facts/bits.cpp) · [graph](../../output/cornerstone-review/large/36-semi-naive-datalog-over-generated-chain-graph-80-nodes-159-edge-facts/bits.graph.txt) | 570.024 | 9181 | 633532 | 15089.521 | 10341 | 13500 | 1 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/36-semi-naive-datalog-over-generated-chain-graph-80-nodes-159-edge-facts/bytes.cpp) · [graph](../../output/cornerstone-review/large/36-semi-naive-datalog-over-generated-chain-graph-80-nodes-159-edge-facts/bytes.graph.txt) | 40.236 | 267 | 29220 | 2245.093 | 3485 | 3563 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.
