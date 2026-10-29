# N-queens

[All examples](README.md) · Runtime measurements are **milliseconds per evaluation**.

The five original Scala evaluator columns, two digit-set reference interpreters, and three C++ dialects are listed separately below. Large cases retain their two originally measured Scala evaluators. See the [measurement notes](README.md#measurement-notes) before comparing engines.

## n-queens — MORKL 8x8 source

Fixture family: `programs`. Result: **92 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 922.094 |  |
| ROG exec | 0.008 | precomputed literal |
| evalTrie | 6107.864 |  |
| evalZ | 96836.172544 | additional source run: measured; 192g heap, 600 s limit; exact check on success |
| ROG execT | 0.001 | precomputed literal |
| Zippy_bits reference | 1882.132751 | BigInt path sets; 1 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 5165.467386 | byte-array path sets; 1 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.000007 | symbol labels; standalone; precomputed literal |
| C++ OpenFst bits | 1257.704268 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 950.600251 | four-byte big-endian intern IDs; standalone; optimized graph |

Original benchmark note: compiled away; use compile+run; direct evalZ is currently omitted for the high-level source search tree; use compile-pass/graph rows.

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 677.661 |
| total compile | 677.661 |
| compile+ROG execT | 677.662 |
| source pass | 671.239 |
| lower+inline | 2.001 |
| graph optimize | 0.684 |
| graph transpile | 0.006 |
| const-fold eval | 661.699 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/21-n-queens-morkl-8x8-source/symbols.cpp) · [graph](../../output/cornerstone-review/programs/21-n-queens-morkl-8x8-source/symbols.graph.txt) | 720.876 | 1 | 2390 | 505.260 | 288 | 378 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/21-n-queens-morkl-8x8-source/bits.cpp) · [graph](../../output/cornerstone-review/programs/21-n-queens-morkl-8x8-source/bits.graph.txt) | 1432.674 | 24842 | 1749671 | 61203.104 | 2252 | 2251 | 1 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/21-n-queens-morkl-8x8-source/bytes.cpp) · [graph](../../output/cornerstone-review/programs/21-n-queens-morkl-8x8-source/bytes.graph.txt) | 96.927 | 1076 | 112662 | 4537.914 | 1928 | 1927 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## n-queens — MORKL 8x8 compile-pass

Fixture family: `programs`. Result: **92 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.002 |  |
| ROG exec | 0.001 | precomputed literal |
| evalTrie | 0.151 |  |
| evalZ | 0.136 |  |
| ROG execT | 0.001 | precomputed literal |
| Zippy_bits reference | 0.004527 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 0.012298 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.000006 | symbol labels; standalone; precomputed literal |
| C++ OpenFst bits | 0.000008 | Fibonacci-coded intern IDs; standalone; precomputed literal |
| C++ OpenFst bytes | 0.000007 | four-byte big-endian intern IDs; standalone; precomputed literal |

Original benchmark note: compiled away; use compile+run.

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | 609.430 |
| graph compile | 1.751 |
| total compile | 611.180 |
| compile+ROG execT | 611.181 |
| source pass | 607.033 |
| lower+inline | 1.737 |
| graph optimize | 0.421 |
| graph transpile | 0.122 |
| const-fold eval | 593.705 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/22-n-queens-morkl-8x8-compile-pass/symbols.cpp) · [graph](../../output/cornerstone-review/programs/22-n-queens-morkl-8x8-compile-pass/symbols.graph.txt) | 0.792 | 1 | 2390 | 2.245 | 288 | 378 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/22-n-queens-morkl-8x8-compile-pass/bits.cpp) · [graph](../../output/cornerstone-review/programs/22-n-queens-morkl-8x8-compile-pass/bits.graph.txt) | 1.763 | 1 | 7212 | 671.364 | 1074 | 1164 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/22-n-queens-morkl-8x8-compile-pass/bytes.cpp) · [graph](../../output/cornerstone-review/programs/22-n-queens-morkl-8x8-compile-pass/bytes.graph.txt) | 1.835 | 1 | 6844 | 573.300 | 1149 | 1239 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## Focused constant-fold evaluator sweep

This separate compile-time experiment selects Reference, Trie, Zipper, ExecT, or Hybrid as the constant-folding engine. These are compile times, not additional runtime measurements. All durations are milliseconds.

| benchmark | backend | compile ms | const-fold total ms | eval ms | evalTrie ms | evalZ ms | execT ms |
| --- | --- | --- | --- | --- | --- | --- | --- |
| n-queens MORKL 8x8 source graph compile | Reference | 372.757 | 366.658 | 366.658 | 0.000 | 0.000 | 0.000 |
| n-queens MORKL 8x8 compile-pass | Reference | 371.225 | 366.239 | 366.239 | 0.000 | 0.000 | 0.000 |
| n-queens MORKL 8x8 source graph compile | Trie | 740.792 | 733.597 | 0.000 | 733.597 | 0.000 | 0.000 |
| n-queens MORKL 8x8 compile-pass | Trie | 648.604 | 643.747 | 0.000 | 643.747 | 0.000 | 0.000 |
| n-queens MORKL 8x8 source graph compile | Zipper | 1246.428 | 1240.338 | 0.000 | 0.000 | 1240.338 | 0.000 |
| n-queens MORKL 8x8 compile-pass | Zipper | 1282.748 | 1277.896 | 0.000 | 0.000 | 1277.896 | 0.000 |
| n-queens MORKL 8x8 source graph compile | ExecT | 298.610 | 291.574 | 0.000 | 0.000 | 0.000 | 291.574 |
| n-queens MORKL 8x8 compile-pass | ExecT | 302.530 | 297.789 | 0.000 | 0.000 | 0.000 | 297.789 |
| n-queens MORKL 8x8 source graph compile | Hybrid | 452.682 | 443.949 | 443.949 | 0.000 | 0.000 | 0.000 |
| n-queens MORKL 8x8 compile-pass | Hybrid | 386.047 | 381.112 | 381.112 | 0.000 | 0.000 | 0.000 |

## Independent specialized bit-recursive reference

These additional scaling rows use the dedicated n-queens algorithm, not the Zippy bit-language interpreter. They are kept separate from the common-program comparison.

| benchmark | variant | result | ms | note |
| --- | --- | --- | --- | --- |
| n-queens bit reference | n=8 | 92 | 0.119 | independent reference, not MORKL eval |
| n-queens bit reference | n=9 | 352 | 0.054 | independent reference, not MORKL eval |
| n-queens bit reference | n=10 | 724 | 0.251 | independent reference, not MORKL eval |
| n-queens bit reference | n=11 | 2680 | 1.075 | independent reference, not MORKL eval |
| n-queens bit reference | n=12 | 14200 | 5.626 | independent reference, not MORKL eval |
