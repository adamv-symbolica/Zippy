# Aunt queries

[All examples](README.md) · Milliseconds per evaluation.

## aunt — reference

Result: **3 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.111 | reference path sets |
| ROG exec | 0.193 |  |
| evalTrie | 0.455 | symbol trie |
| evalZ | 0.685 | zipper evaluator |
| ROG execT | 0.311 |  |
| Zippy_bits reference | 5.326830 | independent set representation |
| Zippy_bytes reference | 3.789474 | independent set representation |
| C++ OpenFst symbols | 0.013823 | optimized graph |
| C++ OpenFst bits | 0.090918 | optimized graph |
| C++ OpenFst bytes | 0.047861 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/programs/01-aunt-reference/bytes-interned.rs) | 0.009638 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/programs/01-aunt-reference/bytes-strings.rs) | 0.030184 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/programs/01-aunt-reference/bits-bytes.rs) | 0.025153 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 16.742 | 9707.996 |  |
| C++ OpenFst bits | 191.472 | 2055.967 |  |
| C++ OpenFst bytes | 22.312 | 828.258 |  |
| Rust PathMap bytes-interned | 50.884 | 24.164 | 3 |
| Rust PathMap bytes-strings | 64.651 | 1.715 | 3 |
| Rust PathMap bits-bytes | 127.815 | 2.845 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 5.195 | 5.195 | 5.505 |

## aunt — process-sc static family

Result: **3 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.072 | reference path sets |
| ROG exec | 0.186 |  |
| evalTrie | 0.237 | symbol trie |
| evalZ | 0.536 | zipper evaluator |
| ROG execT | 0.178 |  |
| Zippy_bits reference | 5.547021 | independent set representation |
| Zippy_bytes reference | 2.478938 | independent set representation |
| C++ OpenFst symbols | 0.013879 | optimized graph |
| C++ OpenFst bits | 0.085472 | optimized graph |
| C++ OpenFst bytes | 0.043529 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/programs/02-aunt-process-sc-static-family/bytes-interned.rs) | 0.009205 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/programs/02-aunt-process-sc-static-family/bytes-strings.rs) | 0.029003 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/programs/02-aunt-process-sc-static-family/bits-bytes.rs) | 0.025510 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 17.487 | 9791.591 |  |
| C++ OpenFst bits | 189.063 | 1833.325 |  |
| C++ OpenFst bytes | 10.609 | 811.525 |  |
| Rust PathMap bytes-interned | 51.137 | 23.339 | 3 |
| Rust PathMap bytes-strings | 47.017 | 2.044 | 3 |
| Rust PathMap bits-bytes | 126.363 | 2.356 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | 58.236 | 2.358 | 60.594 | 60.772 |

## aunt synthetic — reference 60 people

Result: **36 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 2.484 | reference path sets |
| ROG exec | 1.544 |  |
| evalTrie | 0.869 | symbol trie |
| evalZ | 2.239 | zipper evaluator |
| ROG execT | 0.820 |  |
| Zippy_bits reference | 18.046209 | independent set representation |
| Zippy_bytes reference | 43.756746 | independent set representation |
| C++ OpenFst symbols | 0.346881 | optimized graph |
| C++ OpenFst bits | 5.294341 | optimized graph |
| C++ OpenFst bytes | 0.883468 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/programs/03-aunt-synthetic-reference-60-people/bytes-interned.rs) | 0.152270 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/programs/03-aunt-synthetic-reference-60-people/bytes-strings.rs) | 0.404459 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/programs/03-aunt-synthetic-reference-60-people/bits-bytes.rs) | 0.753340 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 35.063 | 9624.497 |  |
| C++ OpenFst bits | 238.966 | 3063.062 |  |
| C++ OpenFst bytes | 12.300 | 872.708 |  |
| Rust PathMap bytes-interned | 48.021 | 23.533 | 3 |
| Rust PathMap bytes-strings | 76.007 | 2.018 | 3 |
| Rust PathMap bits-bytes | 159.605 | 2.805 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 2.022 | 2.022 | 2.843 |

## aunt royal92 — reference royal92_simple.metta fallback

