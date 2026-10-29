# Graph queries

[All examples](README.md) · Runtime measurements are **milliseconds per evaluation**.

The five original Scala evaluator columns, two digit-set reference interpreters, and three C++ dialects are listed separately below. Large cases retain their two originally measured Scala evaluators. See the [measurement notes](README.md#measurement-notes) before comparing engines.

## graph two-hop — reference 90-chain

Fixture family: `programs`. Result: **264 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 2.903 |  |
| ROG exec | 1.287 |  |
| evalTrie | 0.921 |  |
| evalZ | 1.550 |  |
| ROG execT | 0.556 |  |
| Zippy_bits reference | 59.887842 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 38.595763 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.494895 | symbol labels; standalone; optimized graph |
| C++ OpenFst bits | 6.610836 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 1.017524 | four-byte big-endian intern IDs; standalone; optimized graph |

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 2.608 |
| total compile | 2.608 |
| compile+ROG execT | 3.165 |
| source pass | 0.303 |
| lower+inline | 0.082 |
| graph optimize | 1.451 |
| graph transpile | 0.073 |
| const-fold eval | 0.027 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/06-graph-two-hop-reference-90-chain/symbols.cpp) · [graph](../../output/cornerstone-review/programs/06-graph-two-hop-reference-90-chain/symbols.graph.txt) | 2.147 | 14 | 1859 | 1.905 | 355 | 354 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/06-graph-two-hop-reference-90-chain/bits.cpp) · [graph](../../output/cornerstone-review/programs/06-graph-two-hop-reference-90-chain/bits.graph.txt) | 146.682 | 2492 | 169034 | 4350.084 | 2214 | 2476 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/06-graph-two-hop-reference-90-chain/bytes.cpp) · [graph](../../output/cornerstone-review/programs/06-graph-two-hop-reference-90-chain/bytes.graph.txt) | 6.635 | 57 | 6644 | 714.147 | 628 | 627 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## graph mutual — reference 90-chain

Fixture family: `programs`. Result: **0 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 2.560 |  |
| ROG exec | 1.293 |  |
| evalTrie | 0.529 |  |
| evalZ | 0.740 |  |
| ROG execT | 0.310 |  |
| Zippy_bits reference | 21.072008 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 33.729144 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.053381 | symbol labels; standalone; optimized graph |
| C++ OpenFst bits | 1.681308 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 0.085336 | four-byte big-endian intern IDs; standalone; optimized graph |

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 2.423 |
| total compile | 2.423 |
| compile+ROG execT | 2.733 |
| source pass | 0.127 |
| lower+inline | 0.029 |
| graph optimize | 1.542 |
| graph transpile | 0.091 |
| const-fold eval | 0.000 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/07-graph-mutual-reference-90-chain/symbols.cpp) · [graph](../../output/cornerstone-review/programs/07-graph-mutual-reference-90-chain/symbols.graph.txt) | 1.796 | 14 | 1849 | 569.927 | 0 | 0 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/07-graph-mutual-reference-90-chain/bits.cpp) · [graph](../../output/cornerstone-review/programs/07-graph-mutual-reference-90-chain/bits.graph.txt) | 79.999 | 1478 | 98864 | 2709.660 | 0 | 0 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/07-graph-mutual-reference-90-chain/bytes.cpp) · [graph](../../output/cornerstone-review/programs/07-graph-mutual-reference-90-chain/bytes.graph.txt) | 5.759 | 45 | 5234 | 678.301 | 0 | 0 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## nonrecursive graph two-hop — 180 nodes, 359 edge facts

Fixture family: `large`. Result: **534 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 1.832 |  |
| evalZ | 2.396 |  |
| C++ OpenFst symbols | 1.128613 | symbol labels; standalone; optimized graph |
| C++ OpenFst bits | 16.734572 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 2.046854 | four-byte big-endian intern IDs; standalone; optimized graph |

