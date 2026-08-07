# Trie Runtime Benchmarks

Measured with `TrieBenchmarks` on this worktree. Runtime times are average milliseconds per evaluation after two warmup runs. The first table excludes compilation/supercompilation and graph construction; rows labelled `process-sc` or `compile-pass` time the residual/compiled runtime. Runtime speedup cells and compile/run ratios are deliberately omitted for graphs that have been completely residualized to a one-node literal; use the compile+run column in the second table for those rows. The second table reports setup supercompilation time, graph compilation time, total compile time, compile/run ratio for non-residual graph execution, compile+`ROG execT` time, timed compile-stage totals, constant-folding eval time, and optimization-pass totals under explicit compile budgets. Graph optimization includes loop-invariant subgraph hoisting, push-out, and sharing. `ROG exec` is the legacy `RecursiveOpGraph` executor over `SpaceValue`; `evalZ` is the declarative zipper traversal evaluator that composes logical trie cursors and materializes only at the result boundary; `ROG execT` is the interned trie executor over `TrieSpace` and `List[Int]` path slots. Graph rows use fully optimized graphs: helper functions are inlined/expanded/lowered where possible, any surviving nonrecursive `Call`s dispatch to optimized callee graphs, and graph-note cells list retained calls found in both the top graph and optimized callee graphs. Recursive source `Call` cycles that survive lowering are marked unsupported.

## Runtime

| benchmark | variant | result paths | eval Set ms | ROG exec ms | evalTrie ms | evalZ ms | ROG execT ms | evalTrie / eval | evalZ / eval | evalZ / evalTrie | ROG exec / eval | ROG execT / eval | ROG execT / evalTrie | graph nodes | graph note |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| aunt | reference | 3 | 0.125 | 0.337 | 0.404 | 0.757 | 0.449 | 0.31 x | 0.17 x | 0.53 x | 0.37 x | 0.28 x | 0.90 x | 21 |  |
| aunt | process-sc static family | 3 | 0.124 | 0.248 | 0.558 | 1.169 | 0.243 | 0.22 x | 0.11 x | 0.48 x | 0.50 x | 0.51 x | 2.30 x | 20 |  |
| aunt synthetic | reference 60 people | 36 | 5.049 | 1.424 | 1.304 | 2.873 | 1.365 | 3.87 x | 1.76 x | 0.45 x | 3.55 x | 3.70 x | 0.96 x | 21 |  |
| aunt royal92 | reference royal92_simple.metta fallback | 3 | 0.106 | 0.106 | 0.112 | 0.133 | 0.105 | 0.95 x | 0.79 x | 0.84 x | 1.00 x | 1.01 x | 1.06 x | 21 |  |
| aunt synthetic | process-sc static family 24 people | 12 | 0.130 | 0.196 | 2.061 | 1.217 | 0.149 | 0.06 x | 0.11 x | 1.69 x | 0.66 x | 0.87 x | 13.81 x | 20 |  |
| graph two-hop | reference 90-chain | 264 | 3.438 | 1.726 | 0.839 | 15.892 | 0.313 | 4.10 x | 0.22 x | 0.05 x | 1.99 x | 10.97 x | 2.68 x | 15 |  |
| graph mutual | reference 90-chain | 0 | 3.213 | 0.922 | 0.256 | 1.096 | 0.160 | 12.54 x | 2.93 x | 0.23 x | 3.48 x | 20.05 x | 1.60 x | 15 |  |
| datalog semi-naive | reference 24-chain | 300 | 268.240 | 0.001 | 6.232 | n/a | 0.002 | 43.04 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ rejects the recursive top-level self-union; use evalTrie/graph rows |
| datalog semi-naive | process-sc 24-chain | 300 | 250.157 | 20.346 | 4.379 | n/a | 1.759 | 57.13 x | n/a | n/a | 12.30 x | 142.18 x | 2.49 x | 6 | exec fallback Call dispatch to optimized callee graph(s): step708533063_sc1; direct evalZ rejects the residual recursive top-level self-union; use evalTrie/graph rows |
| life | reference random 24x24 | 125 | 33.500 | 17.897 | 559.616 | 606.670 | 2.086 | 0.06 x | 0.06 x | 0.92 x | 1.87 x | 16.06 x | 268.22 x | 2,667 | exec fallback Call dispatch to optimized callee graph(s): neigh |
| life | compile-pass random 24x24 | 125 | 28.076 | 0.001 | 958.434 | 794.734 | 0.001 | 0.03 x | 0.04 x | 1.21 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| life | compile-pass random 24x24 initial literal | 125 | 0.001 | 0.001 | 0.117 | 0.111 | 0.001 | 0.01 x | 0.01 x | 1.05 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| temperature | NOAA committed slice | 430 | 0.277 | 0.128 | 0.028 | 0.053 | 0.012 | 10.04 x | 5.21 x | 0.52 x | 2.16 x | 22.79 x | 2.27 x | 3 |  |
| temperature | synthetic 32x64 | 448 | 1.379 | 1.343 | 0.023 | 0.094 | 0.006 | 58.90 x | 14.70 x | 0.25 x | 1.03 x | 245.83 x | 4.17 x | 3 |  |
| sliding puzzle | 2x2 pure source full frontier step | 12 | 5.058 | 0.001 | 5.007 | 9.779 | 0.001 | 1.01 x | 0.52 x | 0.51 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| sliding puzzle | 2x2 pure compile-pass full frontier step | 12 | 0.001 | 0.001 | 0.005 | 0.009 | 0.001 | 0.16 x | 0.09 x | 0.53 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| sliding puzzle | 3x3 pure source depth-8 step | 420 | 406.479 | 0.001 | 409.636 | n/a | 0.001 | 0.99 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ source search tree exceeds the benchmark heap; use evalTrie/graph rows |
| sliding puzzle | 3x3 pure compile-pass depth-8 step | 420 | 0.001 | 0.001 | 0.214 | 0.220 | 0.001 | 0.00 x | 0.00 x | 0.97 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| sliding puzzle | 4x4 pure source depth-5 step | 202 | 524.527 | 0.001 | 694.371 | n/a | 0.001 | 0.76 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ source search tree exceeds the benchmark heap; use evalTrie/graph rows |
| n-queens | MORKL 8x8 source | 92 | 481.246 | 0.001 | 1487.658 | n/a | 0.001 | 0.32 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ is currently omitted for the high-level source search tree; use compile-pass/graph rows |
| n-queens | MORKL 8x8 compile-pass | 92 | 0.001 | 0.001 | 0.039 | 0.039 | 0.001 | 0.02 x | 0.02 x | 1.02 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |

