# Temperature

[All examples](README.md) · Runtime measurements are **milliseconds per evaluation**.

The five original Scala evaluator columns, two digit-set reference interpreters, and three C++ dialects are listed separately below. Large cases retain their two originally measured Scala evaluators. See the [measurement notes](README.md#measurement-notes) before comparing engines.

## temperature — NOAA committed slice

Fixture family: `programs`. Result: **12 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.003 |  |
| ROG exec | 0.003 |  |
| evalTrie | 0.014 |  |
| evalZ | 0.026 |  |
| ROG execT | 0.010 |  |
| Zippy_bits reference | 0.093220 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 0.476034 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.008457 | symbol labels; standalone; optimized graph |
| C++ OpenFst bits | 0.022673 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 0.042838 | four-byte big-endian intern IDs; standalone; optimized graph |

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 0.655 |
| total compile | 0.655 |
| compile+ROG execT | 0.665 |
| source pass | 0.103 |
| lower+inline | 0.036 |
| graph optimize | 0.202 |
| graph transpile | 0.011 |
| const-fold eval | 0.009 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/14-temperature-noaa-committed-slice/symbols.cpp) · [graph](../../output/cornerstone-review/programs/14-temperature-noaa-committed-slice/symbols.graph.txt) | 1.140 | 3 | 885 | 448.385 | 49 | 59 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/14-temperature-noaa-committed-slice/bits.cpp) · [graph](../../output/cornerstone-review/programs/14-temperature-noaa-committed-slice/bits.graph.txt) | 0.897 | 3 | 982 | 365.444 | 121 | 131 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/14-temperature-noaa-committed-slice/bytes.cpp) · [graph](../../output/cornerstone-review/programs/14-temperature-noaa-committed-slice/bytes.graph.txt) | 1.088 | 3 | 1034 | 389.677 | 193 | 203 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## temperature — synthetic 32x64

Fixture family: `programs`. Result: **448 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 1.208 |  |
| ROG exec | 1.107 |  |
| evalTrie | 0.019 |  |
| evalZ | 0.060 |  |
| ROG execT | 0.010 |  |
| Zippy_bits reference | 5.292709 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 64.194192 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.066697 | symbol labels; standalone; optimized graph |
| C++ OpenFst bits | 0.199600 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 0.318322 | four-byte big-endian intern IDs; standalone; optimized graph |

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 0.505 |
| total compile | 0.505 |
| compile+ROG execT | 0.515 |
| source pass | 0.081 |
| lower+inline | 0.026 |
| graph optimize | 0.118 |
| graph transpile | 0.005 |
| const-fold eval | 0.014 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/15-temperature-synthetic-32x64/symbols.cpp) · [graph](../../output/cornerstone-review/programs/15-temperature-synthetic-32x64/symbols.graph.txt) | 1.012 | 3 | 1348 | 494.191 | 258 | 375 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/15-temperature-synthetic-32x64/bits.cpp) · [graph](../../output/cornerstone-review/programs/15-temperature-synthetic-32x64/bits.graph.txt) | 3.614 | 3 | 2101 | 533.770 | 665 | 782 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/15-temperature-synthetic-32x64/bytes.cpp) · [graph](../../output/cornerstone-review/programs/15-temperature-synthetic-32x64/bytes.graph.txt) | 1.456 | 3 | 2745 | 534.105 | 1029 | 1146 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.
