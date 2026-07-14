# Trie Runtime Benchmarks

Measured with `TrieBenchmarks` on this worktree. Runtime times are average milliseconds per evaluation after two warmup runs. The first table excludes compilation/supercompilation and graph construction; rows labelled `process-sc` or `compile-pass` time the residual/compiled runtime. Runtime speedup cells and compile/run ratios are deliberately omitted for graphs that have been completely residualized to a one-node literal; use the compile+run column in the second table for those rows. The second table reports setup supercompilation time, graph compilation time, total compile time, compile/run ratio for non-residual graph execution, compile+`ROG execT` time, timed compile-stage totals, constant-folding eval time, and optimization-pass totals under explicit compile budgets. Graph optimization includes loop-invariant subgraph hoisting, push-out, and sharing. `ROG exec` is the legacy `RecursiveOpGraph` executor over `SpaceValue`; `evalZ` is the declarative zipper traversal evaluator that composes logical trie cursors and materializes only at the result boundary; `ROG execT` is the interned trie executor over `TrieSpace` and `List[Int]` path slots. Graph rows use fully optimized graphs: helper functions are inlined/expanded/lowered where possible, any surviving nonrecursive `Call`s dispatch to optimized callee graphs, and graph-note cells list retained calls found in both the top graph and optimized callee graphs. Recursive source `Call` cycles that survive lowering are marked unsupported.

## Runtime

| benchmark | variant | result paths | eval Set ms | ROG exec ms | evalTrie ms | evalZ ms | ROG execT ms | evalTrie / eval | evalZ / eval | evalZ / evalTrie | ROG exec / eval | ROG execT / eval | ROG execT / evalTrie | graph nodes | graph note |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| aunt | reference | 3 | 0.183 | 0.203 | 0.299 | 0.968 | 0.200 | 0.61 x | 0.19 x | 0.31 x | 0.90 x | 0.91 x | 1.50 x | 21 |  |
| aunt | process-sc static family | 3 | 0.030 | 0.124 | 0.246 | 0.806 | 0.175 | 0.12 x | 0.04 x | 0.31 x | 0.24 x | 0.17 x | 1.41 x | 20 |  |
| aunt synthetic | reference 60 people | 36 | 3.423 | 0.985 | 1.180 | 3.335 | 0.655 | 2.90 x | 1.03 x | 0.35 x | 3.47 x | 5.23 x | 1.80 x | 21 |  |
| aunt royal92 | reference royal92_simple.metta fallback | 3 | 0.040 | 0.050 | 0.051 | 0.343 | 0.051 | 0.79 x | 0.12 x | 0.15 x | 0.80 x | 0.79 x | 0.99 x | 21 |  |
| aunt synthetic | process-sc static family 24 people | 12 | 0.142 | 0.203 | 1.599 | 1.348 | 0.158 | 0.09 x | 0.11 x | 1.19 x | 0.70 x | 0.90 x | 10.11 x | 20 |  |
| graph two-hop | reference 90-chain | 264 | 4.162 | 1.265 | 1.692 | 11.343 | 0.254 | 2.46 x | 0.37 x | 0.15 x | 3.29 x | 16.37 x | 6.65 x | 15 |  |
| graph mutual | reference 90-chain | 0 | 5.823 | 1.145 | 0.489 | 1.055 | 0.162 | 11.90 x | 5.52 x | 0.46 x | 5.09 x | 35.88 x | 3.02 x | 15 |  |
| datalog semi-naive | reference 24-chain | 300 | 295.844 | 0.001 | 5.542 | n/a | 0.002 | 53.38 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ rejects the recursive top-level self-union; use evalTrie/graph rows |
| datalog semi-naive | process-sc 24-chain | 300 | 272.584 | 20.800 | 9.060 | n/a | 1.637 | 30.09 x | n/a | n/a | 13.10 x | 166.48 x | 5.53 x | 6 | exec fallback Call dispatch to optimized callee graph(s): step398572781_sc1; direct evalZ rejects the residual recursive top-level self-union; use evalTrie/graph rows |
| life | reference random 24x24 | 125 | 34.139 | 18.099 | 459.205 | 504.852 | 1.997 | 0.07 x | 0.07 x | 0.91 x | 1.89 x | 17.09 x | 229.95 x | 2,667 | exec fallback Call dispatch to optimized callee graph(s): neigh |
| life | compile-pass random 24x24 | 125 | 30.007 | 0.001 | 668.873 | 668.551 | 0.001 | 0.04 x | 0.04 x | 1.00 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| life | compile-pass random 24x24 initial literal | 125 | 0.000 | 0.001 | 0.032 | 0.034 | 0.001 | 0.01 x | 0.01 x | 0.96 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| temperature | NOAA committed slice | 430 | 0.084 | 0.078 | 0.015 | 0.032 | 0.012 | 5.68 x | 2.65 x | 0.47 x | 1.08 x | 6.87 x | 1.21 x | 3 |  |
| temperature | synthetic 32x64 | 448 | 1.136 | 1.141 | 0.020 | 0.041 | 0.015 | 57.91 x | 27.70 x | 0.48 x | 1.00 x | 73.72 x | 1.27 x | 3 |  |
| sliding puzzle | 2x2 pure source full frontier step | 12 | 4.500 | 0.001 | 4.060 | 6.821 | 0.001 | 1.11 x | 0.66 x | 0.60 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| sliding puzzle | 2x2 pure compile-pass full frontier step | 12 | 0.001 | 0.001 | 0.004 | 0.003 | 0.001 | 0.14 x | 0.18 x | 1.23 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| sliding puzzle | 3x3 pure source depth-8 step | 420 | 290.940 | 0.001 | 355.571 | n/a | 0.001 | 0.82 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ source search tree exceeds the benchmark heap; use evalTrie/graph rows |
| sliding puzzle | 3x3 pure compile-pass depth-8 step | 420 | 0.000 | 0.001 | 0.225 | 0.184 | 0.001 | 0.00 x | 0.00 x | 1.22 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| sliding puzzle | 4x4 pure source depth-5 step | 202 | 493.117 | 0.001 | 625.555 | n/a | 0.001 | 0.79 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ source search tree exceeds the benchmark heap; use evalTrie/graph rows |
| n-queens | MORKL 8x8 source | 92 | 458.553 | 0.001 | 1193.423 | n/a | 0.001 | 0.38 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ is currently omitted for the high-level source search tree; use compile-pass/graph rows |
| n-queens | MORKL 8x8 compile-pass | 92 | 0.001 | 0.001 | 0.031 | 0.031 | 0.001 | 0.02 x | 0.02 x | 0.98 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |

