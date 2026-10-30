# Aunt queries

[All examples](README.md) · Milliseconds per evaluation.

## aunt — reference

Result: **3 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.111 | reference path sets |
| ROG exec | 0.194 |  |
| evalTrie | 0.452 | symbol trie |
| evalZ | 0.693 | zipper evaluator |
| ROG execT | 0.428 |  |
| Zippy_bits reference | 3.394622 | independent set representation |
| Zippy_bytes reference | 3.085388 | independent set representation |
| C++ OpenFst symbols | 0.013284 | optimized graph |
| C++ OpenFst bits | 0.086720 | optimized graph |
| C++ OpenFst bytes | 0.040120 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/programs/01-aunt-reference/bytes-interned.rs) | 0.010026 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/programs/01-aunt-reference/bytes-strings.rs) | 0.033582 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/programs/01-aunt-reference/bits-bytes.rs) | 0.026691 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 15.014 | 9857.760 |  |
| C++ OpenFst bits | 157.859 | 1811.275 |  |
| C++ OpenFst bytes | 7.031 | 660.075 |  |
| Rust PathMap bytes-interned | 38.968 | 55.141 | 3 |
| Rust PathMap bytes-strings | 54.689 | 2.126 | 3 |
| Rust PathMap bits-bytes | 195.516 | 7.015 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 4.548 | 4.548 | 4.977 |

## aunt — process-sc static family

Result: **3 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.082 | reference path sets |
| ROG exec | 0.215 |  |
| evalTrie | 0.294 | symbol trie |
| evalZ | 0.590 | zipper evaluator |
| ROG execT | 0.249 |  |
| Zippy_bits reference | 3.614202 | independent set representation |
| Zippy_bytes reference | 1.735033 | independent set representation |
| C++ OpenFst symbols | 0.013572 | optimized graph |
| C++ OpenFst bits | 0.085692 | optimized graph |
| C++ OpenFst bytes | 0.035663 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/programs/02-aunt-process-sc-static-family/bytes-interned.rs) | 0.008915 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/programs/02-aunt-process-sc-static-family/bytes-strings.rs) | 0.031584 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/programs/02-aunt-process-sc-static-family/bits-bytes.rs) | 0.026549 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 16.572 | 9832.260 |  |
| C++ OpenFst bits | 125.901 | 1570.430 |  |
| C++ OpenFst bytes | 7.996 | 643.159 |  |
| Rust PathMap bytes-interned | 39.896 | 55.685 | 3 |
| Rust PathMap bytes-strings | 49.866 | 2.059 | 3 |
| Rust PathMap bits-bytes | 167.743 | 6.160 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | 51.334 | 2.252 | 53.586 | 53.835 |

## aunt synthetic — reference 60 people

Result: **36 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 2.357 | reference path sets |
| ROG exec | 0.883 |  |
| evalTrie | 1.174 | symbol trie |
| evalZ | 2.576 | zipper evaluator |
| ROG execT | 1.055 |  |
| Zippy_bits reference | 14.963645 | independent set representation |
| Zippy_bytes reference | 39.712248 | independent set representation |
| C++ OpenFst symbols | 0.338289 | optimized graph |
| C++ OpenFst bits | 4.186409 | optimized graph |
| C++ OpenFst bytes | 0.796804 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/programs/03-aunt-synthetic-reference-60-people/bytes-interned.rs) | 0.138071 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/programs/03-aunt-synthetic-reference-60-people/bytes-strings.rs) | 0.432415 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/programs/03-aunt-synthetic-reference-60-people/bits-bytes.rs) | 0.822852 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 2.879 | 1.538 |  |
| C++ OpenFst bits | 112.019 | 2591.291 |  |
| C++ OpenFst bytes | 6.961 | 658.684 |  |
| Rust PathMap bytes-interned | 38.543 | 56.847 | 3 |
| Rust PathMap bytes-strings | 49.338 | 1.828 | 3 |
| Rust PathMap bits-bytes | 261.290 | 10.231 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 2.456 | 2.456 | 3.511 |

## aunt royal92 — reference royal92_simple.metta fallback

