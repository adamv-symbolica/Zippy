# Graph queries

[All examples](README.md) · Runtime measurements are **milliseconds per evaluation**.

The five original Scala evaluator columns, two digit-set reference interpreters, and three C++ dialects are listed separately below. Large cases retain their two originally measured Scala evaluators. See the [measurement notes](README.md#measurement-notes) before comparing engines.

## graph two-hop — reference 90-chain

Fixture family: `programs`. Result: **264 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 3.732 |  |
| ROG exec | 1.314 |  |
| evalTrie | 1.052 |  |
| evalZ | 1.553 |  |
| ROG execT | 0.855 |  |
| Zippy_bits reference | 30.664068 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 30.772570 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 2.905518 | symbol labels; standalone |
| C++ OpenFst bits | 30.938929 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 11.564897 | four-byte big-endian intern IDs; standalone |

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 1.942 |
| total compile | 1.942 |
| compile+ROG execT | 2.797 |
| source pass | 0.262 |
| lower+inline | 0.022 |
| graph optimize | 1.190 |
| graph transpile | 0.046 |
| const-fold eval | 0.048 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/06-graph-two-hop-reference-90-chain/symbols.cpp) | 32.392 | 92 | 354 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/06-graph-two-hop-reference-90-chain/bits.cpp) | 8.690 | 400 | 618 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/06-graph-two-hop-reference-90-chain/bytes.cpp) | 2.106 | 365 | 627 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## graph mutual — reference 90-chain

Fixture family: `programs`. Result: **0 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 3.238 |  |
| ROG exec | 1.169 |  |
| evalTrie | 0.551 |  |
| evalZ | 0.741 |  |
| ROG execT | 0.274 |  |
| Zippy_bits reference | 17.554512 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 32.911479 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 2.060155 | symbol labels; standalone |
| C++ OpenFst bits | 14.672749 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 9.626860 | four-byte big-endian intern IDs; standalone |

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 1.665 |
| total compile | 1.665 |
| compile+ROG execT | 1.939 |
| source pass | 0.117 |
| lower+inline | 0.022 |
| graph optimize | 1.057 |
| graph transpile | 0.040 |
| const-fold eval | 0.000 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/07-graph-mutual-reference-90-chain/symbols.cpp) | 2.040 | 0 | 0 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/07-graph-mutual-reference-90-chain/bits.cpp) | 4.002 | 0 | 0 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/07-graph-mutual-reference-90-chain/bytes.cpp) | 1.556 | 0 | 0 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## nonrecursive graph two-hop — 180 nodes, 359 edge facts

Fixture family: `large`. Result: **534 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 1.843 |  |
| evalZ | 2.803 |  |
| C++ OpenFst symbols | 10.637807 | symbol labels; standalone |
| C++ OpenFst bits | 104.544236 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 46.925899 | four-byte big-endian intern IDs; standalone |

Original benchmark note: generic iteration materializes branch tries natively at the final boundary.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/23-nonrecursive-graph-two-hop-180-nodes-359-edge-facts/symbols.cpp) | 41.464 | 182 | 714 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/large/23-nonrecursive-graph-two-hop-180-nodes-359-edge-facts/bits.cpp) | 9.712 | 794 | 1233 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/23-nonrecursive-graph-two-hop-180-nodes-359-edge-facts/bytes.cpp) | 2.066 | 725 | 1257 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## nonrecursive graph two-hop — 360 nodes, 719 edge facts

Fixture family: `large`. Result: **1,074 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 2.669 |  |
| evalZ | 3.209 |  |
| C++ OpenFst symbols | 45.286980 | symbol labels; standalone |
| C++ OpenFst bits | 400.203034 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 229.897386 | four-byte big-endian intern IDs; standalone |

Original benchmark note: generic iteration materializes branch tries natively at the final boundary.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/24-nonrecursive-graph-two-hop-360-nodes-719-edge-facts/symbols.cpp) | 38.887 | 362 | 1434 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/large/24-nonrecursive-graph-two-hop-360-nodes-719-edge-facts/bits.cpp) | 10.451 | 1562 | 2441 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/24-nonrecursive-graph-two-hop-360-nodes-719-edge-facts/bytes.cpp) | 2.445 | 1403 | 2369 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## nonrecursive graph two-hop — 720 nodes, 1439 edge facts

Fixture family: `large`. Result: **2,154 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 4.325 |  |
| evalZ | 4.519 |  |
| C++ OpenFst symbols | 193.560991 | symbol labels; standalone |
| C++ OpenFst bits | 1509.672888 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 1012.848632 | four-byte big-endian intern IDs; standalone |

Original benchmark note: generic iteration materializes branch tries natively at the final boundary.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/25-nonrecursive-graph-two-hop-720-nodes-1439-edge-facts/symbols.cpp) | 39.749 | 722 | 2874 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/large/25-nonrecursive-graph-two-hop-720-nodes-1439-edge-facts/bits.cpp) | 11.454 | 3098 | 4875 | 1 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/25-nonrecursive-graph-two-hop-720-nodes-1439-edge-facts/bytes.cpp) | 1.965 | 2581 | 4095 | 1 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.