## Compilation And Optimization

| benchmark | variant | prep compile ms | graph compile ms | total compile ms | compile/run | compile+ROG execT ms | lower+inline ms | source pass ms | const-fold eval ms | const-fold evalTrie ms | const-fold evalZ ms | const-fold execT ms | const-fold eval calls | const-fold evalTrie calls | const-fold evalZ calls | const-fold execT calls | graph transpile ms | graph optimize ms | source pass attempts | graph pass steps | budget ms sum | note |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| aunt | reference | n/a | 4.322 | 4.322 | 21.64 x | 4.522 | 0.208 | 0.825 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.136 | 1.228 | 38 | 6 | 30000 | graph compile + optimized callees |
| aunt | process-sc static family | 34.427 | 3.843 | 38.270 | 219.20 x | 38.445 | 1.370 | 0.540 | 0.006 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.143 | 1.361 | 19 | 6 | 60000 | process SC setup; graph compile + optimized callees |
| aunt synthetic | reference 60 people | n/a | 1.760 | 1.760 | 2.69 x | 2.415 | 0.063 | 0.607 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.068 | 0.787 | 38 | 6 | 30000 | graph compile + optimized callees |
| aunt royal92 | reference royal92_simple.metta fallback | n/a | 1.652 | 1.652 | 32.11 x | 1.704 | 0.050 | 0.491 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.077 | 0.750 | 38 | 6 | 30000 | graph compile + optimized callees |
| aunt synthetic | process-sc static family 24 people | 6.310 | 1.311 | 7.621 | 48.19 x | 7.779 | 0.087 | 0.400 | 0.004 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.033 | 0.618 | 19 | 6 | 60000 | process SC setup; graph compile + optimized callees |
| graph two-hop | reference 90-chain | n/a | 1.375 | 1.375 | 5.41 x | 1.629 | 0.018 | 0.171 | 0.028 | 0.000 | 0.000 | 0.000 | 2 | 0 | 0 | 0 | 0.030 | 0.828 | 57 | 9 | 30000 | graph compile + optimized callees |
| graph mutual | reference 90-chain | n/a | 1.160 | 1.160 | 7.15 x | 1.322 | 0.019 | 0.100 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.030 | 0.747 | 38 | 9 | 30000 | graph compile + optimized callees |
| datalog semi-naive | reference 24-chain | n/a | 12.616 | 12.616 | n/a | 12.618 | 0.363 | 11.818 | 0.168 | 10.462 | 0.000 | 0.000 | 9 | 1 | 0 | 0 | 0.003 | 0.073 | 38 | 3 | 30000 | graph compile + optimized callees |
| datalog semi-naive | process-sc 24-chain | 50.600 | 9.936 | 60.536 | 36.97 x | 62.173 | 0.181 | 1.823 | 0.005 | 0.000 | 0.000 | 0.000 | 10 | 0 | 0 | 0 | 0.282 | 5.542 | 38 | 12 | 90000 | process SC setup; graph compile + optimized callees |
| life | reference random 24x24 | n/a | 2870.412 | 2870.412 | 1437.36 x | 2872.409 | 0.541 | 2549.525 | 0.435 | 0.000 | 0.000 | 0.000 | 1384 | 0 | 0 | 0 | 3.375 | 313.220 | 114 | 12 | 60000 | graph compile + optimized callees |
| life | compile-pass random 24x24 | 84.477 | 19.200 | 103.677 | n/a | 103.678 | 1.160 | 55.436 | 21.005 | 0.000 | 0.000 | 0.000 | 114 | 0 | 0 | 0 | 1.159 | 30.655 | 95 | 12 | 60000 | compile-pass setup; graph compile + optimized callees |
| life | compile-pass random 24x24 initial literal | 76.103 | 0.232 | 76.335 | n/a | 76.336 | 0.189 | 75.158 | 69.086 | 0.000 | 0.000 | 0.000 | 73 | 0 | 0 | 0 | 0.009 | 0.200 | 57 | 6 | 60000 | compile-pass setup with initial grid literal; graph compile + optimized callees |
| temperature | NOAA committed slice | n/a | 0.418 | 0.418 | 33.98 x | 0.430 | 0.026 | 0.096 | 0.014 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.004 | 0.052 | 38 | 3 | 30000 | graph compile + optimized callees |
| temperature | synthetic 32x64 | n/a | 0.329 | 0.329 | 21.32 x | 0.344 | 0.009 | 0.074 | 0.007 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.026 | 0.075 | 38 | 3 | 30000 | graph compile + optimized callees |
| sliding puzzle | 2x2 pure source full frontier step | n/a | 2.228 | 2.228 | n/a | 2.228 | 0.110 | 1.719 | 0.950 | 0.000 | 0.000 | 0.000 | 65 | 0 | 0 | 0 | 0.002 | 0.046 | 38 | 3 | 30000 | graph compile + optimized callees |
| sliding puzzle | 2x2 pure compile-pass full frontier step | 13.588 | 0.231 | 13.819 | n/a | 13.820 | 0.157 | 12.277 | 6.129 | 0.000 | 0.000 | 0.000 | 66 | 0 | 0 | 0 | 0.001 | 0.053 | 57 | 3 | 60000 | compile-pass setup; graph compile + optimized callees |
| sliding puzzle | 3x3 pure source depth-8 step | n/a | 34.614 | 34.614 | n/a | 34.615 | 0.144 | 33.997 | 31.877 | 0.000 | 0.000 | 0.000 | 176 | 0 | 0 | 0 | 0.002 | 0.051 | 38 | 3 | 30000 | graph compile + optimized callees |
| sliding puzzle | 3x3 pure compile-pass depth-8 step | 101.434 | 0.213 | 101.647 | n/a | 101.648 | 0.306 | 100.887 | 81.203 | 0.000 | 0.000 | 0.000 | 177 | 0 | 0 | 0 | 0.002 | 0.050 | 57 | 3 | 60000 | compile-pass setup; graph compile + optimized callees |
| sliding puzzle | 4x4 pure source depth-5 step | n/a | 55.267 | 55.267 | n/a | 55.267 | 0.150 | 54.379 | 45.187 | 0.000 | 0.000 | 0.000 | 341 | 0 | 0 | 0 | 0.002 | 0.050 | 38 | 3 | 30000 | graph compile + optimized callees |
| n-queens | MORKL 8x8 source | n/a | 321.468 | 321.468 | n/a | 321.468 | 0.139 | 320.707 | 317.152 | 0.000 | 0.000 | 0.000 | 130 | 0 | 0 | 0 | 0.002 | 0.052 | 38 | 3 | 30000 | graph compile + optimized callees |
| n-queens | MORKL 8x8 compile-pass | 369.250 | 0.263 | 369.513 | n/a | 369.514 | 0.848 | 368.188 | 360.927 | 0.000 | 0.000 | 0.000 | 131 | 0 | 0 | 0 | 0.001 | 0.052 | 57 | 3 | 60000 | compile-pass setup; graph compile + optimized callees |

