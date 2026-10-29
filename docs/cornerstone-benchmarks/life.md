# Game of Life

[All examples](README.md) · Runtime measurements are **milliseconds per evaluation**.

The five original Scala evaluator columns, two digit-set reference interpreters, and three C++ dialects are listed separately below. Large cases retain their two originally measured Scala evaluators. See the [measurement notes](README.md#measurement-notes) before comparing engines.

## life — reference random 24x24

Fixture family: `programs`. Result: **125 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 27.786 |  |
| ROG exec | 14.247 |  |
| evalTrie | 464.934 |  |
| evalZ | 493.081 |  |
| ROG execT | 4.208 |  |
| Zippy_bits reference | 832.823195 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 907.779510 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 6.593130 | symbol labels; standalone; optimized graph |
| C++ OpenFst bits | 211.307802 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 76.152429 | four-byte big-endian intern IDs; standalone; optimized graph |

Original benchmark note: exec fallback Call dispatch to optimized callee graph(s): neigh.

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 3158.375 |
| total compile | 3158.375 |
| compile+ROG execT | 3162.583 |
| source pass | 3113.990 |
| lower+inline | 1.035 |
| graph optimize | 34.598 |
| graph transpile | 3.877 |
| const-fold eval | 0.496 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/11-life-reference-random-24x24/symbols.cpp) · [graph](../../output/cornerstone-review/programs/11-life-reference-random-24x24/symbols.graph.txt) | 3462.488 | 2197 | 180909 | 26919.277 | 150 | 149 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/11-life-reference-random-24x24/bits.cpp) · [graph](../../output/cornerstone-review/programs/11-life-reference-random-24x24/bits.graph.txt) | 905.666 | 12806 | 917128 | 27805.220 | 648 | 647 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/11-life-reference-random-24x24/bytes.cpp) · [graph](../../output/cornerstone-review/programs/11-life-reference-random-24x24/bytes.graph.txt) | 579.213 | 565 | 58869 | 1813.757 | 225 | 224 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## life — compile-pass random 24x24

Fixture family: `programs`. Result: **125 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 23.910 |  |
| ROG exec | 0.002 | precomputed literal |
| evalTrie | 401.333 |  |
| evalZ | 461.052 |  |
| ROG execT | 0.001 | precomputed literal |
| Zippy_bits reference | 701.709135 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 804.940862 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.000006 | symbol labels; standalone; precomputed literal |
| C++ OpenFst bits | 169.401873 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 77.802984 | four-byte big-endian intern IDs; standalone; optimized graph |

Original benchmark note: compiled away; use compile+run.

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | 138.133 |
| graph compile | 18.754 |
| total compile | 156.888 |
| compile+ROG execT | 156.888 |
| source pass | 75.366 |
| lower+inline | 6.882 |
| graph optimize | 47.469 |
| graph transpile | 2.542 |
| const-fold eval | 18.076 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/12-life-compile-pass-random-24x24/symbols.cpp) · [graph](../../output/cornerstone-review/programs/12-life-compile-pass-random-24x24/symbols.graph.txt) | 21.111 | 1 | 2103 | 372.229 | 26 | 149 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/12-life-compile-pass-random-24x24/bits.cpp) · [graph](../../output/cornerstone-review/programs/12-life-compile-pass-random-24x24/bits.graph.txt) | 758.858 | 11051 | 779200 | 17397.916 | 648 | 647 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/12-life-compile-pass-random-24x24/bytes.cpp) · [graph](../../output/cornerstone-review/programs/12-life-compile-pass-random-24x24/bytes.graph.txt) | 311.654 | 567 | 56119 | 1813.704 | 225 | 224 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## life — compile-pass random 24x24 initial literal

Fixture family: `programs`. Result: **125 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.000 |  |
| ROG exec | 0.001 | precomputed literal |
| evalTrie | 0.052 |  |
| evalZ | 0.053 |  |
| ROG execT | 0.001 | precomputed literal |
| Zippy_bits reference | 0.004297 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 0.004877 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.000006 | symbol labels; standalone; precomputed literal |
| C++ OpenFst bits | 0.000006 | Fibonacci-coded intern IDs; standalone; precomputed literal |
| C++ OpenFst bytes | 0.000006 | four-byte big-endian intern IDs; standalone; precomputed literal |

Original benchmark note: compiled away; use compile+run.

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | 89.963 |
| graph compile | 0.482 |
| total compile | 90.445 |
| compile+ROG execT | 90.446 |
| source pass | 85.805 |
| lower+inline | 1.817 |
| graph optimize | 0.642 |
| graph transpile | 0.023 |
| const-fold eval | 77.913 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/13-life-compile-pass-random-24x24-initial-literal/symbols.cpp) · [graph](../../output/cornerstone-review/programs/13-life-compile-pass-random-24x24-initial-literal/symbols.graph.txt) | 2.686 | 1 | 2103 | 4.980 | 26 | 149 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/13-life-compile-pass-random-24x24-initial-literal/bits.cpp) · [graph](../../output/cornerstone-review/programs/13-life-compile-pass-random-24x24-initial-literal/bits.graph.txt) | 3.527 | 1 | 4744 | 505.229 | 163 | 249 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/13-life-compile-pass-random-24x24-initial-literal/bytes.cpp) · [graph](../../output/cornerstone-review/programs/13-life-compile-pass-random-24x24-initial-literal/bytes.graph.txt) | 3.611 | 1 | 4218 | 421.515 | 101 | 224 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## Focused constant-fold evaluator sweep

This separate compile-time experiment selects Reference, Trie, Zipper, ExecT, or Hybrid as the constant-folding engine. These are compile times, not additional runtime measurements. All durations are milliseconds.

| benchmark | backend | compile ms | const-fold total ms | eval ms | evalTrie ms | evalZ ms | execT ms |
| --- | --- | --- | --- | --- | --- | --- | --- |
| life compile-pass random 24x24 initial literal | Reference | 21.697 | 18.621 | 18.621 | 0.000 | 0.000 | 0.000 |
| life compile-pass random 24x24 initial literal | Trie | 284.844 | 281.865 | 0.000 | 281.865 | 0.000 | 0.000 |
| life compile-pass random 24x24 initial literal | Zipper | 287.962 | 285.029 | 0.000 | 0.000 | 285.029 | 0.000 |
| life compile-pass random 24x24 initial literal | ExecT | 19.415 | 16.574 | 0.000 | 0.000 | 0.000 | 16.574 |
| life compile-pass random 24x24 initial literal | Hybrid | 16.696 | 12.899 | 2.515 | 0.000 | 0.000 | 10.384 |