Result: **3 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.041 | reference path sets |
| ROG exec | 0.042 |  |
| evalTrie | 0.094 | symbol trie |
| evalZ | 0.144 | zipper evaluator |
| ROG execT | 0.130 |  |
| Zippy_bits reference | 3.257156 | independent set representation |
| Zippy_bytes reference | 3.229133 | independent set representation |
| C++ OpenFst symbols | 0.013305 | optimized graph |
| C++ OpenFst bits | 0.086705 | optimized graph |
| C++ OpenFst bytes | 0.039521 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/programs/04-aunt-royal92-reference-royal92-simple-metta-fallback/bytes-interned.rs) | 0.009977 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/programs/04-aunt-royal92-reference-royal92-simple-metta-fallback/bytes-strings.rs) | 0.033738 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/programs/04-aunt-royal92-reference-royal92-simple-metta-fallback/bits-bytes.rs) | 0.026521 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 2.562 | 1.162 |  |
| C++ OpenFst bits | 88.560 | 2.047 |  |
| C++ OpenFst bytes | 3.420 | 101.946 |  |
| Rust PathMap bytes-interned | 38.469 | 54.835 | 3 |
| Rust PathMap bytes-strings | 48.854 | 1.696 | 3 |
| Rust PathMap bits-bytes | 192.314 | 6.983 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 2.133 | 2.133 | 2.263 |

## aunt synthetic — process-sc static family 24 people

Result: **12 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.120 | reference path sets |
| ROG exec | 0.279 |  |
| evalTrie | 1.882 | symbol trie |
| evalZ | 1.559 | zipper evaluator |
| ROG execT | 0.297 |  |
| Zippy_bits reference | 5.254281 | independent set representation |
| Zippy_bytes reference | 9.071447 | independent set representation |
| C++ OpenFst symbols | 0.106017 | optimized graph |
| C++ OpenFst bits | 1.063523 | optimized graph |
| C++ OpenFst bytes | 0.270071 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/programs/05-aunt-synthetic-process-sc-static-family-24-people/bytes-interned.rs) | 0.043170 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/programs/05-aunt-synthetic-process-sc-static-family-24-people/bytes-strings.rs) | 0.142669 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/programs/05-aunt-synthetic-process-sc-static-family-24-people/bits-bytes.rs) | 0.202949 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 5.909 | 452.917 |  |
| C++ OpenFst bits | 122.767 | 2071.951 |  |
| C++ OpenFst bytes | 5.697 | 648.233 |  |
| Rust PathMap bytes-interned | 11.793 | 1.518 | 3 |
| Rust PathMap bytes-strings | 15.892 | 1.851 | 3 |
| Rust PathMap bits-bytes | 141.013 | 7.685 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | 9.727 | 2.001 | 11.728 | 12.025 |

## aunt query over large generated family, one queried person — 9 generations x 80 people (2720 facts)

Result: **0 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 0.064 | symbol trie |
| evalZ | 0.112 | zipper evaluator |
| C++ OpenFst symbols | 0.000267 | optimized graph |
| C++ OpenFst bits | 0.010204 | optimized graph |
| C++ OpenFst bytes | 0.000639 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/large/32-aunt-query-over-large-generated-family-one-queried-person-9-generations-x-80-people-2720-facts/bytes-interned.rs) | 0.000799 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/large/32-aunt-query-over-large-generated-family-one-queried-person-9-generations-x-80-people-2720-facts/bytes-strings.rs) | 0.004191 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/large/32-aunt-query-over-large-generated-family-one-queried-person-9-generations-x-80-people-2720-facts/bits-bytes.rs) | 0.006695 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 3.401 | 1.470 |  |
| C++ OpenFst bits | 136.574 | 4009.639 |  |
| C++ OpenFst bytes | 3.491 | 714.618 |  |
| Rust PathMap bytes-interned | 7.841 | 1.519 | 3 |
| Rust PathMap bytes-strings | 15.614 | 1.795 | 3 |
| Rust PathMap bits-bytes | 192.749 | 14.068 | 1 |

## aunt query over large generated family, one queried person — 11 generations x 160 people (6720 facts)

Result: **0 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 0.040 | symbol trie |
| evalZ | 0.077 | zipper evaluator |
| C++ OpenFst symbols | 0.000255 | optimized graph |
| C++ OpenFst bits | 0.011343 | optimized graph |
| C++ OpenFst bytes | 0.000639 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/large/33-aunt-query-over-large-generated-family-one-queried-person-11-generations-x-160-people-6720-facts/bytes-interned.rs) | 0.000826 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/large/33-aunt-query-over-large-generated-family-one-queried-person-11-generations-x-160-people-6720-facts/bytes-strings.rs) | 0.004946 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/large/33-aunt-query-over-large-generated-family-one-queried-person-11-generations-x-160-people-6720-facts/bits-bytes.rs) | 0.006170 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 3.088 | 1.639 |  |
| C++ OpenFst bits | 137.601 | 4479.799 |  |
| C++ OpenFst bytes | 3.461 | 701.333 |  |
| Rust PathMap bytes-interned | 7.465 | 1.582 | 3 |
| Rust PathMap bytes-strings | 14.778 | 1.700 | 3 |
| Rust PathMap bits-bytes | 294.398 | 19.020 | 1 |
