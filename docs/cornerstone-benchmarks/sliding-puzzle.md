# Sliding puzzle

[All examples](README.md) · Runtime measurements are **milliseconds per evaluation**.

The five original Scala evaluator columns, two digit-set reference interpreters, and three C++ dialects are listed separately below. Large cases retain their two originally measured Scala evaluators. See the [measurement notes](README.md#measurement-notes) before comparing engines.

## sliding puzzle — 2x2 pure source full frontier step

Fixture family: `programs`. Result: **12 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 6.303 |  |
| ROG exec | 0.001 | precomputed literal |
| evalTrie | 4.848 |  |
| evalZ | 7.853 |  |
| ROG execT | 0.001 | precomputed literal |
| Zippy_bits reference | 48.906413 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 27.942936 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 8.004878 | symbol labels; standalone |
| C++ OpenFst bits | 71.054772 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 31.720151 | four-byte big-endian intern IDs; standalone |

Original benchmark note: compiled away; use compile+run.

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 3.029 |
| total compile | 3.029 |
| compile+ROG execT | 3.030 |
| source pass | 2.065 |
| lower+inline | 0.115 |
| graph optimize | 0.080 |
| graph transpile | 0.002 |
| const-fold eval | 1.274 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/16-sliding-puzzle-2x2-pure-source-full-frontier-step/symbols.cpp) | 1.979 | 13 | 19 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/16-sliding-puzzle-2x2-pure-source-full-frontier-step/bits.cpp) | 6.569 | 40 | 46 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/16-sliding-puzzle-2x2-pure-source-full-frontier-step/bytes.cpp) | 2.092 | 49 | 55 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## sliding puzzle — 2x2 pure compile-pass full frontier step

Fixture family: `programs`. Result: **12 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.001 |  |
| ROG exec | 0.001 | precomputed literal |
| evalTrie | 0.007 |  |
| evalZ | 0.007 |  |
| ROG execT | 0.001 | precomputed literal |
| Zippy_bits reference | 0.002945 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 0.003425 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.000005 | symbol labels; standalone |
| C++ OpenFst bits | 0.000005 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 0.000005 | four-byte big-endian intern IDs; standalone |

Original benchmark note: compiled away; use compile+run.

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | 10.074 |
| graph compile | 0.281 |
| total compile | 10.355 |
| compile+ROG execT | 10.356 |
| source pass | 9.347 |
| lower+inline | 0.267 |
| graph optimize | 0.085 |
| graph transpile | 0.004 |
| const-fold eval | 4.106 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/17-sliding-puzzle-2x2-pure-compile-pass-full-frontier-step/symbols.cpp) | 1.590 | 13 | 19 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/17-sliding-puzzle-2x2-pure-compile-pass-full-frontier-step/bits.cpp) | 1.313 | 40 | 46 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/17-sliding-puzzle-2x2-pure-compile-pass-full-frontier-step/bytes.cpp) | 1.253 | 49 | 55 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## sliding puzzle — 3x3 pure source depth-8 step

Fixture family: `programs`. Result: **420 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 440.023 |  |
| ROG exec | 0.002 | precomputed literal |
| evalTrie | 745.508 |  |
| evalZ | n/a | omitted by original benchmark (see note below) |
| ROG execT | 0.001 | precomputed literal |
| Zippy_bits reference | 7334.864319 | BigInt path sets; 1 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 3384.286054 | byte-array path sets; 1 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 1920.536003 | symbol labels; standalone |
| C++ OpenFst bits | 20807.285439 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 8300.112781 | four-byte big-endian intern IDs; standalone |

Original benchmark note: compiled away; use compile+run; direct evalZ source search tree exceeds the benchmark heap; use evalTrie/graph rows.

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 43.114 |
| total compile | 43.114 |
| compile+ROG execT | 43.116 |
| source pass | 41.603 |
| lower+inline | 0.114 |
| graph optimize | 0.089 |
| graph transpile | 0.003 |
| const-fold eval | 38.883 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/18-sliding-puzzle-3x3-pure-source-depth-8-step/symbols.cpp) | 2.581 | 419 | 721 | 1 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/18-sliding-puzzle-3x3-pure-source-depth-8-step/bits.cpp) | 26.751 | 1796 | 2082 | 1 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/18-sliding-puzzle-3x3-pure-source-depth-8-step/bytes.cpp) | 3.623 | 1673 | 1975 | 1 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## sliding puzzle — 3x3 pure compile-pass depth-8 step