| benchmark | variant | result | ms | note |
|---|---:|---:|---:|---|
| n-queens bit reference | n=8 | 92 | 0.009 | independent reference, not MORKL eval |
| n-queens bit reference | n=9 | 352 | 0.032 | independent reference, not MORKL eval |
| n-queens bit reference | n=10 | 724 | 0.174 | independent reference, not MORKL eval |
| n-queens bit reference | n=11 | 2680 | 0.676 | independent reference, not MORKL eval |
| n-queens bit reference | n=12 | 14200 | 4.269 | independent reference, not MORKL eval |

## Interpretation

The trie evaluator remains strongest on native path algebra with shared prefixes, joins, restriction, unwrap, and first-symbol iteration. The direct `execT` backend compounds that advantage when the graph is lowered to native ops and optimized callee graphs because it avoids rebuilding old `PathValue` and `SpaceValue` intermediates.

`evalZ` is included as a correctness-backed zipper traversal prototype, not yet as the winning runtime for every source shape. Memoized virtual zippers now keep the sliding-puzzle source rows near `evalTrie`, and top-level union-saturating self recursion is rejected as unsupported rather than falling back to concrete `evalTrie` materialization. The separate `ZIPPER_LARGE_BENCHMARKS.md` report contains larger asymptotic product-selector rows where zipper traversal avoids broad intermediate scans. Direct source n-queens remains omitted for `evalZ` with an explicit note because that high-level search tree still needs a dedicated recursive zipper strategy.

