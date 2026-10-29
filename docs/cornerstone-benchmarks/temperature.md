# Temperature

[All examples](README.md) · Runtime measurements are **milliseconds per evaluation**.

The five original Scala evaluator columns, two digit-set reference interpreters, and three C++ dialects are listed separately below. Large cases retain their two originally measured Scala evaluators. See the [measurement notes](README.md#measurement-notes) before comparing engines.

## temperature — NOAA committed slice

Fixture family: `programs`. Result: **12 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.006 |  |
| ROG exec | 0.003 |  |
| evalTrie | 0.011 |  |
| evalZ | 0.025 |  |
| ROG execT | 0.010 |  |
| Zippy_bits reference | 0.055914 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 0.447741 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.008732 | symbol labels; standalone |
| C++ OpenFst bits | 0.023636 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 0.058035 | four-byte big-endian intern IDs; standalone |

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 0.282 |
| total compile | 0.282 |
| compile+ROG execT | 0.292 |
| source pass | 0.051 |
| lower+inline | 0.019 |
| graph optimize | 0.059 |
| graph transpile | 0.004 |
| const-fold eval | 0.006 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/14-temperature-noaa-committed-slice/symbols.cpp) | 1.348 | 49 | 59 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/14-temperature-noaa-committed-slice/bits.cpp) | 1.455 | 121 | 131 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/14-temperature-noaa-committed-slice/bytes.cpp) | 1.326 | 193 | 203 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## temperature — synthetic 32x64

Fixture family: `programs`. Result: **448 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 1.128 |  |
| ROG exec | 1.125 |  |
| evalTrie | 0.016 |  |
| evalZ | 0.046 |  |
| ROG execT | 0.011 |  |
| Zippy_bits reference | 2.488583 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 61.061046 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.084735 | symbol labels; standalone |
| C++ OpenFst bits | 0.245882 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 0.399011 | four-byte big-endian intern IDs; standalone |

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 0.171 |
| total compile | 0.171 |
| compile+ROG execT | 0.182 |
| source pass | 0.039 |
| lower+inline | 0.008 |
| graph optimize | 0.041 |
| graph transpile | 0.004 |
| const-fold eval | 0.007 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/15-temperature-synthetic-32x64/symbols.cpp) | 1.626 | 258 | 375 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/15-temperature-synthetic-32x64/bits.cpp) | 2.081 | 665 | 782 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/15-temperature-synthetic-32x64/bytes.cpp) | 1.690 | 1029 | 1146 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.
