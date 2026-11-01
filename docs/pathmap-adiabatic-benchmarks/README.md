# PathMap adiabatic reuse: cornerstone results

Recorded on 2026-11-01 on a Symba h4d xlarge (192 AMD EPYC Turin CPUs).

Every cornerstone is listed separately: five Scala evaluators, two digit references, three C++ dialects, and four Rust byte encodings. Supplementary large cases retain their two Scala evaluators and all seven native variants. All 434 runtime cells are measured and every native cornerstone result was checked exactly.

| Example group | Variants |
| --- | --- |
| [Aunt queries](aunt.md) | 7 |
| [Graph queries](graph.md) | 5 |
| [Strongly connected components](scc.md) | 2 |
| [Semi-naive Datalog](datalog.md) | 4 |
| [Game of Life](life.md) | 3 |
| [Temperature](temperature.md) | 2 |
| [Sliding puzzle](sliding-puzzle.md) | 5 |
| [N-queens](n-queens.md) | 2 |
| [Supplement: large selective products](large-products.md) | 6 |

The [complete CSV](all-evaluators.csv) includes native session setup, graph/source compiler times, and Scala graph compile + execution. The [Rust review bundle](../compiled-cornerstones-rust/README.md) contains 144 sources, graph dumps, exact inputs and independent expected results. [Validation evidence](../validation/pathmap-adiabatic/README.md) retains all nine native benchmark families, microbenchmarks and the fourteen original evaluator reports.

[Translation and performance notes](performance.md) explain the main implementation choices and include the large fold and product results.

Native timings include graph execution and exact result cardinality; compilation, input construction, protocol IO and output enumeration are excluded. Constant-folded outputs are marked. Rust kernels always use release level 3; the table records the size-based generated-code level. Session setup can hit the compilation cache and is not a cold compiler comparison.

The full matrix ran with four Rust and four C++ benchmark workers and four shards per family while validation used other cores. Treat small timing differences as noise. The original Scala reports, digit-reference runs and extra zipper runs have their own warmup policies; raw evidence is retained. The three source evalZ cases omitted by the legacy report were measured separately with exact checks.

¹ Native session setup includes any compilation/cache lookup and process startup.
