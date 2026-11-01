# Aunt queries

[All examples](README.md) · Milliseconds per evaluation.

## aunt — reference

Result: **3 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.077 | reference path sets |
| ROG exec | 0.162 |  |
| evalTrie | 0.471 | symbol trie |
| evalZ | 0.830 | zipper evaluator |
| ROG execT | 0.451 |  |
| Zippy_bits reference | 3.526189 | independent set representation |
| Zippy_bytes reference | 2.866780 | independent set representation |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/programs/01-aunt-reference/symbols.cpp) | 0.013011 | optimized graph |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/programs/01-aunt-reference/bits.cpp) | 0.089313 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/programs/01-aunt-reference/bytes.cpp) | 0.039006 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/programs/01-aunt-reference/bytes-interned.rs) | 0.007933 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/programs/01-aunt-reference/bytes-strings.rs) | 0.029234 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/programs/01-aunt-reference/bytes-length.rs) | 0.019847 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/programs/01-aunt-reference/bits-bytes.rs) | 0.025886 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 15.724 | 11504.370 |  |
| C++ OpenFst bits | 211.595 | 2260.965 |  |
| C++ OpenFst bytes | 8.853 | 808.946 |  |
| Rust PathMap bytes-interned | 42.796 | 10814.232 | 3 |
| Rust PathMap bytes-strings | 43.610 | 2572.490 | 3 |
| Rust PathMap bytes-length | 1914.646 | 2353.152 | 3 |
| Rust PathMap bits-bytes | 102.008 | 8329.613 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 4.990 | 4.990 | 5.441 |

## aunt — process-sc static family

Result: **3 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.038 | reference path sets |
| ROG exec | 0.183 |  |
| evalTrie | 0.442 | symbol trie |
| evalZ | 0.640 | zipper evaluator |
| ROG execT | 0.269 |  |
| Zippy_bits reference | 2.945848 | independent set representation |
| Zippy_bytes reference | 2.126910 | independent set representation |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/programs/02-aunt-process-sc-static-family/symbols.cpp) | 0.013045 | optimized graph |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/programs/02-aunt-process-sc-static-family/bits.cpp) | 0.083811 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/programs/02-aunt-process-sc-static-family/bytes.cpp) | 0.035757 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/programs/02-aunt-process-sc-static-family/bytes-interned.rs) | 0.005676 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/programs/02-aunt-process-sc-static-family/bytes-strings.rs) | 0.026824 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/programs/02-aunt-process-sc-static-family/bytes-length.rs) | 0.016280 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/programs/02-aunt-process-sc-static-family/bits-bytes.rs) | 0.023488 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 19.293 | 11498.694 |  |
| C++ OpenFst bits | 210.248 | 1901.078 |  |
| C++ OpenFst bytes | 11.825 | 801.391 |  |
| Rust PathMap bytes-interned | 52.105 | 10456.430 | 3 |
| Rust PathMap bytes-strings | 42.896 | 2537.874 | 3 |
| Rust PathMap bytes-length | 2042.626 | 2423.089 | 3 |
| Rust PathMap bits-bytes | 98.547 | 6744.112 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | 51.609 | 2.694 | 54.303 | 54.572 |

## aunt synthetic — reference 60 people

