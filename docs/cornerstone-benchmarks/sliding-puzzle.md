# Sliding puzzle

[All examples](README.md) · Runtime measurements are **milliseconds per evaluation**.

The five original Scala evaluator columns, two digit-set reference interpreters, and three C++ dialects are listed separately below. Large cases retain their two originally measured Scala evaluators. See the [measurement notes](README.md#measurement-notes) before comparing engines.

## sliding puzzle — 2x2 pure source full frontier step

Fixture family: `programs`. Result: **12 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 2.017 |  |
| ROG exec | 0.001 | precomputed literal |
| evalTrie | 4.811 |  |
| evalZ | 7.608 |  |
| ROG execT | 0.001 | precomputed literal |
| Zippy_bits reference | 48.963295 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 32.874061 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.000006 | symbol labels; standalone; precomputed literal |
| C++ OpenFst bits | 31.732589 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 15.345618 | four-byte big-endian intern IDs; standalone; optimized graph |

Original benchmark note: compiled away; use compile+run.

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 3.486 |
| total compile | 3.486 |
| compile+ROG execT | 3.487 |
| source pass | 1.709 |
| lower+inline | 0.631 |
| graph optimize | 0.120 |
| graph transpile | 0.004 |
| const-fold eval | 0.677 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/16-sliding-puzzle-2x2-pure-source-full-frontier-step/symbols.cpp) · [graph](../../output/cornerstone-review/programs/16-sliding-puzzle-2x2-pure-source-full-frontier-step/symbols.graph.txt) | 4.561 | 1 | 859 | 3.067 | 13 | 19 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/16-sliding-puzzle-2x2-pure-source-full-frontier-step/bits.cpp) · [graph](../../output/cornerstone-review/programs/16-sliding-puzzle-2x2-pure-source-full-frontier-step/bits.graph.txt) | 909.633 | 13879 | 977398 | 31103.432 | 144 | 143 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/16-sliding-puzzle-2x2-pure-source-full-frontier-step/bytes.cpp) · [graph](../../output/cornerstone-review/programs/16-sliding-puzzle-2x2-pure-source-full-frontier-step/bytes.graph.txt) | 64.226 | 639 | 66085 | 2544.752 | 128 | 127 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## sliding puzzle — 2x2 pure compile-pass full frontier step

Fixture family: `programs`. Result: **12 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.001 |  |
| ROG exec | 0.001 | precomputed literal |
| evalTrie | 0.013 |  |
| evalZ | 0.007 |  |
| ROG execT | 0.001 | precomputed literal |
| Zippy_bits reference | 0.002984 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 0.003555 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.000006 | symbol labels; standalone; precomputed literal |
| C++ OpenFst bits | 0.000006 | Fibonacci-coded intern IDs; standalone; precomputed literal |
| C++ OpenFst bytes | 0.000006 | four-byte big-endian intern IDs; standalone; precomputed literal |

Original benchmark note: compiled away; use compile+run.

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | 15.568 |
| graph compile | 0.434 |
| total compile | 16.002 |
| compile+ROG execT | 16.003 |
| source pass | 12.409 |
| lower+inline | 2.539 |
| graph optimize | 0.125 |
| graph transpile | 0.003 |
| const-fold eval | 4.326 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/17-sliding-puzzle-2x2-pure-compile-pass-full-frontier-step/symbols.cpp) · [graph](../../output/cornerstone-review/programs/17-sliding-puzzle-2x2-pure-compile-pass-full-frontier-step/symbols.graph.txt) | 0.528 | 1 | 859 | 382.392 | 13 | 19 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/17-sliding-puzzle-2x2-pure-compile-pass-full-frontier-step/bits.cpp) · [graph](../../output/cornerstone-review/programs/17-sliding-puzzle-2x2-pure-compile-pass-full-frontier-step/bits.graph.txt) | 1.211 | 1 | 1082 | 399.137 | 40 | 46 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/17-sliding-puzzle-2x2-pure-compile-pass-full-frontier-step/bytes.cpp) · [graph](../../output/cornerstone-review/programs/17-sliding-puzzle-2x2-pure-compile-pass-full-frontier-step/bytes.graph.txt) | 1.287 | 1 | 1100 | 531.274 | 49 | 55 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## sliding puzzle — 3x3 pure source depth-8 step