The pure Game-of-Life source is now intentionally a lowering benchmark: direct `evalTrie` still interprets the high-level relation program, while the graph backend lowers `Range`, `Unwrap`, joins, and literal coordinate relations into direct operations; use the `ROG execT` and compile+`ROG execT` columns for the lowered result.

Graph timings are intentionally conservative about semantics. Raw routine calls are not timed as raw helper graphs; helper routines are inlined/expanded and lowered before graph execution when possible, and surviving nonrecursive calls use optimized callee graphs. The fixpoint lowering handles the `Routines.fixpoint` union-saturating shape, while recursive residual call cycles still need dedicated lowering before `exec`/`execT` can be reported honestly.

Compilation is now explicitly bounded by wall-clock budgets as well as structural caps. Source normalization records every pass attempt, graph optimization records `hoist_loop_invariant_subgraphs`, `push_out`, and `optimize_sharing` per round, constant folding records eval/evalTrie/evalZ/execT time and call counts, and the compilation table separates setup SC time, lowering/inlining time, source-pass time, graph-build time, graph-optimization time, compile/run ratio, and compile+run time so runtime speedups are not mistaken for free compilation.

`budget ms sum` is the sum of the independent compile invocation budgets used for a row, including optimized callee-graph compiles when present; it is not elapsed time and not a single shared deadline across all rows.

Rows marked `compiled away; use compile+run` have been fully residualized to a one-node literal by the compile pipeline before execution. Their executor timings are still shown as an implementation sanity check, but their graph speedup factors are intentionally reported as `n/a`; the meaningful number is the compile+`ROG execT` total in the compilation table.

The independent bit-recursive n-queens rows are scaling references, not MORKL runtime rows. They record the known targets through 12x12 while the pure MORKL path-algebra program is executed exactly at 8x8 and compiled/residualized for graph execution.