## Compilation And Optimization

| benchmark | variant | prep compile ms | graph compile ms | total compile ms | compile/run | compile+ROG execT ms | lower+inline ms | source pass ms | const-fold eval ms | const-fold evalTrie ms | const-fold evalZ ms | const-fold execT ms | const-fold eval calls | const-fold evalTrie calls | const-fold evalZ calls | const-fold execT calls | graph transpile ms | graph optimize ms | source pass attempts | graph pass steps | budget ms sum | note |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| aunt | reference | n/a | 2.958 | 2.958 | 6.59 x | 3.407 | 0.059 | 0.462 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.109 | 1.110 | 38 | 6 | 30000 | graph compile + optimized callees |
| aunt | process-sc static family | 53.718 | 5.736 | 59.454 | 245.04 x | 59.697 | 2.000 | 1.516 | 0.014 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.106 | 1.545 | 19 | 6 | 60000 | process SC setup; graph compile + optimized callees |
| aunt synthetic | reference 60 people | n/a | 1.775 | 1.775 | 1.30 x | 3.140 | 0.037 | 0.401 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.098 | 0.847 | 38 | 6 | 30000 | graph compile + optimized callees |
| aunt royal92 | reference royal92_simple.metta fallback | n/a | 1.458 | 1.458 | 13.85 x | 1.563 | 0.039 | 0.307 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.079 | 0.793 | 38 | 6 | 30000 | graph compile + optimized callees |
| aunt synthetic | process-sc static family 24 people | 10.941 | 1.887 | 12.827 | 85.96 x | 12.976 | 0.084 | 0.332 | 0.004 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.060 | 1.152 | 19 | 6 | 60000 | process SC setup; graph compile + optimized callees |
| graph two-hop | reference 90-chain | n/a | 1.610 | 1.610 | 5.14 x | 1.923 | 0.020 | 0.198 | 0.021 | 0.000 | 0.000 | 0.000 | 2 | 0 | 0 | 0 | 0.044 | 0.949 | 57 | 9 | 30000 | graph compile + optimized callees |
| graph mutual | reference 90-chain | n/a | 1.385 | 1.385 | 8.64 x | 1.546 | 0.020 | 0.101 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.043 | 0.831 | 38 | 9 | 30000 | graph compile + optimized callees |
| datalog semi-naive | reference 24-chain | n/a | 16.831 | 16.831 | n/a | 16.833 | 0.374 | 15.641 | 0.162 | 14.339 | 0.000 | 0.000 | 9 | 1 | 0 | 0 | 0.005 | 0.089 | 38 | 3 | 30000 | graph compile + optimized callees |
| datalog semi-naive | process-sc 24-chain | 79.867 | 12.273 | 92.140 | 52.37 x | 93.899 | 0.165 | 1.798 | 0.007 | 0.000 | 0.000 | 0.000 | 10 | 0 | 0 | 0 | 0.356 | 7.795 | 38 | 12 | 90000 | process SC setup; graph compile + optimized callees |
| life | reference random 24x24 | n/a | 3195.464 | 3195.464 | 1531.59 x | 3197.551 | 0.549 | 2898.293 | 0.326 | 0.000 | 0.000 | 0.000 | 1384 | 0 | 0 | 0 | 2.552 | 290.832 | 114 | 12 | 60000 | graph compile + optimized callees |
| life | compile-pass random 24x24 | 142.526 | 20.094 | 162.621 | n/a | 162.621 | 2.215 | 71.778 | 20.005 | 0.000 | 0.000 | 0.000 | 114 | 0 | 0 | 0 | 2.378 | 41.148 | 95 | 12 | 60000 | compile-pass setup; graph compile + optimized callees |
| life | compile-pass random 24x24 initial literal | 80.478 | 0.351 | 80.829 | n/a | 80.830 | 0.231 | 79.040 | 73.796 | 0.000 | 0.000 | 0.000 | 73 | 0 | 0 | 0 | 0.015 | 0.233 | 57 | 6 | 60000 | compile-pass setup with initial grid literal; graph compile + optimized callees |
| temperature | NOAA committed slice | n/a | 0.950 | 0.950 | 78.20 x | 0.962 | 0.048 | 0.196 | 0.015 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.012 | 0.136 | 38 | 3 | 30000 | graph compile + optimized callees |
| temperature | synthetic 32x64 | n/a | 0.500 | 0.500 | 89.03 x | 0.505 | 0.019 | 0.185 | 0.020 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.007 | 0.085 | 38 | 3 | 30000 | graph compile + optimized callees |
| sliding puzzle | 2x2 pure source full frontier step | n/a | 3.192 | 3.192 | n/a | 3.193 | 0.124 | 1.798 | 1.073 | 0.000 | 0.000 | 0.000 | 65 | 0 | 0 | 0 | 0.006 | 0.092 | 38 | 3 | 30000 | graph compile + optimized callees |
| sliding puzzle | 2x2 pure compile-pass full frontier step | 11.321 | 0.372 | 11.693 | n/a | 11.694 | 0.233 | 9.880 | 2.978 | 0.000 | 0.000 | 0.000 | 66 | 0 | 0 | 0 | 0.005 | 0.099 | 57 | 3 | 60000 | compile-pass setup; graph compile + optimized callees |
| sliding puzzle | 3x3 pure source depth-8 step | n/a | 44.545 | 44.545 | n/a | 44.545 | 0.126 | 42.732 | 40.538 | 0.000 | 0.000 | 0.000 | 176 | 0 | 0 | 0 | 0.006 | 0.089 | 38 | 3 | 30000 | graph compile + optimized callees |
| sliding puzzle | 3x3 pure compile-pass depth-8 step | 120.354 | 0.218 | 120.572 | n/a | 120.572 | 0.358 | 118.753 | 96.861 | 0.000 | 0.000 | 0.000 | 177 | 0 | 0 | 0 | 0.003 | 0.047 | 57 | 3 | 60000 | compile-pass setup; graph compile + optimized callees |
| sliding puzzle | 4x4 pure source depth-5 step | n/a | 61.056 | 61.056 | n/a | 61.057 | 0.164 | 57.674 | 48.430 | 0.000 | 0.000 | 0.000 | 341 | 0 | 0 | 0 | 0.007 | 0.088 | 38 | 3 | 30000 | graph compile + optimized callees |
| n-queens | MORKL 8x8 source | n/a | 350.722 | 350.722 | n/a | 350.722 | 0.121 | 349.653 | 345.807 | 0.000 | 0.000 | 0.000 | 130 | 0 | 0 | 0 | 0.003 | 0.054 | 38 | 3 | 30000 | graph compile + optimized callees |
| n-queens | MORKL 8x8 compile-pass | 395.678 | 0.320 | 395.998 | n/a | 395.999 | 0.343 | 394.855 | 386.210 | 0.000 | 0.000 | 0.000 | 131 | 0 | 0 | 0 | 0.002 | 0.061 | 57 | 3 | 60000 | compile-pass setup; graph compile + optimized callees |