Result: **36 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 3.818 | reference path sets |
| ROG exec | 1.403 |  |
| evalTrie | 1.423 | symbol trie |
| evalZ | 3.896 | zipper evaluator |
| ROG execT | 1.355 |  |
| Zippy_bits reference | 15.559630 | independent set representation |
| Zippy_bytes reference | 42.803395 | independent set representation |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/programs/03-aunt-synthetic-reference-60-people/symbols.cpp) | 0.333861 | optimized graph |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/programs/03-aunt-synthetic-reference-60-people/bits.cpp) | 4.901262 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/programs/03-aunt-synthetic-reference-60-people/bytes.cpp) | 0.792233 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/programs/03-aunt-synthetic-reference-60-people/bytes-interned.rs) | 0.114188 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/programs/03-aunt-synthetic-reference-60-people/bytes-strings.rs) | 0.420078 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/programs/03-aunt-synthetic-reference-60-people/bytes-length.rs) | 0.180125 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/programs/03-aunt-synthetic-reference-60-people/bits-bytes.rs) | 0.803494 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 16.838 | 11509.064 |  |
| C++ OpenFst bits | 266.532 | 3085.058 |  |
| C++ OpenFst bytes | 13.624 | 836.263 |  |
| Rust PathMap bytes-interned | 51.931 | 1960.912 | 3 |
| Rust PathMap bytes-strings | 53.132 | 3.232 | 3 |
| Rust PathMap bytes-length | 2072.751 | 1.955 | 3 |
| Rust PathMap bits-bytes | 143.303 | 11921.686 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 2.617 | 2.617 | 3.972 |

## aunt royal92 — reference royal92_simple.metta fallback

Result: **3 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.058 | reference path sets |
| ROG exec | 0.069 |  |
| evalTrie | 0.090 | symbol trie |
| evalZ | 0.153 | zipper evaluator |
| ROG execT | 0.099 |  |
| Zippy_bits reference | 3.190175 | independent set representation |
| Zippy_bytes reference | 3.045718 | independent set representation |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/programs/04-aunt-royal92-reference-royal92-simple-metta-fallback/symbols.cpp) | 0.013313 | optimized graph |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/programs/04-aunt-royal92-reference-royal92-simple-metta-fallback/bits.cpp) | 0.090080 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/programs/04-aunt-royal92-reference-royal92-simple-metta-fallback/bytes.cpp) | 0.039687 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/programs/04-aunt-royal92-reference-royal92-simple-metta-fallback/bytes-interned.rs) | 0.007964 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/programs/04-aunt-royal92-reference-royal92-simple-metta-fallback/bytes-strings.rs) | 0.029278 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/programs/04-aunt-royal92-reference-royal92-simple-metta-fallback/bytes-length.rs) | 0.019859 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/programs/04-aunt-royal92-reference-royal92-simple-metta-fallback/bits-bytes.rs) | 0.025867 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 19.662 | 11353.954 |  |
| C++ OpenFst bits | 215.371 | 2262.004 |  |
| C++ OpenFst bytes | 14.670 | 788.031 |  |
| Rust PathMap bytes-interned | 46.908 | 61.612 | 3 |
| Rust PathMap bytes-strings | 54.089 | 1.968 | 3 |
| Rust PathMap bytes-length | 1978.006 | 3.351 | 3 |
| Rust PathMap bits-bytes | 112.105 | 6.733 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 2.409 | 2.409 | 2.508 |

## aunt synthetic — process-sc static family 24 people

Result: **12 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.363 | reference path sets |
| ROG exec | 0.434 |  |
| evalTrie | 3.942 | symbol trie |
| evalZ | 2.333 | zipper evaluator |
| ROG execT | 0.264 |  |
| Zippy_bits reference | 7.014632 | independent set representation |
| Zippy_bytes reference | 4.561222 | independent set representation |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/programs/05-aunt-synthetic-process-sc-static-family-24-people/symbols.cpp) | 0.109632 | optimized graph |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/programs/05-aunt-synthetic-process-sc-static-family-24-people/bits.cpp) | 1.073811 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/programs/05-aunt-synthetic-process-sc-static-family-24-people/bytes.cpp) | 0.266232 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/programs/05-aunt-synthetic-process-sc-static-family-24-people/bytes-interned.rs) | 0.032023 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/programs/05-aunt-synthetic-process-sc-static-family-24-people/bytes-strings.rs) | 0.134604 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/programs/05-aunt-synthetic-process-sc-static-family-24-people/bytes-length.rs) | 0.050884 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/programs/05-aunt-synthetic-process-sc-static-family-24-people/bits-bytes.rs) | 0.187860 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 6.079 | 554.808 |  |
| C++ OpenFst bits | 173.018 | 2665.603 |  |
| C++ OpenFst bytes | 8.984 | 805.581 |  |
| Rust PathMap bytes-interned | 9.345 | 1889.891 | 3 |
| Rust PathMap bytes-strings | 12.839 | 2512.703 | 3 |
| Rust PathMap bytes-length | 1516.657 | 2316.244 | 3 |
| Rust PathMap bits-bytes | 84.180 | 9208.205 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | 11.027 | 2.455 | 13.482 | 13.745 |

