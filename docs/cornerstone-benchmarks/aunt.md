# Aunt queries

[All examples](README.md) · Runtime measurements are **milliseconds per evaluation**.

The five original Scala evaluator columns, two digit-set reference interpreters, and three C++ dialects are listed separately below. Large cases retain their two originally measured Scala evaluators. See the [measurement notes](README.md#measurement-notes) before comparing engines.

## aunt — reference

Fixture family: `programs`. Result: **3 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.128 |  |
| ROG exec | 0.199 |  |
| evalTrie | 0.459 |  |
| evalZ | 0.843 |  |
| ROG execT | 0.356 |  |
| Zippy_bits reference | 3.756084 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 3.620351 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.013213 | symbol labels; standalone; optimized graph |
| C++ OpenFst bits | 0.090156 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 0.039540 | four-byte big-endian intern IDs; standalone; optimized graph |

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 7.523 |
| total compile | 7.523 |
| compile+ROG execT | 7.879 |
| source pass | 0.847 |
| lower+inline | 0.444 |
| graph optimize | 1.854 |
| graph transpile | 0.369 |
| const-fold eval | 0.000 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/01-aunt-reference/symbols.cpp) · [graph](../../output/cornerstone-review/programs/01-aunt-reference/symbols.graph.txt) | 18.699 | 20 | 1931 | 11644.274 | 8 | 7 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/01-aunt-reference/bits.cpp) · [graph](../../output/cornerstone-review/programs/01-aunt-reference/bits.graph.txt) | 161.450 | 1356 | 92307 | 2179.719 | 28 | 27 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/01-aunt-reference/bytes.cpp) · [graph](../../output/cornerstone-review/programs/01-aunt-reference/bytes.graph.txt) | 7.815 | 48 | 5875 | 803.057 | 23 | 22 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## aunt — process-sc static family

Fixture family: `programs`. Result: **3 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.068 |  |
| ROG exec | 0.145 |  |
| evalTrie | 0.238 |  |
| evalZ | 1.101 |  |
| ROG execT | 0.192 |  |
| Zippy_bits reference | 5.452649 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 2.225939 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.013327 | symbol labels; standalone; optimized graph |
| C++ OpenFst bits | 0.086400 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 0.038206 | four-byte big-endian intern IDs; standalone; optimized graph |

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | 106.467 |
| graph compile | 4.601 |
| total compile | 111.069 |
| compile+ROG execT | 111.261 |
| source pass | 1.526 |
| lower+inline | 0.445 |
| graph optimize | 1.175 |
| graph transpile | 0.214 |
| const-fold eval | 0.019 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/02-aunt-process-sc-static-family/symbols.cpp) · [graph](../../output/cornerstone-review/programs/02-aunt-process-sc-static-family/symbols.graph.txt) | 20.327 | 19 | 1934 | 11636.814 | 8 | 7 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/02-aunt-process-sc-static-family/bits.cpp) · [graph](../../output/cornerstone-review/programs/02-aunt-process-sc-static-family/bits.graph.txt) | 149.961 | 1134 | 77346 | 1880.733 | 27 | 26 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/02-aunt-process-sc-static-family/bytes.cpp) · [graph](../../output/cornerstone-review/programs/02-aunt-process-sc-static-family/bytes.graph.txt) | 8.143 | 46 | 5963 | 748.640 | 23 | 22 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## aunt synthetic — reference 60 people

Fixture family: `programs`. Result: **36 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 3.109 |  |
| ROG exec | 1.152 |  |
| evalTrie | 2.307 |  |
| evalZ | 2.675 |  |
| ROG execT | 0.823 |  |
| Zippy_bits reference | 38.478928 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 48.952068 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.344329 | symbol labels; standalone; optimized graph |
| C++ OpenFst bits | 5.100275 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 0.806540 | four-byte big-endian intern IDs; standalone; optimized graph |

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 2.785 |
| total compile | 2.785 |
| compile+ROG execT | 3.608 |
| source pass | 0.667 |
| lower+inline | 0.220 |
| graph optimize | 1.128 |
| graph transpile | 0.210 |
| const-fold eval | 0.000 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/03-aunt-synthetic-reference-60-people/symbols.cpp) · [graph](../../output/cornerstone-review/programs/03-aunt-synthetic-reference-60-people/symbols.graph.txt) | 30.361 | 20 | 1931 | 11013.578 | 74 | 73 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/03-aunt-synthetic-reference-60-people/bits.cpp) · [graph](../../output/cornerstone-review/programs/03-aunt-synthetic-reference-60-people/bits.graph.txt) | 228.111 | 2136 | 144940 | 3002.515 | 465 | 464 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/03-aunt-synthetic-reference-60-people/bytes.cpp) · [graph](../../output/cornerstone-review/programs/03-aunt-synthetic-reference-60-people/bytes.graph.txt) | 8.230 | 48 | 5875 | 856.018 | 188 | 187 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## aunt royal92 — reference royal92_simple.metta fallback

