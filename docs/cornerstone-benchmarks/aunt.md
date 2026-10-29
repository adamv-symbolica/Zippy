# Aunt queries

[All examples](README.md) · Runtime measurements are **milliseconds per evaluation**.

The five original Scala evaluator columns, two digit-set reference interpreters, and three C++ dialects are listed separately below. Large cases retain their two originally measured Scala evaluators. See the [measurement notes](README.md#measurement-notes) before comparing engines.

## aunt — reference

Fixture family: `programs`. Result: **3 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.108 |  |
| ROG exec | 0.190 |  |
| evalTrie | 0.439 |  |
| evalZ | 0.683 |  |
| ROG execT | 0.310 |  |
| Zippy_bits reference | 3.084267 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 3.062294 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.038954 | symbol labels; standalone |
| C++ OpenFst bits | 0.201241 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 0.099896 | four-byte big-endian intern IDs; standalone |

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 4.822 |
| total compile | 4.822 |
| compile+ROG execT | 5.132 |
| source pass | 0.748 |
| lower+inline | 0.101 |
| graph optimize | 1.765 |
| graph transpile | 0.175 |
| const-fold eval | 0.000 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/01-aunt-reference/symbols.cpp) | 27.657 | 5 | 6 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/01-aunt-reference/bits.cpp) | 4.795 | 20 | 21 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/01-aunt-reference/bytes.cpp) | 12.440 | 17 | 18 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## aunt — process-sc static family

Fixture family: `programs`. Result: **3 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.075 |  |
| ROG exec | 0.175 |  |
| evalTrie | 0.238 |  |
| evalZ | 0.547 |  |
| ROG execT | 0.161 |  |
| Zippy_bits reference | 3.228253 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 1.708322 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.027977 | symbol labels; standalone |
| C++ OpenFst bits | 0.170976 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 0.075612 | four-byte big-endian intern IDs; standalone |

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | 49.000 |
| graph compile | 1.600 |
| total compile | 50.600 |
| compile+ROG execT | 50.761 |
| source pass | 0.377 |
| lower+inline | 0.097 |
| graph optimize | 0.674 |
| graph transpile | 0.061 |
| const-fold eval | 0.003 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/02-aunt-process-sc-static-family/symbols.cpp) | 24.537 | 5 | 6 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/02-aunt-process-sc-static-family/bits.cpp) | 4.539 | 19 | 20 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/02-aunt-process-sc-static-family/bytes.cpp) | 13.782 | 17 | 18 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## aunt synthetic — reference 60 people

Fixture family: `programs`. Result: **36 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 2.849 |  |
| ROG exec | 1.411 |  |
| evalTrie | 0.864 |  |
| evalZ | 2.323 |  |
| ROG execT | 1.147 |  |
| Zippy_bits reference | 15.750467 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 39.320764 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 1.443358 | symbol labels; standalone |
| C++ OpenFst bits | 10.912739 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 3.709399 | four-byte big-endian intern IDs; standalone |

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 2.080 |
| total compile | 2.080 |
| compile+ROG execT | 3.227 |
| source pass | 0.511 |
| lower+inline | 0.041 |
| graph optimize | 1.034 |
| graph transpile | 0.113 |
| const-fold eval | 0.000 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/03-aunt-synthetic-reference-60-people/symbols.cpp) | 33.578 | 21 | 55 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/03-aunt-synthetic-reference-60-people/bits.cpp) | 5.378 | 137 | 171 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/03-aunt-synthetic-reference-60-people/bytes.cpp) | 1.788 | 81 | 115 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## aunt royal92 — reference royal92_simple.metta fallback

Fixture family: `programs`. Result: **3 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.055 |  |
| ROG exec | 0.058 |  |
| evalTrie | 0.073 |  |
| evalZ | 0.103 |  |
| ROG execT | 0.102 |  |
| Zippy_bits reference | 3.261332 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 3.210396 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.039430 | symbol labels; standalone |
| C++ OpenFst bits | 0.197498 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 0.101761 | four-byte big-endian intern IDs; standalone |

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 2.260 |
| total compile | 2.260 |
| compile+ROG execT | 2.361 |
| source pass | 0.414 |
| lower+inline | 0.041 |
| graph optimize | 1.052 |
| graph transpile | 0.336 |
| const-fold eval | 0.000 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/04-aunt-royal92-reference-royal92-simple-metta-fallback/symbols.cpp) | 37.751 | 5 | 6 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/04-aunt-royal92-reference-royal92-simple-metta-fallback/bits.cpp) | 4.759 | 20 | 21 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/04-aunt-royal92-reference-royal92-simple-metta-fallback/bytes.cpp) | 1.785 | 17 | 18 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## aunt synthetic — process-sc static family 24 people

Fixture family: `programs`. Result: **12 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.126 |  |
| ROG exec | 0.323 |  |
| evalTrie | 1.151 |  |
| evalZ | 1.208 |  |
| ROG execT | 0.244 |  |
| Zippy_bits reference | 5.761062 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 5.595724 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.227016 | symbol labels; standalone |
| C++ OpenFst bits | 1.521830 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 0.424859 | four-byte big-endian intern IDs; standalone |

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | 7.975 |
| graph compile | 1.881 |
| total compile | 9.856 |
| compile+ROG execT | 10.100 |
| source pass | 0.501 |
| lower+inline | 0.057 |
| graph optimize | 0.895 |
| graph transpile | 0.126 |
| const-fold eval | 0.003 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/05-aunt-synthetic-process-sc-static-family-24-people/symbols.cpp) | 31.909 | 9 | 19 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/05-aunt-synthetic-process-sc-static-family-24-people/bits.cpp) | 5.737 | 56 | 65 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/05-aunt-synthetic-process-sc-static-family-24-people/bytes.cpp) | 1.955 | 33 | 43 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## aunt query over large generated family, one queried person — 9 generations x 80 people (2720 facts)

Fixture family: `large`. Result: **0 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 0.088 |  |
| evalZ | 0.140 |  |
| C++ OpenFst symbols | 0.057264 | symbol labels; standalone |
| C++ OpenFst bits | 0.620781 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 0.294096 | four-byte big-endian intern IDs; standalone |

Original benchmark note: large query-shaped dataset with small queried person set.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/32-aunt-query-over-large-generated-family-one-queried-person-9-generations-x-80-people-2720-facts/symbols.cpp) | 2.743 | 0 | 0 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/large/32-aunt-query-over-large-generated-family-one-queried-person-9-generations-x-80-people-2720-facts/bits.cpp) | 8.972 | 0 | 0 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/32-aunt-query-over-large-generated-family-one-queried-person-9-generations-x-80-people-2720-facts/bytes.cpp) | 2.065 | 0 | 0 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## aunt query over large generated family, one queried person — 11 generations x 160 people (6720 facts)

Fixture family: `large`. Result: **0 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 0.047 |  |
| evalZ | 0.089 |  |
| C++ OpenFst symbols | 0.215297 | symbol labels; standalone |
| C++ OpenFst bits | 1.841576 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 0.795933 | four-byte big-endian intern IDs; standalone |

Original benchmark note: large query-shaped dataset with small queried person set.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/33-aunt-query-over-large-generated-family-one-queried-person-11-generations-x-160-people-6720-facts/symbols.cpp) | 2.806 | 0 | 0 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/large/33-aunt-query-over-large-generated-family-one-queried-person-11-generations-x-160-people-6720-facts/bits.cpp) | 8.216 | 0 | 0 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/33-aunt-query-over-large-generated-family-one-queried-person-11-generations-x-160-people-6720-facts/bytes.cpp) | 1.961 | 0 | 0 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.