## aunt query over large generated family, one queried person — 9 generations x 80 people (2720 facts)

Result: **0 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 0.058 | symbol trie |
| evalZ | 0.121 | zipper evaluator |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/large/32-aunt-query-over-large-generated-family-one-queried-person-9-generations-x-80-people-2720-facts/symbols.cpp) | 0.000270 | optimized graph |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/large/32-aunt-query-over-large-generated-family-one-queried-person-9-generations-x-80-people-2720-facts/bits.cpp) | 0.010239 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/large/32-aunt-query-over-large-generated-family-one-queried-person-9-generations-x-80-people-2720-facts/bytes.cpp) | 0.000635 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/large/32-aunt-query-over-large-generated-family-one-queried-person-9-generations-x-80-people-2720-facts/bytes-interned.rs) | 0.000708 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/large/32-aunt-query-over-large-generated-family-one-queried-person-9-generations-x-80-people-2720-facts/bytes-strings.rs) | 0.002269 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/large/32-aunt-query-over-large-generated-family-one-queried-person-9-generations-x-80-people-2720-facts/bytes-length.rs) | 0.003831 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/large/32-aunt-query-over-large-generated-family-one-queried-person-9-generations-x-80-people-2720-facts/bits-bytes.rs) | 0.004616 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 3.742 | 1.918 |  |
| C++ OpenFst bits | 241.218 | 4508.572 |  |
| C++ OpenFst bytes | 3.993 | 955.908 |  |
| Rust PathMap bytes-interned | 11.884 | 1928.759 | 3 |
| Rust PathMap bytes-strings | 24.762 | 2.787 | 3 |
| Rust PathMap bytes-length | 2113.947 | 2.956 | 3 |
| Rust PathMap bits-bytes | 231.872 | 7883.640 | 1 |

## aunt query over large generated family, one queried person — 11 generations x 160 people (6720 facts)

Result: **0 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 0.042 | symbol trie |
| evalZ | 0.081 | zipper evaluator |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/large/33-aunt-query-over-large-generated-family-one-queried-person-11-generations-x-160-people-6720-facts/symbols.cpp) | 0.000282 | optimized graph |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/large/33-aunt-query-over-large-generated-family-one-queried-person-11-generations-x-160-people-6720-facts/bits.cpp) | 0.012137 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/large/33-aunt-query-over-large-generated-family-one-queried-person-11-generations-x-160-people-6720-facts/bytes.cpp) | 0.000650 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/large/33-aunt-query-over-large-generated-family-one-queried-person-11-generations-x-160-people-6720-facts/bytes-interned.rs) | 0.000717 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/large/33-aunt-query-over-large-generated-family-one-queried-person-11-generations-x-160-people-6720-facts/bytes-strings.rs) | 0.002570 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/large/33-aunt-query-over-large-generated-family-one-queried-person-11-generations-x-160-people-6720-facts/bytes-length.rs) | 0.004503 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/large/33-aunt-query-over-large-generated-family-one-queried-person-11-generations-x-160-people-6720-facts/bits-bytes.rs) | 0.004048 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 6.644 | 2.414 |  |
| C++ OpenFst bits | 253.797 | 4922.971 |  |
| C++ OpenFst bytes | 11.913 | 782.672 |  |
| Rust PathMap bytes-interned | 7.539 | 1874.534 | 3 |
| Rust PathMap bytes-strings | 14.230 | 1.795 | 3 |
| Rust PathMap bytes-length | 1650.343 | 1.937 | 3 |
| Rust PathMap bits-bytes | 163.461 | 8682.290 | 1 |
