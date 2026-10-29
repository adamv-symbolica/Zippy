# Supplement: large selective products

[All examples](README.md) · Runtime measurements are **milliseconds per evaluation**.

The five original Scala evaluator columns, two digit-set reference interpreters, and three C++ dialects are listed separately below. Large cases retain their two originally measured Scala evaluators. See the [measurement notes](README.md#measurement-notes) before comparing engines.

## product intersected by one exact path — 2000 x 2000 product, 1 result path

Fixture family: `large`. Result: **1 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 0.675 |  |
| evalZ | 0.085 |  |
| C++ OpenFst symbols | 0.288844 | symbol labels; standalone; optimized graph |
| C++ OpenFst bits | 0.009332 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 0.025204 | four-byte big-endian intern IDs; standalone; optimized graph |

Original benchmark note: large intermediate product should not be materialized by zipper traversal.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/26-product-intersected-by-one-exact-path-2000-x-2000-product-1-result-path/symbols.cpp) · [graph](../../output/cornerstone-review/large/26-product-intersected-by-one-exact-path-2000-x-2000-product-1-result-path/symbols.graph.txt) | 60.819 | 5 | 885 | 9768.322 | 3 | 2 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/large/26-product-intersected-by-one-exact-path-2000-x-2000-product-1-result-path/bits.cpp) · [graph](../../output/cornerstone-review/large/26-product-intersected-by-one-exact-path-2000-x-2000-product-1-result-path/bits.graph.txt) | 2.532 | 5 | 886 | 406.700 | 23 | 22 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/26-product-intersected-by-one-exact-path-2000-x-2000-product-1-result-path/bytes.cpp) · [graph](../../output/cornerstone-review/large/26-product-intersected-by-one-exact-path-2000-x-2000-product-1-result-path/bytes.graph.txt) | 2.659 | 5 | 886 | 2.250 | 9 | 8 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## product intersected by one exact path — 10000 x 10000 product, 1 result path

Fixture family: `large`. Result: **1 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 2.797 |  |
| evalZ | 0.091 |  |
| C++ OpenFst symbols | 1.540355 | symbol labels; standalone; optimized graph |
| C++ OpenFst bits | 0.019634 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 0.048867 | four-byte big-endian intern IDs; standalone; optimized graph |

Original benchmark note: large intermediate product should not be materialized by zipper traversal.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/27-product-intersected-by-one-exact-path-10000-x-10000-product-1-result-path/symbols.cpp) · [graph](../../output/cornerstone-review/large/27-product-intersected-by-one-exact-path-10000-x-10000-product-1-result-path/symbols.graph.txt) | 2.643 | 5 | 885 | 1.749 | 3 | 2 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/large/27-product-intersected-by-one-exact-path-10000-x-10000-product-1-result-path/bits.cpp) · [graph](../../output/cornerstone-review/large/27-product-intersected-by-one-exact-path-10000-x-10000-product-1-result-path/bits.graph.txt) | 1.283 | 5 | 886 | 1.806 | 41 | 40 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/27-product-intersected-by-one-exact-path-10000-x-10000-product-1-result-path/bytes.cpp) · [graph](../../output/cornerstone-review/large/27-product-intersected-by-one-exact-path-10000-x-10000-product-1-result-path/bytes.graph.txt) | 1.228 | 5 | 886 | 1.942 | 9 | 8 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## product intersected by one exact path — 30000 x 30000 product, 1 result path

Fixture family: `large`. Result: **1 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 9.794 |  |
| evalZ | 0.127 |  |
| C++ OpenFst symbols | 4.836129 | symbol labels; standalone; optimized graph |
| C++ OpenFst bits | 0.020799 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 0.064227 | four-byte big-endian intern IDs; standalone; optimized graph |

Original benchmark note: large intermediate product should not be materialized by zipper traversal.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/28-product-intersected-by-one-exact-path-30000-x-30000-product-1-result-path/symbols.cpp) · [graph](../../output/cornerstone-review/large/28-product-intersected-by-one-exact-path-30000-x-30000-product-1-result-path/symbols.graph.txt) | 1.598 | 5 | 885 | 1.655 | 3 | 2 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/large/28-product-intersected-by-one-exact-path-30000-x-30000-product-1-result-path/bits.cpp) · [graph](../../output/cornerstone-review/large/28-product-intersected-by-one-exact-path-30000-x-30000-product-1-result-path/bits.graph.txt) | 2.020 | 5 | 886 | 2.045 | 43 | 42 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/28-product-intersected-by-one-exact-path-30000-x-30000-product-1-result-path/bytes.cpp) · [graph](../../output/cornerstone-review/large/28-product-intersected-by-one-exact-path-30000-x-30000-product-1-result-path/bytes.graph.txt) | 1.094 | 5 | 886 | 1.898 | 9 | 8 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## product restricted by one first-level prefix — 2000 x 2000 product, 2000 result paths

