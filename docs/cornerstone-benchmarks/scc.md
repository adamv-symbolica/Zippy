# Strongly connected components

[All examples](README.md) · Runtime measurements are **milliseconds per evaluation**.

The five original Scala evaluator columns, two digit-set reference interpreters, and three C++ dialects are listed separately below. Large cases retain their two originally measured Scala evaluators. See the [measurement notes](README.md#measurement-notes) before comparing engines.

## scc — paper divide-and-conquer

Fixture family: `programs`. Result: **2 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.686 |  |
| ROG exec | 0.539 |  |
| evalTrie | 0.601 |  |
| evalZ | 4.383 |  |
| ROG execT | 0.309 |  |
| Zippy_bits reference | 12.521682 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 8.463665 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.196565 | symbol labels; standalone; optimized graph |
| C++ OpenFst bits | 1.150000 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 0.520910 | four-byte big-endian intern IDs; standalone; optimized graph |

Original benchmark note: exec fallback Call dispatch to optimized callee graph(s): reachable, seedless_scc.

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 32.774 |
| total compile | 32.774 |
| compile+ROG execT | 33.083 |
| source pass | 14.763 |
| lower+inline | 1.910 |
| graph optimize | 3.867 |
| graph transpile | 1.272 |
| const-fold eval | 0.596 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/08-scc-paper-divide-and-conquer/symbols.cpp) · [graph](../../output/cornerstone-review/programs/08-scc-paper-divide-and-conquer/symbols.graph.txt) | 24.276 | 104 | 5043 | 42.373 | 5 | 4 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/08-scc-paper-divide-and-conquer/bits.cpp) · [graph](../../output/cornerstone-review/programs/08-scc-paper-divide-and-conquer/bits.graph.txt) | 281.187 | 3324 | 232585 | 7014.181 | 14 | 13 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/08-scc-paper-divide-and-conquer/bytes.cpp) · [graph](../../output/cornerstone-review/programs/08-scc-paper-divide-and-conquer/bytes.graph.txt) | 39.783 | 351 | 34938 | 1444.846 | 14 | 13 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## SCC divide-and-conquer — 6 directed edges, 2 representative/member pairs

Fixture family: `large`. Result: **2 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 2.123 |  |
| evalZ | 8.545 |  |
| C++ OpenFst symbols | 0.197220 | symbol labels; standalone; optimized graph |
| C++ OpenFst bits | 1.156293 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 0.521198 | four-byte big-endian intern IDs; standalone; optimized graph |

Original benchmark note: paper pivot/partition recursion with masked reachability lowered to a native fixpoint.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/34-scc-divide-and-conquer-6-directed-edges-2-representative-member-pairs/symbols.cpp) · [graph](../../output/cornerstone-review/large/34-scc-divide-and-conquer-6-directed-edges-2-representative-member-pairs/symbols.graph.txt) | 96.197 | 104 | 5043 | 690.136 | 5 | 4 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/large/34-scc-divide-and-conquer-6-directed-edges-2-representative-member-pairs/bits.cpp) · [graph](../../output/cornerstone-review/large/34-scc-divide-and-conquer-6-directed-edges-2-representative-member-pairs/bits.graph.txt) | 404.136 | 3324 | 232585 | 6913.941 | 14 | 13 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/34-scc-divide-and-conquer-6-directed-edges-2-representative-member-pairs/bytes.cpp) · [graph](../../output/cornerstone-review/large/34-scc-divide-and-conquer-6-directed-edges-2-representative-member-pairs/bytes.graph.txt) | 44.308 | 351 | 34938 | 1428.153 | 14 | 13 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.