| benchmark | variant | result | ms | note |
|---|---:|---:|---:|---|
| n-queens bit reference | n=8 | 92 | 0.081 | independent reference, not MORKL eval |
| n-queens bit reference | n=9 | 352 | 0.025 | independent reference, not MORKL eval |
| n-queens bit reference | n=10 | 724 | 0.179 | independent reference, not MORKL eval |
| n-queens bit reference | n=11 | 2680 | 0.761 | independent reference, not MORKL eval |
| n-queens bit reference | n=12 | 14200 | 4.039 | independent reference, not MORKL eval |

## Interpretation

The trie evaluator remains strongest on native path algebra with shared prefixes, joins, restriction, unwrap, and first-symbol iteration. The direct `execT` backend compounds that advantage when the graph is lowered to native ops and optimized callee graphs because it avoids rebuilding old `PathValue` and `SpaceValue` intermediates.

`evalZ` is included as a correctness-backed zipper traversal prototype, not yet as the winning runtime for every source shape. Memoized virtual zippers now keep the sliding-puzzle source rows near `evalTrie`, and top-level union-saturating self recursion is rejected as unsupported rather than falling back to concrete `evalTrie` materialization. The separate `ZIPPER_LARGE_BENCHMARKS.md` report contains larger asymptotic product-selector rows where zipper traversal avoids broad intermediate scans. Direct source n-queens remains omitted for `evalZ` with an explicit note because that high-level search tree still needs a dedicated recursive zipper strategy.

