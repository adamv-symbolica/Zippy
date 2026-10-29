# N-queens

[All examples](README.md) · Runtime measurements are **milliseconds per evaluation**.

The five original Scala evaluator columns, two digit-set reference interpreters, and three C++ dialects are listed separately below. Large cases retain their two originally measured Scala evaluators. See the [measurement notes](README.md#measurement-notes) before comparing engines.

## n-queens — MORKL 8x8 source

Fixture family: `programs`. Result: **92 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 573.259 |  |
| ROG exec | 0.003 | precomputed literal |
| evalTrie | 1881.980 |  |
| evalZ | n/a | omitted by original benchmark (see note below) |
| ROG execT | 0.001 | precomputed literal |
| Zippy_bits reference | 1740.286046 | BigInt path sets; 1 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 6165.488068 | byte-array path sets; 1 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 364.509674 | symbol labels; standalone |
| C++ OpenFst bits | 1706.336924 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 1196.834150 | four-byte big-endian intern IDs; standalone |

Original benchmark note: compiled away; use compile+run; direct evalZ is currently omitted for the high-level source search tree; use compile-pass/graph rows.

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | n/a |
| graph compile | 405.657 |
| total compile | 405.657 |
| compile+ROG execT | 405.658 |
| source pass | 404.048 |
| lower+inline | 0.181 |
| graph optimize | 0.146 |
| graph transpile | 0.004 |
| const-fold eval | 399.310 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/21-n-queens-morkl-8x8-source/symbols.cpp) | 2.234 | 288 | 378 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/21-n-queens-morkl-8x8-source/bits.cpp) | 8.206 | 1133 | 1223 | 1 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/21-n-queens-morkl-8x8-source/bytes.cpp) | 2.532 | 1149 | 1239 | 1 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## n-queens — MORKL 8x8 compile-pass

Fixture family: `programs`. Result: **92 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.001 |  |
| ROG exec | 0.001 | precomputed literal |
| evalTrie | 0.121 |  |
| evalZ | 0.119 |  |
| ROG execT | 0.001 | precomputed literal |
| Zippy_bits reference | 0.003295 | BigInt path sets; 5 sample(s), exact check; separate h4d run |
| Zippy_bytes reference | 0.003746 | byte-array path sets; 5 sample(s), exact check; separate h4d run |
| C++ OpenFst symbols | 0.000005 | symbol labels; standalone |
| C++ OpenFst bits | 0.000005 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 0.000005 | four-byte big-endian intern IDs; standalone |

Original benchmark note: compiled away; use compile+run.

Scala compilation costs from the same report (milliseconds):

| Stage | ms |
| --- | --- |
| prep compile | 527.446 |
| graph compile | 0.370 |
| total compile | 527.815 |
| compile+ROG execT | 527.816 |
| source pass | 526.057 |
| lower+inline | 0.654 |
| graph optimize | 0.089 |
| graph transpile | 0.024 |
| const-fold eval | 514.805 |
| const-fold evalTrie | 0.000 |
| const-fold evalZ | 0.000 |
| const-fold execT | 0.000 |

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/programs/22-n-queens-morkl-8x8-compile-pass/symbols.cpp) | 2.028 | 288 | 378 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/programs/22-n-queens-morkl-8x8-compile-pass/bits.cpp) | 2.042 | 1074 | 1164 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/programs/22-n-queens-morkl-8x8-compile-pass/bytes.cpp) | 1.743 | 1149 | 1239 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## Focused constant-fold evaluator sweep

This separate compile-time experiment selects Reference, Trie, Zipper, ExecT, or Hybrid as the constant-folding engine. These are compile times, not additional runtime measurements. All durations are milliseconds.

| benchmark | backend | compile ms | const-fold total ms | eval ms | evalTrie ms | evalZ ms | execT ms |
| --- | --- | --- | --- | --- | --- | --- | --- |
| n-queens MORKL 8x8 source graph compile | Reference | 388.331 | 382.181 | 382.181 | 0.000 | 0.000 | 0.000 |
| n-queens MORKL 8x8 compile-pass | Reference | 394.489 | 388.953 | 388.953 | 0.000 | 0.000 | 0.000 |
| n-queens MORKL 8x8 source graph compile | Trie | 723.541 | 717.194 | 0.000 | 717.194 | 0.000 | 0.000 |
| n-queens MORKL 8x8 compile-pass | Trie | 606.273 | 600.825 | 0.000 | 600.825 | 0.000 | 0.000 |
| n-queens MORKL 8x8 source graph compile | Zipper | 1111.440 | 1105.064 | 0.000 | 0.000 | 1105.064 | 0.000 |
| n-queens MORKL 8x8 compile-pass | Zipper | 917.887 | 912.728 | 0.000 | 0.000 | 912.728 | 0.000 |
| n-queens MORKL 8x8 source graph compile | ExecT | 199.528 | 193.680 | 0.000 | 0.000 | 0.000 | 193.680 |
| n-queens MORKL 8x8 compile-pass | ExecT | 241.872 | 236.515 | 0.000 | 0.000 | 0.000 | 236.515 |
| n-queens MORKL 8x8 source graph compile | Hybrid | 399.497 | 392.838 | 392.838 | 0.000 | 0.000 | 0.000 |
| n-queens MORKL 8x8 compile-pass | Hybrid | 380.343 | 375.256 | 375.256 | 0.000 | 0.000 | 0.000 |

## Independent specialized bit-recursive reference

These additional scaling rows use the dedicated n-queens algorithm, not the Zippy bit-language interpreter. They are kept separate from the common-program comparison.

| benchmark | variant | result | ms | note |
| --- | --- | --- | --- | --- |
| n-queens bit reference | n=8 | 92 | 0.011 | independent reference, not MORKL eval |
| n-queens bit reference | n=9 | 352 | 0.041 | independent reference, not MORKL eval |
| n-queens bit reference | n=10 | 724 | 0.257 | independent reference, not MORKL eval |
| n-queens bit reference | n=11 | 2680 | 0.976 | independent reference, not MORKL eval |
| n-queens bit reference | n=12 | 14200 | 5.220 | independent reference, not MORKL eval |
