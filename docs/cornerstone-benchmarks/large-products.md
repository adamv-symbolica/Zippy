# Supplement: large selective products

[All examples](README.md) · Runtime measurements are **milliseconds per evaluation**.

The five original Scala evaluator columns, two digit-set reference interpreters, and three C++ dialects are listed separately below. Large cases retain their two originally measured Scala evaluators. See the [measurement notes](README.md#measurement-notes) before comparing engines.

## product intersected by one exact path — 2000 x 2000 product, 1 result path

Fixture family: `large`. Result: **1 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 0.513 |  |
| evalZ | 0.153 |  |
| C++ OpenFst symbols | 0.028729 | symbol labels; standalone |
| C++ OpenFst bits | 0.008054 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 0.009388 | four-byte big-endian intern IDs; standalone |

Original benchmark note: large intermediate product should not be materialized by zipper traversal.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/26-product-intersected-by-one-exact-path-2000-x-2000-product-1-result-path/symbols.cpp) | 41.311 | 3 | 2 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/large/26-product-intersected-by-one-exact-path-2000-x-2000-product-1-result-path/bits.cpp) | 2.600 | 23 | 22 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/26-product-intersected-by-one-exact-path-2000-x-2000-product-1-result-path/bytes.cpp) | 2.437 | 9 | 8 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## product intersected by one exact path — 10000 x 10000 product, 1 result path

Fixture family: `large`. Result: **1 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 2.114 |  |
| evalZ | 0.256 |  |
| C++ OpenFst symbols | 0.137992 | symbol labels; standalone |
| C++ OpenFst bits | 0.018792 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 0.008572 | four-byte big-endian intern IDs; standalone |

Original benchmark note: large intermediate product should not be materialized by zipper traversal.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/27-product-intersected-by-one-exact-path-10000-x-10000-product-1-result-path/symbols.cpp) | 43.652 | 3 | 2 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/large/27-product-intersected-by-one-exact-path-10000-x-10000-product-1-result-path/bits.cpp) | 2.322 | 41 | 40 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/27-product-intersected-by-one-exact-path-10000-x-10000-product-1-result-path/bytes.cpp) | 2.621 | 9 | 8 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## product intersected by one exact path — 30000 x 30000 product, 1 result path

Fixture family: `large`. Result: **1 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 6.758 |  |
| evalZ | 0.098 |  |
| C++ OpenFst symbols | 0.388167 | symbol labels; standalone |
| C++ OpenFst bits | 0.020756 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 0.010083 | four-byte big-endian intern IDs; standalone |

Original benchmark note: large intermediate product should not be materialized by zipper traversal.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/28-product-intersected-by-one-exact-path-30000-x-30000-product-1-result-path/symbols.cpp) | 38.123 | 3 | 2 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/large/28-product-intersected-by-one-exact-path-30000-x-30000-product-1-result-path/bits.cpp) | 2.085 | 43 | 42 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/28-product-intersected-by-one-exact-path-30000-x-30000-product-1-result-path/bytes.cpp) | 3.052 | 9 | 8 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## product restricted by one first-level prefix — 2000 x 2000 product, 2000 result paths

Fixture family: `large`. Result: **2,000 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 0.430 |  |
| evalZ | 0.023 |  |
| C++ OpenFst symbols | 0.064618 | symbol labels; standalone |
| C++ OpenFst bits | 0.014840 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 0.020694 | four-byte big-endian intern IDs; standalone |

Original benchmark note: large intermediate product should not be materialized by zipper traversal.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/29-product-restricted-by-one-first-level-prefix-2000-x-2000-product-2000-result-paths/symbols.cpp) | 2.265 | 3 | 2001 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/large/29-product-restricted-by-one-first-level-prefix-2000-x-2000-product-2000-result-paths/bits.cpp) | 2.055 | 60 | 90 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/29-product-restricted-by-one-first-level-prefix-2000-x-2000-product-2000-result-paths/bytes.cpp) | 1.972 | 11 | 479 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## product restricted by one first-level prefix — 10000 x 10000 product, 10000 result paths

Fixture family: `large`. Result: **10,000 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 2.005 |  |
| evalZ | 0.035 |  |
| C++ OpenFst symbols | 0.322316 | symbol labels; standalone |
| C++ OpenFst bits | 0.033465 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 0.021098 | four-byte big-endian intern IDs; standalone |

Original benchmark note: large intermediate product should not be materialized by zipper traversal.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/30-product-restricted-by-one-first-level-prefix-10000-x-10000-product-10000-result-paths/symbols.cpp) | 1.961 | 3 | 10001 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/large/30-product-restricted-by-one-first-level-prefix-10000-x-10000-product-10000-result-paths/bits.cpp) | 1.907 | 86 | 123 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/30-product-restricted-by-one-first-level-prefix-10000-x-10000-product-10000-result-paths/bytes.cpp) | 1.709 | 11 | 574 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.

## product restricted by one first-level prefix — 30000 x 30000 product, 30000 result paths

Fixture family: `large`. Result: **30,000 paths**.

| Evaluator / transpilation | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 10.377 |  |
| evalZ | 0.015 |  |
| C++ OpenFst symbols | 0.929918 | symbol labels; standalone |
| C++ OpenFst bits | 0.039034 | Fibonacci-coded intern IDs; standalone |
| C++ OpenFst bytes | 0.025654 | four-byte big-endian intern IDs; standalone |

Original benchmark note: large intermediate product should not be materialized by zipper traversal.

Native compilation/session costs and resulting acceptor sizes:

| Dialect / source | Session setup ms¹ | States | Arcs | Samples | Correctness |
| --- | --- | --- | --- | --- | --- |
| [symbols.cpp](../../output/cornerstone-review/large/31-product-restricted-by-one-first-level-prefix-30000-x-30000-product-30000-result-paths/symbols.cpp) | 2.037 | 3 | 30001 | 5 | exact |
| [bits.cpp](../../output/cornerstone-review/large/31-product-restricted-by-one-first-level-prefix-30000-x-30000-product-30000-result-paths/bits.cpp) | 2.107 | 97 | 142 | 5 | exact |
| [bytes.cpp](../../output/cornerstone-review/large/31-product-restricted-by-one-first-level-prefix-30000-x-30000-product-30000-result-paths/bytes.cpp) | 2.924 | 11 | 684 | 5 | exact |

¹ May include a compilation cache hit; this is not a cold compilation comparison.
