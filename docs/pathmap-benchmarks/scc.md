# Strongly connected components

[All examples](README.md) · Milliseconds per evaluation.

## scc — paper divide-and-conquer

Result: **2 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.679 | reference path sets |
| ROG exec | 0.412 | exec fallback Call dispatch to optimized callee graph(s): reachable, seedless_scc |
| evalTrie | 1.042 | symbol trie |
| evalZ | 4.218 | zipper evaluator |
| ROG execT | 0.282 | exec fallback Call dispatch to optimized callee graph(s): reachable, seedless_scc |
| Zippy_bits reference | 6.544096 | independent set representation |
| Zippy_bytes reference | 5.403195 | independent set representation |
| C++ OpenFst symbols | 0.195221 | optimized graph |
| C++ OpenFst bits | 1.132906 | optimized graph |
| C++ OpenFst bytes | 0.521765 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/programs/08-scc-paper-divide-and-conquer/bytes-interned.rs) | 0.063627 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/programs/08-scc-paper-divide-and-conquer/bytes-strings.rs) | 0.084684 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/programs/08-scc-paper-divide-and-conquer/bits-bytes.rs) | 0.159564 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 18.687 | 611.628 |  |
| C++ OpenFst bits | 214.001 | 5688.523 |  |
| C++ OpenFst bytes | 41.164 | 1315.849 |  |
| Rust PathMap bytes-interned | 63.083 | 3.257 | 3 |
| Rust PathMap bytes-strings | 189.760 | 4.199 | 3 |
| Rust PathMap bits-bytes | 302.180 | 11.782 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 32.726 | 32.726 | 33.009 |

## SCC divide-and-conquer — 6 directed edges, 2 representative/member pairs

Result: **2 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 2.943 | symbol trie |
| evalZ | 6.929 | zipper evaluator |
| C++ OpenFst symbols | 0.195834 | optimized graph |
| C++ OpenFst bits | 1.141633 | optimized graph |
| C++ OpenFst bytes | 0.516268 | optimized graph |
| [Rust PathMap bytes-interned](../../output/pathmap-zipper/final-rust/large/34-scc-divide-and-conquer-6-directed-edges-2-representative-member-pairs/bytes-interned.rs) | 0.063789 | optimized graph |
| [Rust PathMap bytes-strings](../../output/pathmap-zipper/final-rust/large/34-scc-divide-and-conquer-6-directed-edges-2-representative-member-pairs/bytes-strings.rs) | 0.084358 | optimized graph |
| [Rust PathMap bits-bytes](../../output/pathmap-zipper/final-rust/large/34-scc-divide-and-conquer-6-directed-edges-2-representative-member-pairs/bits-bytes.rs) | 0.157059 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 26.875 | 1.580 |  |
| C++ OpenFst bits | 188.464 | 2.356 |  |
| C++ OpenFst bytes | 36.006 | 1.536 |  |
| Rust PathMap bytes-interned | 185.470 | 3.345 | 3 |
| Rust PathMap bytes-strings | 292.509 | 4.753 | 3 |
| Rust PathMap bits-bytes | 386.626 | 11.633 | 3 |
