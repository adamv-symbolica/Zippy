# Semi-naive Datalog

[All examples](README.md) · Runtime measurements are **milliseconds per evaluation**.

The five original Scala evaluator columns, two digit-set reference interpreters, and three C++ dialects are listed separately below. Large cases retain their two originally measured Scala evaluators. See the [measurement notes](README.md#measurement-notes) before comparing engines.

## datalog semi-naive — reference 24-chain

Fixture family: `programs`. Result: **300 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 363.306 |  |
| ROG exec | 0.002 | precomputed literal |
| evalTrie | 5.724 |  |
| evalZ | 11.276 |  |
| ROG execT | 0.001 | precomputed literal |
| Zippy_bits reference | 1167.758626 | BigInt path sets; 1 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 3073.271993 | byte-array path sets; 1 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 151.105298 | symbol labels; standalone |
| C++ OpenFst bits | 1076.769413 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 422.971728 | four-byte big-endian intern IDs; standalone |

Original benchmark note: compiled away; use compile+run.

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 11.681 |
| total compile | 11.681 |
| compile+ROG execT | 11.683 |
| source pass | 10.648 |
| lower+inline | 0.073 |
| graph optimize | 0.166 |
| graph transpile | 0.006 |
| const-fold eval | 0.273 |
| const-fold evalTrie | 9.152 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/09-datalog-semi-naive-reference-24-chain/symbols.cpp) | 1.778 | 26 | 324 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/09-datalog-semi-naive-reference-24-chain/bits.cpp) | 5.040 | 143 | 230 | 1 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/09-datalog-semi-naive-reference-24-chain/bytes.cpp) | 1.938 | 101 | 399 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## datalog semi-naive — process-sc 24-chain

Fixture family: `programs`. Result: **300 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 294.418 |  |
| ROG exec | 18.890 |  |
| evalTrie | 10.562 |  |
| evalZ | 8.138 |  |
| ROG execT | 2.793 |  |
| Zippy_bits reference | 361.493704 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 2383.947870 | byte-array path sets; 1 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 76.413691 | symbol labels; standalone |
| C++ OpenFst bits | 326.169784 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 177.791046 | four-byte big-endian intern IDs; standalone |

Original benchmark note: exec fallback Call dispatch to optimized callee graph(s): step1328718765_sc1.

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | 66.772 |
| graph compile | 8.253 |
| total compile | 75.025 |
| compile+ROG execT | 77.818 |
| source pass | 1.232 |
| lower+inline | 0.083 |
| graph optimize | 5.359 |
| graph transpile | 0.278 |
| const-fold eval | 0.011 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/10-datalog-semi-naive-process-sc-24-chain/symbols.cpp) | 1.836 | 26 | 324 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/10-datalog-semi-naive-process-sc-24-chain/bits.cpp) | 3.768 | 134 | 209 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/10-datalog-semi-naive-process-sc-24-chain/bytes.cpp) | 1.865 | 101 | 399 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## semi-naive datalog over generated chain graph — 40 nodes, 79 edge facts

Fixture family: `large`. Result: **820 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 36.053 |  |
| evalZ | 21.734 |  |
| C++ OpenFst symbols | 878.026810 | symbol labels; standalone |
| C++ OpenFst bits | 6011.503850 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 2409.274450 | four-byte big-endian intern IDs; standalone |

Original benchmark note: recursive union-saturating routine lowered to lazy native-trie synchronous rounds.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/35-semi-naive-datalog-over-generated-chain-graph-40-nodes-79-edge-facts/symbols.cpp) | 1.949 | 42 | 860 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/large/35-semi-naive-datalog-over-generated-chain-graph-40-nodes-79-edge-facts/bits.cpp) | 9.464 | 258 | 426 | 1 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/35-semi-naive-datalog-over-generated-chain-graph-40-nodes-79-edge-facts/bytes.cpp) | 1.982 | 165 | 983 | 1 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## semi-naive datalog over generated chain graph — 80 nodes, 159 edge facts

Fixture family: `large`. Result: **3,240 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 195.449 |  |
| evalZ | 76.826 |  |
| C++ OpenFst symbols | 9512.884238 | symbol labels; standalone |
| C++ OpenFst bits | 59657.067397 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 23121.141642 | four-byte big-endian intern IDs; standalone |

Original benchmark note: recursive union-saturating routine lowered to lazy native-trie synchronous rounds.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/36-semi-naive-datalog-over-generated-chain-graph-80-nodes-159-edge-facts/symbols.cpp) | 1.611 | 82 | 3320 | 1 | exact |
| [bits.cpp](../../output/cornerstone-review/large/36-semi-naive-datalog-over-generated-chain-graph-80-nodes-159-edge-facts/bits.cpp) | 5.928 | 579 | 972 | 1 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/36-semi-naive-datalog-over-generated-chain-graph-80-nodes-159-edge-facts/bytes.cpp) | 3.600 | 325 | 3563 | 1 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.