Fixture family: `programs`. Result: **420 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 358.626 |  |
| ROG exec | 0.003 | precomputed literal |
| evalTrie | 912.922 |  |
| evalZ | 1999.178900 | additional source run: measured; 192g heap, 600 s limit; exact check on success |
| ROG execT | 0.001 | precomputed literal |
| Zippy_bits reference | 7562.843162 | BigInt path sets; 1 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 3525.220154 | byte-array path sets; 1 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.000006 | symbol labels; standalone; precomputed literal |
| C++ OpenFst bits | 3223.566853 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 762.885168 | four-byte big-endian intern IDs; standalone; optimized graph |

Original benchmark note: compiled away; use compile+run; direct evalZ source search tree exceeds the benchmark heap; use evalTrie/graph rows.

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 38.665 |
| total compile | 38.665 |
| compile+ROG execT | 38.666 |
| source pass | 35.644 |
| lower+inline | 0.994 |
| graph optimize | 0.112 |
| graph transpile | 0.006 |
| const-fold eval | 32.449 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/18-sliding-puzzle-3x3-pure-source-depth-8-step/symbols.cpp) · [graph](../../output/cornerstone-review/programs/18-sliding-puzzle-3x3-pure-source-depth-8-step/symbols.graph.txt) | 68.637 | 1 | 9674 | 401.341 | 419 | 721 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/18-sliding-puzzle-3x3-pure-source-depth-8-step/bits.cpp) · [graph](../../output/cornerstone-review/programs/18-sliding-puzzle-3x3-pure-source-depth-8-step/bits.graph.txt) | 5384.332 | 73023 | 5195957 | 25858.821 | 11146 | 11145 | 1 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/18-sliding-puzzle-3x3-pure-source-depth-8-step/bytes.cpp) · [graph](../../output/cornerstone-review/programs/18-sliding-puzzle-3x3-pure-source-depth-8-step/bytes.graph.txt) | 324.548 | 2363 | 250534 | 6681.973 | 7904 | 7903 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## sliding puzzle — 3x3 pure compile-pass depth-8 step

Fixture family: `programs`. Result: **420 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.002 |  |
| ROG exec | 0.001 | precomputed literal |
| evalTrie | 0.325 |  |
| evalZ | 0.388 |  |
| ROG execT | 0.001 | precomputed literal |
| Zippy_bits reference | 0.003295 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 0.010345 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.000006 | symbol labels; standalone; precomputed literal |
| C++ OpenFst bits | 0.000007 | Fibonacci-coded intern IDs; standalone; precomputed literal |
| C++ OpenFst bytes | 0.000009 | four-byte big-endian intern IDs; standalone; precomputed literal |

Original benchmark note: compiled away; use compile+run.

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | 126.907 |
| graph compile | 0.472 |
| total compile | 127.379 |
| compile+ROG execT | 127.380 |
| source pass | 120.538 |
| lower+inline | 4.006 |
| graph optimize | 0.146 |
| graph transpile | 0.003 |
| const-fold eval | 105.999 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/19-sliding-puzzle-3x3-pure-compile-pass-depth-8-step/symbols.cpp) · [graph](../../output/cornerstone-review/programs/19-sliding-puzzle-3x3-pure-compile-pass-depth-8-step/symbols.graph.txt) | 2.210 | 1 | 9674 | 3.002 | 419 | 721 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/19-sliding-puzzle-3x3-pure-compile-pass-depth-8-step/bits.cpp) · [graph](../../output/cornerstone-review/programs/19-sliding-puzzle-3x3-pure-compile-pass-depth-8-step/bits.graph.txt) | 20.326 | 1 | 39055 | 480.770 | 1796 | 2082 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/19-sliding-puzzle-3x3-pure-compile-pass-depth-8-step/bytes.cpp) · [graph](../../output/cornerstone-review/programs/19-sliding-puzzle-3x3-pure-compile-pass-depth-8-step/bytes.graph.txt) | 13.086 | 1 | 32564 | 504.383 | 1673 | 1975 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## sliding puzzle — 4x4 pure source depth-5 step

