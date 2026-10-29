# Strongly connected components

[All examples](README.md) · Runtime measurements are **milliseconds per evaluation**.

The five original Scala evaluator columns, two digit-set reference interpreters, and three C++ dialects are listed separately below. Large cases retain their two originally measured Scala evaluators. See the [measurement notes](README.md#measurement-notes) before comparing engines.

## scc — paper divide-and-conquer

Fixture family: `programs`. Result: **2 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.645 |  |
| ROG exec | 0.826 |  |
| evalTrie | 0.668 |  |
| evalZ | 3.339 |  |
| ROG execT | 0.598 |  |
| Zippy_bits reference | 5.805469 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 4.665799 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.494352 | symbol labels; standalone |
| C++ OpenFst bits | 2.026550 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 1.209030 | four-byte big-endian intern IDs; standalone |

Original benchmark note: exec fallback Call dispatch to optimized callee graph(s): reachable, seedless_scc.

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 19.240 |
| total compile | 19.240 |
| compile+ROG execT | 19.839 |
| source pass | 10.476 |
| lower+inline | 0.502 |
| graph optimize | 3.748 |
| graph transpile | 0.892 |
| const-fold eval | 0.583 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/08-scc-paper-divide-and-conquer/symbols.cpp) | 1.822 | 4 | 4 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/08-scc-paper-divide-and-conquer/bits.cpp) | 6949.209 | 9 | 9 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/08-scc-paper-divide-and-conquer/bytes.cpp) | 1536.254 | 13 | 13 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## SCC divide-and-conquer — 6 directed edges, 2 representative/member pairs

Fixture family: `large`. Result: **2 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 1.487 |  |
| evalZ | 5.381 |  |
| C++ OpenFst symbols | 0.492825 | symbol labels; standalone |
| C++ OpenFst bits | 2.086802 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 1.218864 | four-byte big-endian intern IDs; standalone |

Original benchmark note: paper pivot/partition recursion with masked reachability lowered to a native fixpoint.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/34-scc-divide-and-conquer-6-directed-edges-2-representative-member-pairs/symbols.cpp) | 3.567 | 4 | 4 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/large/34-scc-divide-and-conquer-6-directed-edges-2-representative-member-pairs/bits.cpp) | 5032.669 | 9 | 9 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/34-scc-divide-and-conquer-6-directed-edges-2-representative-member-pairs/bytes.cpp) | 1528.740 | 13 | 13 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.
