# Game of Life

[All examples](README.md) · Runtime measurements are **milliseconds per evaluation**.

The five original Scala evaluator columns, two digit-set reference interpreters, and three C++ dialects are listed separately below. Large cases retain their two originally measured Scala evaluators. See the [measurement notes](README.md#measurement-notes) before comparing engines.

## life — reference random 24x24

Fixture family: `programs`. Result: **125 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 32.005 |  |
| ROG exec | 19.364 |  |
| evalTrie | 396.060 |  |
| evalZ | 528.014 |  |
| ROG execT | 6.502 |  |
| Zippy_bits reference | 739.068802 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 817.456609 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 19.719900 | symbol labels; standalone |
| C++ OpenFst bits | 303.109874 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 134.032068 | four-byte big-endian intern IDs; standalone |

Original benchmark note: exec fallback Call dispatch to optimized callee graph(s): neigh.

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 3517.848 |
| total compile | 3517.848 |
| compile+ROG execT | 3524.350 |
| source pass | 3194.640 |
| lower+inline | 0.361 |
| graph optimize | 317.189 |
| graph transpile | 2.344 |
| const-fold eval | 0.420 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/11-life-reference-random-24x24/symbols.cpp) | 3.768 | 26 | 149 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/11-life-reference-random-24x24/bits.cpp) | 27149.574 | 167 | 259 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/11-life-reference-random-24x24/bytes.cpp) | 1972.629 | 101 | 224 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## life — compile-pass random 24x24

Fixture family: `programs`. Result: **125 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 28.956 |  |
| ROG exec | 0.002 | precomputed literal |
| evalTrie | 242.417 |  |
| evalZ | 308.077 |  |
| ROG execT | 0.001 | precomputed literal |
| Zippy_bits reference | 671.115748 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 809.104921 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 12.308442 | symbol labels; standalone |
| C++ OpenFst bits | 219.954171 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 111.651758 | four-byte big-endian intern IDs; standalone |

Original benchmark note: compiled away; use compile+run.

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | 82.949 |
| graph compile | 20.125 |
| total compile | 103.074 |
| compile+ROG execT | 103.075 |
| source pass | 51.512 |
| lower+inline | 1.937 |
| graph optimize | 34.827 |
| graph transpile | 1.624 |
| const-fold eval | 19.948 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/12-life-compile-pass-random-24x24/symbols.cpp) | 2.493 | 26 | 149 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/12-life-compile-pass-random-24x24/bits.cpp) | 23066.735 | 167 | 259 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/12-life-compile-pass-random-24x24/bytes.cpp) | 1897.020 | 101 | 224 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## life — compile-pass random 24x24 initial literal

Fixture family: `programs`. Result: **125 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.001 |  |
| ROG exec | 0.001 | precomputed literal |
| evalTrie | 0.042 |  |
| evalZ | 0.045 |  |
| ROG execT | 0.001 | precomputed literal |
| Zippy_bits reference | 0.003706 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 0.003666 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.000005 | symbol labels; standalone |
| C++ OpenFst bits | 0.000005 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 0.000005 | four-byte big-endian intern IDs; standalone |

Original benchmark note: compiled away; use compile+run.

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | 94.583 |
| graph compile | 0.328 |
| total compile | 94.912 |
| compile+ROG execT | 94.913 |
| source pass | 92.504 |
| lower+inline | 0.342 |
| graph optimize | 0.456 |
| graph transpile | 0.015 |
| const-fold eval | 85.953 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/13-life-compile-pass-random-24x24-initial-literal/symbols.cpp) | 1.747 | 26 | 149 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/13-life-compile-pass-random-24x24-initial-literal/bits.cpp) | 1.700 | 163 | 249 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/13-life-compile-pass-random-24x24-initial-literal/bytes.cpp) | 1.419 | 101 | 224 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## Focused constant-fold evaluator sweep

This separate compile-time experiment selects Reference, Trie, Zipper, ExecT, or Hybrid as the constant-folding engine. These are compile times, not additional runtime measurements. All durations are milliseconds.

| benchmark | backend | compile ms | const-fold total ms | eval ms | evalTrie ms | evalZ ms | execT ms |
| --- | --- | --- | --- | --- | --- | --- | --- |
| life compile-pass random 24x24 initial literal | Reference | 25.779 | 22.578 | 22.578 | 0.000 | 0.000 | 0.000 |
| life compile-pass random 24x24 initial literal | Trie | 292.580 | 289.469 | 0.000 | 289.469 | 0.000 | 0.000 |
| life compile-pass random 24x24 initial literal | Zipper | 270.581 | 267.674 | 0.000 | 0.000 | 267.674 | 0.000 |
| life compile-pass random 24x24 initial literal | ExecT | 16.298 | 13.756 | 0.000 | 0.000 | 0.000 | 13.756 |
| life compile-pass random 24x24 initial literal | Hybrid | 11.961 | 9.169 | 1.912 | 0.000 | 0.000 | 7.257 |
