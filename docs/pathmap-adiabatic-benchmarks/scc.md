# Strongly connected components

[All examples](README.md) · Milliseconds per evaluation.

## scc — paper divide-and-conquer

Result: **2 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.784 | reference path sets |
| ROG exec | 0.469 | exec fallback Call dispatch to optimized callee graph(s): reachable, seedless_scc |
| evalTrie | 1.270 | symbol trie |
| evalZ | 3.989 | zipper evaluator |
| ROG execT | 0.363 | exec fallback Call dispatch to optimized callee graph(s): reachable, seedless_scc |
| Zippy_bits reference | 6.511978 | independent set representation |
| Zippy_bytes reference | 7.649294 | independent set representation |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/programs/08-scc-paper-divide-and-conquer/symbols.cpp) | 0.199734 | optimized graph |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/programs/08-scc-paper-divide-and-conquer/bits.cpp) | 1.127410 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/programs/08-scc-paper-divide-and-conquer/bytes.cpp) | 0.494826 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/programs/08-scc-paper-divide-and-conquer/bytes-interned.rs) | 0.052388 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/programs/08-scc-paper-divide-and-conquer/bytes-strings.rs) | 0.091943 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/programs/08-scc-paper-divide-and-conquer/bytes-length.rs) | 0.070971 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/programs/08-scc-paper-divide-and-conquer/bits-bytes.rs) | 0.151490 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 28.721 | 760.510 |  |
| C++ OpenFst bits | 366.227 | 9313.935 |  |
| C++ OpenFst bytes | 45.573 | 1805.569 |  |
| Rust PathMap bytes-interned | 63.864 | 2892.955 | 3 |
| Rust PathMap bytes-strings | 117.319 | 4307.135 | 3 |
| Rust PathMap bytes-length | 8895.946 | 3786.063 | 3 |
| Rust PathMap bits-bytes | 189.146 | 13324.517 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 36.390 | 36.390 | 36.752 |

## SCC divide-and-conquer — 6 directed edges, 2 representative/member pairs

Result: **2 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 2.718 | symbol trie |
| evalZ | 5.732 | zipper evaluator |
| [C++ OpenFst symbols](../../output/pathmap-adiabatic/cpp-cornerstones/large/34-scc-divide-and-conquer-6-directed-edges-2-representative-member-pairs/symbols.cpp) | 0.193609 | optimized graph |
| [C++ OpenFst bits](../../output/pathmap-adiabatic/cpp-cornerstones/large/34-scc-divide-and-conquer-6-directed-edges-2-representative-member-pairs/bits.cpp) | 1.136787 | optimized graph |
| [C++ OpenFst bytes](../../output/pathmap-adiabatic/cpp-cornerstones/large/34-scc-divide-and-conquer-6-directed-edges-2-representative-member-pairs/bytes.cpp) | 0.508200 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-adiabatic/cornerstones/large/34-scc-divide-and-conquer-6-directed-edges-2-representative-member-pairs/bytes-interned.rs) | 0.052746 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-adiabatic/cornerstones/large/34-scc-divide-and-conquer-6-directed-edges-2-representative-member-pairs/bytes-strings.rs) | 0.092021 | optimized graph |
| [Rust PathMap bytes-length](../../output/pathmap-adiabatic/cornerstones/large/34-scc-divide-and-conquer-6-directed-edges-2-representative-member-pairs/bytes-length.rs) | 0.071180 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-adiabatic/cornerstones/large/34-scc-divide-and-conquer-6-directed-edges-2-representative-member-pairs/bits-bytes.rs) | 0.151811 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 105.177 | 3.794 |  |
| C++ OpenFst bits | 488.405 | 6.793 |  |
| C++ OpenFst bytes | 47.610 | 2.651 |  |
| Rust PathMap bytes-interned | 209.638 | 4.594 | 3 |
| Rust PathMap bytes-strings | 279.233 | 5.231 | 3 |
| Rust PathMap bytes-length | 8977.752 | 3.352 | 3 |
| Rust PathMap bits-bytes | 231.982 | 10.990 | 3 |