Result: **3 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.070 | reference path sets |
| ROG exec | 0.065 |  |
| evalTrie | 0.068 | symbol trie |
| evalZ | 0.109 | zipper evaluator |
| ROG execT | 0.045 |  |
| Zippy_bits reference | 4.363546 | independent set representation |
| Zippy_bytes reference | 6.912760 | independent set representation |
| C++ OpenFst symbols | 0.013518 | optimized graph |
| C++ OpenFst bits | 0.089185 | optimized graph |
| C++ OpenFst bytes | 0.044018 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/programs/04-aunt-royal92-reference-royal92-simple-metta-fallback/bytes-interned.rs) | 0.009609 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/programs/04-aunt-royal92-reference-royal92-simple-metta-fallback/bytes-strings.rs) | 0.030297 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/programs/04-aunt-royal92-reference-royal92-simple-metta-fallback/bits-bytes.rs) | 0.025244 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 16.121 | 9867.573 |  |
| C++ OpenFst bits | 181.350 | 2066.641 |  |
| C++ OpenFst bytes | 17.361 | 829.969 |  |
| Rust PathMap bytes-interned | 49.323 | 21.153 | 3 |
| Rust PathMap bytes-strings | 63.137 | 2.445 | 3 |
| Rust PathMap bits-bytes | 123.088 | 3.305 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 2.073 | 2.073 | 2.119 |

## aunt synthetic — process-sc static family 24 people

Result: **12 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.121 | reference path sets |
| ROG exec | 0.218 |  |
| evalTrie | 1.129 | symbol trie |
| evalZ | 1.137 | zipper evaluator |
| ROG execT | 0.178 |  |
| Zippy_bits reference | 6.556755 | independent set representation |
| Zippy_bytes reference | 11.751087 | independent set representation |
| C++ OpenFst symbols | 0.123003 | optimized graph |
| C++ OpenFst bits | 1.067142 | optimized graph |
| C++ OpenFst bytes | 0.283666 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/programs/05-aunt-synthetic-process-sc-static-family-24-people/bytes-interned.rs) | 0.045072 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/programs/05-aunt-synthetic-process-sc-static-family-24-people/bytes-strings.rs) | 0.130282 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/programs/05-aunt-synthetic-process-sc-static-family-24-people/bits-bytes.rs) | 0.187393 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 17.663 | 601.227 |  |
| C++ OpenFst bits | 201.815 | 2517.220 |  |
| C++ OpenFst bytes | 13.960 | 791.755 |  |
| Rust PathMap bytes-interned | 12.533 | 1.869 | 3 |
| Rust PathMap bytes-strings | 29.649 | 2.210 | 3 |
| Rust PathMap bits-bytes | 98.204 | 2.213 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | 8.620 | 1.634 | 10.254 | 10.433 |

## aunt query over large generated family, one queried person — 9 generations x 80 people (2720 facts)

Result: **0 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 0.097 | symbol trie |
| evalZ | 0.159 | zipper evaluator |
| C++ OpenFst symbols | 0.000280 | optimized graph |
| C++ OpenFst bits | 0.011091 | optimized graph |
| C++ OpenFst bytes | 0.000697 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/large/32-aunt-query-over-large-generated-family-one-queried-person-9-generations-x-80-people-2720-facts/bytes-interned.rs) | 0.000737 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/large/32-aunt-query-over-large-generated-family-one-queried-person-9-generations-x-80-people-2720-facts/bytes-strings.rs) | 0.003370 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/large/32-aunt-query-over-large-generated-family-one-queried-person-9-generations-x-80-people-2720-facts/bits-bytes.rs) | 0.004390 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 4.586 | 3.485 |  |
| C++ OpenFst bits | 250.243 | 4839.054 |  |
| C++ OpenFst bytes | 8.848 | 912.664 |  |
| Rust PathMap bytes-interned | 8.766 | 1.882 | 3 |
| Rust PathMap bytes-strings | 16.436 | 1.710 | 3 |
| Rust PathMap bits-bytes | 197.047 | 3.825 | 3 |

## aunt query over large generated family, one queried person — 11 generations x 160 people (6720 facts)

Result: **0 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 0.040 | symbol trie |
| evalZ | 0.088 | zipper evaluator |
| C++ OpenFst symbols | 0.000283 | optimized graph |
| C++ OpenFst bits | 0.012260 | optimized graph |
| C++ OpenFst bytes | 0.000723 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/large/33-aunt-query-over-large-generated-family-one-queried-person-11-generations-x-160-people-6720-facts/bytes-interned.rs) | 0.000742 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/large/33-aunt-query-over-large-generated-family-one-queried-person-11-generations-x-160-people-6720-facts/bytes-strings.rs) | 0.004064 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/large/33-aunt-query-over-large-generated-family-one-queried-person-11-generations-x-160-people-6720-facts/bits-bytes.rs) | 0.004125 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 6.678 | 2.419 |  |
| C++ OpenFst bits | 297.343 | 5614.371 |  |
| C++ OpenFst bytes | 8.814 | 1044.580 |  |
| Rust PathMap bytes-interned | 9.364 | 1.945 | 3 |
| Rust PathMap bytes-strings | 19.163 | 1.976 | 3 |
| Rust PathMap bits-bytes | 167.208 | 3.685 | 3 |