The pure Game-of-Life source is now intentionally a lowering benchmark: direct `evalTrie` still interprets the high-level relation program, while the graph backend lowers `Range`, `Unwrap`, joins, and literal coordinate relations into direct operations; use the `ROG execT` and compile+`ROG execT` columns for the lowered result.

Graph timings are intentionally conservative about semantics. Raw routine calls are not timed as raw helper graphs; helper routines are inlined/expanded and lowered before graph execution when possible, and surviving nonrecursive calls use optimized callee graphs. The fixpoint lowering handles the `Routines.fixpoint` union-saturating shape, while recursive residual call cycles still need dedicated lowering before `exec`/`execT` can be reported honestly.

Compilation is now explicitly bounded by wall-clock budgets as well as structural caps. Source normalization records every pass attempt, graph optimization records `hoist_loop_invariant_subgraphs`, `push_out`, and `optimize_sharing` per round, constant folding records eval/evalTrie/evalZ/execT time and call counts, and the compilation table separates setup SC time, lowering/inlining time, source-pass time, graph-build time, graph-optimization time, compile/run ratio, and compile+run time so runtime speedups are not mistaken for free compilation.

`budget ms sum` is the sum of the independent compile invocation budgets used for a row, including optimized callee-graph compiles when present; it is not elapsed time and not a single shared deadline across all rows.

Rows marked `compiled away; use compile+run` have been fully residualized to a one-node literal by the compile pipeline before execution. Their executor timings are still shown as an implementation sanity check, but their graph speedup factors are intentionally reported as `n/a`; the meaningful number is the compile+`ROG execT` total in the compilation table.

The independent bit-recursive n-queens rows are scaling references, not MORKL runtime rows. They record the known targets through 12x12 while the pure MORKL path-algebra program is executed exactly at 8x8 and compiled/residualized for graph execution.
