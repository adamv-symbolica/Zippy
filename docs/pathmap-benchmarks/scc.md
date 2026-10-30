# Strongly connected components

[All examples](README.md) · Milliseconds per evaluation.

## scc — paper divide-and-conquer

Result: **2 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| eval Set | 0.628 | reference path sets |
| ROG exec | 2.503 | exec fallback Call dispatch to optimized callee graph(s): reachable, seedless_scc |
| evalTrie | 0.597 | symbol trie |
| evalZ | 4.110 | zipper evaluator |
| ROG execT | 0.337 | exec fallback Call dispatch to optimized callee graph(s): reachable, seedless_scc |
| Zippy_bits reference | 8.751147 | independent set representation |
| Zippy_bytes reference | 10.317366 | independent set representation |
| C++ OpenFst symbols | 0.227504 | optimized graph |
| C++ OpenFst bits | 1.223636 | optimized graph |
| C++ OpenFst bytes | 0.568092 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/programs/08-scc-paper-divide-and-conquer/bytes-interned.rs) | 0.063394 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/programs/08-scc-paper-divide-and-conquer/bytes-strings.rs) | 0.094679 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/programs/08-scc-paper-divide-and-conquer/bits-bytes.rs) | 0.158757 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 47.438 | 610.481 |  |
| C++ OpenFst bits | 416.766 | 9353.684 |  |
| C++ OpenFst bytes | 59.914 | 1967.613 |  |
| Rust PathMap bytes-interned | 101.138 | 2.197 | 3 |
| Rust PathMap bytes-strings | 182.791 | 2.432 | 3 |
| Rust PathMap bits-bytes | 211.597 | 3.345 | 3 |

| Scala graph pipeline | Prep compile ms | Graph compile ms | Total compile ms | Compile + ROG execT ms |
| --- | --- | --- | --- | --- |
| optimized executor | n/a | 24.449 | 24.449 | 24.786 |

## SCC divide-and-conquer — 6 directed edges, 2 representative/member pairs

Result: **2 paths**.

| Evaluator / compiled encoding | Runtime ms | Execution |
| --- | --- | --- |
| evalTrie | 1.833 | symbol trie |
| evalZ | 6.606 | zipper evaluator |
| C++ OpenFst symbols | 0.224230 | optimized graph |
| C++ OpenFst bits | 1.257406 | optimized graph |
| C++ OpenFst bytes | 0.576252 | optimized graph |
| [Rust PathMap bytes-interned](../../output/cornerstone-rust-review/large/34-scc-divide-and-conquer-6-directed-edges-2-representative-member-pairs/bytes-interned.rs) | 0.064608 | optimized graph |
| [Rust PathMap bytes-strings](../../output/cornerstone-rust-review/large/34-scc-divide-and-conquer-6-directed-edges-2-representative-member-pairs/bytes-strings.rs) | 0.094127 | optimized graph |
| [Rust PathMap bits-bytes](../../output/cornerstone-rust-review/large/34-scc-divide-and-conquer-6-directed-edges-2-representative-member-pairs/bits-bytes.rs) | 0.158883 | optimized graph |

| Compiled backend | Graph/source compile ms | Session setup ms¹ | Rust opt-level |
| --- | --- | --- | --- |
| C++ OpenFst symbols | 183.357 | 848.211 |  |
| C++ OpenFst bits | 831.498 | 8964.990 |  |
| C++ OpenFst bytes | 108.509 | 1913.956 |  |
| Rust PathMap bytes-interned | 209.418 | 2.737 | 3 |
| Rust PathMap bytes-strings | 245.728 | 2.551 | 3 |
| Rust PathMap bits-bytes | 232.626 | 4.235 | 3 |