Fixture family: `programs`. Result: **202 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 578.346 |  |
| ROG exec | 0.045 | precomputed literal |
| evalTrie | 2088.411 |  |
| evalZ | 6585.874368 | additional source run: measured; 192g heap, 600 s limit; exact check on success |
| ROG execT | 0.009 | precomputed literal |
| Zippy_bits reference | 13832.728186 | BigInt path sets; 1 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 5347.273505 | byte-array path sets; 1 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.000006 | symbol labels; standalone; precomputed literal |
| C++ OpenFst bits | 4195.090770 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 866.665058 | four-byte big-endian intern IDs; standalone; optimized graph |

Original benchmark note: compiled away; use compile+run; direct evalZ source search tree exceeds the benchmark heap; use evalTrie/graph rows.

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 58.095 |
| total compile | 58.095 |
| compile+ROG execT | 58.104 |
| source pass | 52.233 |
| lower+inline | 2.933 |
| graph optimize | 0.175 |
| graph transpile | 0.006 |
| const-fold eval | 41.009 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/20-sliding-puzzle-4x4-pure-source-depth-5-step/symbols.cpp) · [graph](../../output/cornerstone-review/programs/20-sliding-puzzle-4x4-pure-source-depth-5-step/symbols.graph.txt) | 94.548 | 1 | 9747 | 497.885 | 456 | 606 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/20-sliding-puzzle-4x4-pure-source-depth-5-step/bits.cpp) · [graph](../../output/cornerstone-review/programs/20-sliding-puzzle-4x4-pure-source-depth-5-step/bits.graph.txt) | 63867.932 | 257564 | 18305351 | 84396.669 | 13398 | 13397 | 1 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/20-sliding-puzzle-4x4-pure-source-depth-5-step/bytes.cpp) · [graph](../../output/cornerstone-review/programs/20-sliding-puzzle-4x4-pure-source-depth-5-step/bytes.graph.txt) | 531.541 | 7097 | 737869 | 22110.821 | 8486 | 8485 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## Focused constant-fold evaluator sweep

This separate compile-time experiment selects Reference, Trie, Zipper, ExecT, or Hybrid as the constant-folding engine. These are compile times, not additional runtime measurements. All durations are milliseconds.

| benchmark | backend | compile ms | const-fold total ms | eval ms | evalTrie ms | evalZ ms | execT ms |
| --- | --- | --- | --- | --- | --- | --- | --- |
| sliding puzzle 3x3 pure compile-pass depth-8 step | Reference | 34.375 | 30.424 | 30.424 | 0.000 | 0.000 | 0.000 |
| sliding puzzle 3x3 pure compile-pass depth-8 step | Trie | 340.377 | 335.644 | 0.000 | 335.644 | 0.000 | 0.000 |
| sliding puzzle 3x3 pure compile-pass depth-8 step | Zipper | 562.048 | 558.369 | 0.000 | 0.000 | 558.369 | 0.000 |
| sliding puzzle 3x3 pure compile-pass depth-8 step | ExecT | 64.836 | 60.881 | 0.000 | 0.000 | 0.000 | 60.881 |
| sliding puzzle 3x3 pure compile-pass depth-8 step | Hybrid | 57.801 | 52.070 | 52.070 | 0.000 | 0.000 | 0.000 |