Fixture family: `programs`. Result: **3 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.055 |  |
| ROG exec | 0.064 |  |
| evalTrie | 0.086 |  |
| evalZ | 0.136 |  |
| ROG execT | 0.040 |  |
| Zippy_bits reference | 5.029495 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 5.345388 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.013511 | symbol labels; standalone; optimized graph |
| C++ OpenFst bits | 0.087514 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 0.037609 | four-byte big-endian intern IDs; standalone; optimized graph |

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 2.795 |
| total compile | 2.795 |
| compile+ROG execT | 2.835 |
| source pass | 0.526 |
| lower+inline | 0.182 |
| graph optimize | 0.974 |
| graph transpile | 0.495 |
| const-fold eval | 0.000 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/04-aunt-royal92-reference-royal92-simple-metta-fallback/symbols.cpp) · [graph](../../output/cornerstone-review/programs/04-aunt-royal92-reference-royal92-simple-metta-fallback/symbols.graph.txt) | 17.921 | 20 | 1931 | 10951.438 | 8 | 7 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/04-aunt-royal92-reference-royal92-simple-metta-fallback/bits.cpp) · [graph](../../output/cornerstone-review/programs/04-aunt-royal92-reference-royal92-simple-metta-fallback/bits.graph.txt) | 202.761 | 1356 | 92307 | 2143.751 | 28 | 27 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/04-aunt-royal92-reference-royal92-simple-metta-fallback/bytes.cpp) · [graph](../../output/cornerstone-review/programs/04-aunt-royal92-reference-royal92-simple-metta-fallback/bytes.graph.txt) | 8.070 | 48 | 5875 | 807.198 | 23 | 22 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## aunt synthetic — process-sc static family 24 people

Fixture family: `programs`. Result: **12 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.146 |  |
| ROG exec | 0.239 |  |
| evalTrie | 2.959 |  |
| evalZ | 2.279 |  |
| ROG execT | 0.172 |  |
| Zippy_bits reference | 8.811247 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 6.123626 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.107909 | symbol labels; standalone; optimized graph |
| C++ OpenFst bits | 1.070819 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 0.270311 | four-byte big-endian intern IDs; standalone; optimized graph |

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | 12.452 |
| graph compile | 2.896 |
| total compile | 15.349 |
| compile+ROG execT | 15.520 |
| source pass | 0.976 |
| lower+inline | 0.253 |
| graph optimize | 1.032 |
| graph transpile | 0.176 |
| const-fold eval | 0.025 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/05-aunt-synthetic-process-sc-static-family-24-people/symbols.cpp) · [graph](../../output/cornerstone-review/programs/05-aunt-synthetic-process-sc-static-family-24-people/symbols.graph.txt) | 6.961 | 19 | 2745 | 546.234 | 26 | 25 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/05-aunt-synthetic-process-sc-static-family-24-people/bits.cpp) · [graph](../../output/cornerstone-review/programs/05-aunt-synthetic-process-sc-static-family-24-people/bits.graph.txt) | 110.715 | 1632 | 112824 | 2468.878 | 139 | 138 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/05-aunt-synthetic-process-sc-static-family-24-people/bytes.cpp) · [graph](../../output/cornerstone-review/programs/05-aunt-synthetic-process-sc-static-family-24-people/bytes.graph.txt) | 6.428 | 46 | 7230 | 700.033 | 68 | 67 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## aunt query over large generated family, one queried person — 9 generations x 80 people (2720 facts)

Fixture family: `large`. Result: **0 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 0.135 |  |
| evalZ | 0.182 |  |
| C++ OpenFst symbols | 0.000266 | symbol labels; standalone; optimized graph |
| C++ OpenFst bits | 0.010444 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 0.000648 | four-byte big-endian intern IDs; standalone; optimized graph |

Original benchmark note: large query-shaped dataset with small queried person set.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/32-aunt-query-over-large-generated-family-one-queried-person-9-generations-x-80-people-2720-facts/symbols.cpp) · [graph](../../output/cornerstone-review/large/32-aunt-query-over-large-generated-family-one-queried-person-9-generations-x-80-people-2720-facts/symbols.graph.txt) | 4.276 | 20 | 1931 | 2.161 | 0 | 0 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/large/32-aunt-query-over-large-generated-family-one-queried-person-9-generations-x-80-people-2720-facts/bits.cpp) · [graph](../../output/cornerstone-review/large/32-aunt-query-over-large-generated-family-one-queried-person-9-generations-x-80-people-2720-facts/bits.graph.txt) | 184.439 | 3352 | 227055 | 4317.129 | 0 | 0 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/32-aunt-query-over-large-generated-family-one-queried-person-9-generations-x-80-people-2720-facts/bytes.cpp) · [graph](../../output/cornerstone-review/large/32-aunt-query-over-large-generated-family-one-queried-person-9-generations-x-80-people-2720-facts/bytes.graph.txt) | 4.244 | 48 | 5879 | 711.792 | 0 | 0 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## aunt query over large generated family, one queried person — 11 generations x 160 people (6720 facts)

Fixture family: `large`. Result: **0 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 0.042 |  |
| evalZ | 0.093 |  |
| C++ OpenFst symbols | 0.000262 | symbol labels; standalone; optimized graph |
| C++ OpenFst bits | 0.011392 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 0.000672 | four-byte big-endian intern IDs; standalone; optimized graph |

Original benchmark note: large query-shaped dataset with small queried person set.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/33-aunt-query-over-large-generated-family-one-queried-person-11-generations-x-160-people-6720-facts/symbols.cpp) · [graph](../../output/cornerstone-review/large/33-aunt-query-over-large-generated-family-one-queried-person-11-generations-x-160-people-6720-facts/symbols.graph.txt) | 4.097 | 20 | 1931 | 1.823 | 0 | 0 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/large/33-aunt-query-over-large-generated-family-one-queried-person-11-generations-x-160-people-6720-facts/bits.cpp) · [graph](../../output/cornerstone-review/large/33-aunt-query-over-large-generated-family-one-queried-person-11-generations-x-160-people-6720-facts/bits.graph.txt) | 188.384 | 3851 | 260743 | 4798.407 | 0 | 0 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/33-aunt-query-over-large-generated-family-one-queried-person-11-generations-x-160-people-6720-facts/bytes.cpp) · [graph](../../output/cornerstone-review/large/33-aunt-query-over-large-generated-family-one-queried-person-11-generations-x-160-people-6720-facts/bytes.graph.txt) | 4.864 | 48 | 5879 | 819.228 | 0 | 0 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.