Fixture family: `large`. Result: **2,000 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 0.606 |  |
| evalZ | 0.028 |  |
| C++ OpenFst symbols | 0.325559 | symbol labels; standalone; optimized graph |
| C++ OpenFst bits | 0.027825 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 0.071980 | four-byte big-endian intern IDs; standalone; optimized graph |

Original benchmark note: large intermediate product should not be materialized by zipper traversal.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/29-product-restricted-by-one-first-level-prefix-2000-x-2000-product-2000-result-paths/symbols.cpp) · [graph](../../output/cornerstone-review/large/29-product-restricted-by-one-first-level-prefix-2000-x-2000-product-2000-result-paths/symbols.graph.txt) | 3.226 | 5 | 887 | 2.486 | 3 | 2001 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/large/29-product-restricted-by-one-first-level-prefix-2000-x-2000-product-2000-result-paths/bits.cpp) · [graph](../../output/cornerstone-review/large/29-product-restricted-by-one-first-level-prefix-2000-x-2000-product-2000-result-paths/bits.graph.txt) | 1.100 | 5 | 888 | 1.844 | 60 | 90 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/29-product-restricted-by-one-first-level-prefix-2000-x-2000-product-2000-result-paths/bytes.cpp) · [graph](../../output/cornerstone-review/large/29-product-restricted-by-one-first-level-prefix-2000-x-2000-product-2000-result-paths/bytes.graph.txt) | 0.975 | 5 | 888 | 1.502 | 11 | 479 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## product restricted by one first-level prefix — 10000 x 10000 product, 10000 result paths

Fixture family: `large`. Result: **10,000 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 2.430 |  |
| evalZ | 0.081 |  |
| C++ OpenFst symbols | 1.714573 | symbol labels; standalone; optimized graph |
| C++ OpenFst bits | 0.043573 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 0.080910 | four-byte big-endian intern IDs; standalone; optimized graph |

Original benchmark note: large intermediate product should not be materialized by zipper traversal.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/30-product-restricted-by-one-first-level-prefix-10000-x-10000-product-10000-result-paths/symbols.cpp) · [graph](../../output/cornerstone-review/large/30-product-restricted-by-one-first-level-prefix-10000-x-10000-product-10000-result-paths/symbols.graph.txt) | 2.393 | 5 | 887 | 415.507 | 3 | 10001 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/large/30-product-restricted-by-one-first-level-prefix-10000-x-10000-product-10000-result-paths/bits.cpp) · [graph](../../output/cornerstone-review/large/30-product-restricted-by-one-first-level-prefix-10000-x-10000-product-10000-result-paths/bits.graph.txt) | 2.148 | 5 | 888 | 357.920 | 86 | 123 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/30-product-restricted-by-one-first-level-prefix-10000-x-10000-product-10000-result-paths/bytes.cpp) · [graph](../../output/cornerstone-review/large/30-product-restricted-by-one-first-level-prefix-10000-x-10000-product-10000-result-paths/bytes.graph.txt) | 2.125 | 5 | 888 | 1.759 | 11 | 574 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## product restricted by one first-level prefix — 30000 x 30000 product, 30000 result paths

Fixture family: `large`. Result: **30,000 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 13.794 |  |
| evalZ | 0.097 |  |
| C++ OpenFst symbols | 5.371808 | symbol labels; standalone; optimized graph |
| C++ OpenFst bits | 0.049428 | Fibonacci-coded intern IDs; standalone; optimized graph |
| C++ OpenFst bytes | 0.105019 | four-byte big-endian intern IDs; standalone; optimized graph |

Original benchmark note: large intermediate product should not be materialized by zipper traversal.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Source + graph compile ms | Live nodes | Source bytes | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/31-product-restricted-by-one-first-level-prefix-30000-x-30000-product-30000-result-paths/symbols.cpp) · [graph](../../output/cornerstone-review/large/31-product-restricted-by-one-first-level-prefix-30000-x-30000-product-30000-result-paths/symbols.graph.txt) | 1.976 | 5 | 887 | 1.878 | 3 | 30001 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/large/31-product-restricted-by-one-first-level-prefix-30000-x-30000-product-30000-result-paths/bits.cpp) · [graph](../../output/cornerstone-review/large/31-product-restricted-by-one-first-level-prefix-30000-x-30000-product-30000-result-paths/bits.graph.txt) | 1.137 | 5 | 888 | 1.756 | 97 | 142 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/31-product-restricted-by-one-first-level-prefix-30000-x-30000-product-30000-result-paths/bytes.cpp) · [graph](../../output/cornerstone-review/large/31-product-restricted-by-one-first-level-prefix-30000-x-30000-product-30000-result-paths/bytes.graph.txt) | 1.091 | 5 | 888 | 1.996 | 11 | 684 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.