Original benchmark note: generic iteration materializes branch tries natively at the final boundary.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/23-nonrecursive-graph-two-hop-180-nodes-359-edge-facts/symbols.cpp) · [graph](../../output/cornerstone-review/large/23-nonrecursive-graph-two-hop-180-nodes-359-edge-facts/symbols.graph.txt) | 77.170 | 14 | 1859 | 11173.680 | 715 | 714 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/large/23-nonrecursive-graph-two-hop-180-nodes-359-edge-facts/bits.cpp) · [graph](../../output/cornerstone-review/large/23-nonrecursive-graph-two-hop-180-nodes-359-edge-facts/bits.graph.txt) | 406.995 | 2738 | 185522 | 4792.640 | 5145 | 5677 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/23-nonrecursive-graph-two-hop-180-nodes-359-edge-facts/bytes.cpp) · [graph](../../output/cornerstone-review/large/23-nonrecursive-graph-two-hop-180-nodes-359-edge-facts/bytes.graph.txt) | 11.370 | 57 | 6645 | 716.485 | 1258 | 1257 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## nonrecursive graph two-hop — 360 nodes, 719 edge facts

Fixture family: `large`. Result: **1,074 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 1.696 |  |
| evalZ | 2.860 |  |
| C++ OpenFst symbols | 3.277115 | symbol labels; standalone; optimized graph |
| C++ OpenFst bits | 35.693951 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 4.129329 | four-byte big-endian intern IDs; standalone; optimized graph |

Original benchmark note: generic iteration materializes branch tries natively at the final boundary.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/24-nonrecursive-graph-two-hop-360-nodes-719-edge-facts/symbols.cpp) · [graph](../../output/cornerstone-review/large/24-nonrecursive-graph-two-hop-360-nodes-719-edge-facts/symbols.graph.txt) | 81.139 | 14 | 1859 | 11158.398 | 1435 | 1434 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/large/24-nonrecursive-graph-two-hop-360-nodes-719-edge-facts/bits.cpp) · [graph](../../output/cornerstone-review/large/24-nonrecursive-graph-two-hop-360-nodes-719-edge-facts/bits.graph.txt) | 455.348 | 3019 | 204646 | 5384.526 | 11739 | 12811 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/24-nonrecursive-graph-two-hop-360-nodes-719-edge-facts/bytes.cpp) · [graph](../../output/cornerstone-review/large/24-nonrecursive-graph-two-hop-360-nodes-719-edge-facts/bytes.graph.txt) | 10.317 | 57 | 6645 | 727.970 | 2529 | 2528 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## nonrecursive graph two-hop — 720 nodes, 1439 edge facts

Fixture family: `large`. Result: **2,154 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 4.324 |  |
| evalZ | 5.579 |  |
| C++ OpenFst symbols | 3.989034 | symbol labels; standalone; optimized graph |
| C++ OpenFst bits | 84.949845 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 6.547762 | four-byte big-endian intern IDs; standalone; optimized graph |

Original benchmark note: generic iteration materializes branch tries natively at the final boundary.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/25-nonrecursive-graph-two-hop-720-nodes-1439-edge-facts/symbols.cpp) · [graph](../../output/cornerstone-review/large/25-nonrecursive-graph-two-hop-720-nodes-1439-edge-facts/symbols.graph.txt) | 105.281 | 14 | 1859 | 10761.700 | 2875 | 2874 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/large/25-nonrecursive-graph-two-hop-720-nodes-1439-edge-facts/bits.cpp) · [graph](../../output/cornerstone-review/large/25-nonrecursive-graph-two-hop-720-nodes-1439-edge-facts/bits.graph.txt) | 506.140 | 3546 | 240258 | 6549.995 | 26676 | 28828 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/25-nonrecursive-graph-two-hop-720-nodes-1439-edge-facts/bytes.cpp) · [graph](../../output/cornerstone-review/large/25-nonrecursive-graph-two-hop-720-nodes-1439-edge-facts/bytes.graph.txt) | 9.308 | 57 | 6645 | 706.202 | 5056 | 5055 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.