Fixture family: `programs`. Result: **420 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.001 |  |
| ROG exec | 0.001 | precomputed literal |
| evalTrie | 0.397 |  |
| evalZ | 0.390 |  |
| ROG execT | 0.001 | precomputed literal |
| Zippy_bits reference | 0.003526 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 0.003675 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.000005 | symbol labels; standalone |
| C++ OpenFst bits | 0.000005 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 0.000005 | four-byte big-endian intern IDs; standalone |

Original benchmark note: compiled away; use compile+run.

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | 109.211 |
| graph compile | 0.346 |
| total compile | 109.558 |
| compile+ROG execT | 109.559 |
| source pass | 107.532 |
| lower+inline | 0.683 |
| graph optimize | 0.102 |
| graph transpile | 0.002 |
| const-fold eval | 93.028 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/19-sliding-puzzle-3x3-pure-compile-pass-depth-8-step/symbols.cpp) | 1.406 | 419 | 721 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/19-sliding-puzzle-3x3-pure-compile-pass-depth-8-step/bits.cpp) | 1.299 | 1796 | 2082 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/19-sliding-puzzle-3x3-pure-compile-pass-depth-8-step/bytes.cpp) | 2.022 | 1673 | 1975 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## sliding puzzle — 4x4 pure source depth-5 step

Fixture family: `programs`. Result: **202 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 604.890 |  |
| ROG exec | 0.028 | precomputed literal |
| evalTrie | 1355.264 |  |
| evalZ | n/a | omitted by original benchmark (see note below) |
| ROG execT | 0.007 | precomputed literal |
| Zippy_bits reference | 12558.359001 | BigInt path sets; 1 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 5560.672841 | byte-array path sets; 1 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 3512.808310 | symbol labels; standalone |
| C++ OpenFst bits | 43913.322676 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 15722.868579 | four-byte big-endian intern IDs; standalone |

Original benchmark note: compiled away; use compile+run; direct evalZ source search tree exceeds the benchmark heap; use evalTrie/graph rows.

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 73.933 |
| total compile | 73.933 |
| compile+ROG execT | 73.940 |
| source pass | 70.462 |
| lower+inline | 0.386 |
| graph optimize | 0.212 |
| graph transpile | 0.004 |
| const-fold eval | 56.715 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/20-sliding-puzzle-4x4-pure-source-depth-5-step/symbols.cpp) | 6.659 | 456 | 606 | 1 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/20-sliding-puzzle-4x4-pure-source-depth-5-step/bits.cpp) | 152.396 | 2260 | 2408 | 1 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/20-sliding-puzzle-4x4-pure-source-depth-5-step/bytes.cpp) | 15.153 | 1821 | 1971 | 1 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## Focused constant-fold evaluator sweep

This separate compile-time experiment selects Reference, Trie, Zipper, ExecT, or Hybrid as the constant-folding engine. These are compile times, not additional runtime measurements. All durations are milliseconds.

| benchmark | backend | compile ms | const-fold total ms | eval ms | evalTrie ms | evalZ ms | execT ms |
| --- | --- | --- | --- | --- | --- | --- | --- |
| sliding puzzle 3x3 pure compile-pass depth-8 step | Reference | 38.906 | 35.844 | 35.844 | 0.000 | 0.000 | 0.000 |
| sliding puzzle 3x3 pure compile-pass depth-8 step | Trie | 228.907 | 225.662 | 0.000 | 225.662 | 0.000 | 0.000 |
| sliding puzzle 3x3 pure compile-pass depth-8 step | Zipper | 524.391 | 521.483 | 0.000 | 0.000 | 521.483 | 0.000 |
| sliding puzzle 3x3 pure compile-pass depth-8 step | ExecT | 58.462 | 55.678 | 0.000 | 0.000 | 0.000 | 55.678 |
| sliding puzzle 3x3 pure compile-pass depth-8 step | Hybrid | 42.382 | 39.346 | 39.346 | 0.000 | 0.000 | 0.000 |
