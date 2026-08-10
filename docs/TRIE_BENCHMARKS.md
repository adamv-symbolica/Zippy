# Trie Runtime Benchmarks

Measured with `TrieBenchmarks` on this worktree. Runtime times are average milliseconds per evaluation after two warmup runs. The first table excludes compilation/supercompilation and graph construction; rows labelled `process-sc` or `compile-pass` time the residual/compiled runtime. Runtime speedup cells and compile/run ratios are deliberately omitted for graphs that have been completely residualized to a one-node literal; use the compile+run column in the second table for those rows. The second table reports setup supercompilation time, graph compilation time, total compile time, compile/run ratio for non-residual graph execution, compile+`ROG execT` time, timed compile-stage totals, constant-folding eval time, and optimization-pass totals under explicit compile budgets. Graph optimization includes loop-invariant subgraph hoisting, push-out, and sharing. `ROG exec` is the legacy `RecursiveOpGraph` executor over `SpaceValue`; `evalZ` is the declarative zipper traversal evaluator that composes logical trie cursors and materializes only at the result boundary; `ROG execT` is the interned trie executor over `TrieSpace` and `List[Int]` path slots. Graph rows use fully optimized graphs: helper functions are inlined/expanded/lowered where possible, any surviving nonrecursive `Call`s dispatch to optimized callee graphs, and graph-note cells list retained calls found in both the top graph and optimized callee graphs. Recursive source `Call` cycles that survive lowering are marked unsupported.

## Runtime

| benchmark | variant | result paths | eval Set ms | ROG exec ms | evalTrie ms | evalZ ms | ROG execT ms | evalTrie / eval | evalZ / eval | evalZ / evalTrie | ROG exec / eval | ROG execT / eval | ROG execT / evalTrie | graph nodes | graph note |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| aunt | reference | 3 | 0.085 | 0.208 | 0.408 | 0.544 | 0.385 | 0.21 x | 0.16 x | 0.75 x | 0.41 x | 0.22 x | 1.06 x | 21 |  |
| aunt | process-sc static family | 3 | 0.057 | 0.134 | 0.243 | 0.441 | 0.156 | 0.23 x | 0.13 x | 0.55 x | 0.42 x | 0.36 x | 1.56 x | 20 |  |
| aunt synthetic | reference 60 people | 36 | 2.956 | 0.882 | 1.225 | 3.078 | 1.151 | 2.41 x | 0.96 x | 0.40 x | 3.35 x | 2.57 x | 1.06 x | 21 |  |
| aunt royal92 | reference royal92_simple.metta fallback | 3 | 0.036 | 0.038 | 0.061 | 0.116 | 0.080 | 0.58 x | 0.31 x | 0.53 x | 0.93 x | 0.44 x | 0.76 x | 21 |  |
| aunt synthetic | process-sc static family 24 people | 12 | 0.128 | 0.180 | 1.561 | 2.123 | 0.222 | 0.08 x | 0.06 x | 0.74 x | 0.71 x | 0.57 x | 7.02 x | 20 |  |
| graph two-hop | reference 90-chain | 264 | 4.351 | 1.816 | 0.537 | 11.200 | 0.533 | 8.11 x | 0.39 x | 0.05 x | 2.40 x | 8.16 x | 1.01 x | 15 |  |
| graph mutual | reference 90-chain | 0 | 3.193 | 0.919 | 0.270 | 1.839 | 0.204 | 11.84 x | 1.74 x | 0.15 x | 3.48 x | 15.62 x | 1.32 x | 15 |  |
| datalog semi-naive | reference 24-chain | 300 | 261.449 | 0.004 | 6.850 | n/a | 0.002 | 38.17 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ rejects the recursive top-level self-union; use evalTrie/graph rows |
| datalog semi-naive | process-sc 24-chain | 300 | 242.081 | 20.039 | 10.461 | n/a | 3.545 | 23.14 x | n/a | n/a | 12.08 x | 68.28 x | 2.95 x | 6 | exec fallback Call dispatch to optimized callee graph(s): step352598575_sc1; direct evalZ rejects the residual recursive top-level self-union; use evalTrie/graph rows |
| life | reference random 24x24 | 125 | 31.459 | 25.574 | 271.974 | 298.541 | 4.490 | 0.12 x | 0.11 x | 0.91 x | 1.23 x | 7.01 x | 60.57 x | 2,667 | exec fallback Call dispatch to optimized callee graph(s): neigh |
| life | compile-pass random 24x24 | 125 | 29.042 | 0.001 | 196.815 | 231.863 | 0.001 | 0.15 x | 0.13 x | 0.85 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| life | compile-pass random 24x24 initial literal | 125 | 0.000 | 0.001 | 0.031 | 0.027 | 0.001 | 0.01 x | 0.01 x | 1.13 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| temperature | NOAA committed slice | 430 | 0.073 | 0.086 | 0.010 | 0.031 | 0.008 | 7.71 x | 2.35 x | 0.30 x | 0.86 x | 9.15 x | 1.19 x | 3 |  |
| temperature | synthetic 32x64 | 448 | 1.137 | 1.136 | 0.014 | 0.055 | 0.009 | 79.23 x | 20.67 x | 0.26 x | 1.00 x | 124.19 x | 1.57 x | 3 |  |
| sliding puzzle | 2x2 pure source full frontier step | 12 | 4.602 | 0.001 | 6.299 | 8.222 | 0.001 | 0.73 x | 0.56 x | 0.77 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| sliding puzzle | 2x2 pure compile-pass full frontier step | 12 | 0.000 | 0.001 | 0.005 | 0.006 | 0.001 | 0.09 x | 0.08 x | 0.86 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| sliding puzzle | 3x3 pure source depth-8 step | 420 | 299.201 | 0.001 | 506.053 | n/a | 0.001 | 0.59 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ source search tree exceeds the benchmark heap; use evalTrie/graph rows |
| sliding puzzle | 3x3 pure compile-pass depth-8 step | 420 | 0.000 | 0.001 | 0.228 | 0.228 | 0.001 | 0.00 x | 0.00 x | 1.00 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| sliding puzzle | 4x4 pure source depth-5 step | 202 | 493.721 | 0.001 | 835.644 | n/a | 0.001 | 0.59 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ source search tree exceeds the benchmark heap; use evalTrie/graph rows |
| n-queens | MORKL 8x8 source | 92 | 489.392 | 0.001 | 1682.937 | n/a | 0.001 | 0.29 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ is currently omitted for the high-level source search tree; use compile-pass/graph rows |
| n-queens | MORKL 8x8 compile-pass | 92 | 0.000 | 0.001 | 0.043 | 0.042 | 0.001 | 0.01 x | 0.01 x | 1.01 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |

## Compilation And Optimization

| benchmark | variant | prep compile ms | graph compile ms | total compile ms | compile/run | compile+ROG execT ms | lower+inline ms | source pass ms | const-fold eval ms | const-fold evalTrie ms | const-fold evalZ ms | const-fold execT ms | const-fold eval calls | const-fold evalTrie calls | const-fold evalZ calls | const-fold execT calls | graph transpile ms | graph optimize ms | source pass attempts | graph pass steps | budget ms sum | note |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| aunt | reference | n/a | 2.565 | 2.565 | 6.66 x | 2.951 | 0.075 | 0.433 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.115 | 0.965 | 38 | 6 | 30000 | graph compile + optimized callees |
| aunt | process-sc static family | 32.647 | 3.073 | 35.720 | 229.61 x | 35.875 | 1.419 | 0.638 | 0.005 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.061 | 0.732 | 19 | 6 | 60000 | process SC setup; graph compile + optimized callees |
| aunt synthetic | reference 60 people | n/a | 1.605 | 1.605 | 1.39 x | 2.756 | 0.035 | 0.361 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.088 | 0.786 | 38 | 6 | 30000 | graph compile + optimized callees |
| aunt royal92 | reference royal92_simple.metta fallback | n/a | 1.348 | 1.348 | 16.78 x | 1.429 | 0.039 | 0.295 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.084 | 0.730 | 38 | 6 | 30000 | graph compile + optimized callees |
| aunt synthetic | process-sc static family 24 people | 6.266 | 1.250 | 7.516 | 33.79 x | 7.739 | 0.084 | 0.320 | 0.004 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.042 | 0.641 | 19 | 6 | 60000 | process SC setup; graph compile + optimized callees |
| graph two-hop | reference 90-chain | n/a | 1.427 | 1.427 | 2.68 x | 1.961 | 0.019 | 0.188 | 0.020 | 0.000 | 0.000 | 0.000 | 2 | 0 | 0 | 0 | 0.039 | 0.843 | 57 | 9 | 30000 | graph compile + optimized callees |
| graph mutual | reference 90-chain | n/a | 1.368 | 1.368 | 6.69 x | 1.572 | 0.017 | 0.106 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.049 | 0.890 | 38 | 9 | 30000 | graph compile + optimized callees |
| datalog semi-naive | reference 24-chain | n/a | 30.279 | 30.279 | n/a | 30.281 | 0.312 | 27.920 | 0.642 | 24.479 | 0.000 | 0.000 | 9 | 1 | 0 | 0 | 0.008 | 0.161 | 38 | 3 | 30000 | graph compile + optimized callees |
| datalog semi-naive | process-sc 24-chain | 57.049 | 9.518 | 66.568 | 18.78 x | 70.113 | 0.132 | 1.617 | 0.002 | 0.000 | 0.000 | 0.000 | 10 | 0 | 0 | 0 | 0.369 | 5.616 | 38 | 12 | 90000 | process SC setup; graph compile + optimized callees |
| life | reference random 24x24 | n/a | 3219.167 | 3219.167 | 716.95 x | 3223.657 | 0.651 | 2924.587 | 0.271 | 0.000 | 0.000 | 0.000 | 1384 | 0 | 0 | 0 | 2.422 | 288.464 | 114 | 12 | 60000 | graph compile + optimized callees |
| life | compile-pass random 24x24 | 74.342 | 19.422 | 93.764 | n/a | 93.765 | 0.977 | 57.049 | 19.292 | 0.000 | 0.000 | 0.000 | 114 | 0 | 0 | 0 | 1.057 | 24.824 | 95 | 12 | 60000 | compile-pass setup; graph compile + optimized callees |
| life | compile-pass random 24x24 initial literal | 69.621 | 0.200 | 69.821 | n/a | 69.822 | 0.120 | 67.945 | 64.326 | 0.000 | 0.000 | 0.000 | 73 | 0 | 0 | 0 | 0.014 | 0.203 | 57 | 6 | 60000 | compile-pass setup with initial grid literal; graph compile + optimized callees |
| temperature | NOAA committed slice | n/a | 0.397 | 0.397 | 49.38 x | 0.405 | 0.035 | 0.083 | 0.004 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.005 | 0.065 | 38 | 3 | 30000 | graph compile + optimized callees |
| temperature | synthetic 32x64 | n/a | 0.302 | 0.302 | 33.04 x | 0.312 | 0.010 | 0.111 | 0.009 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.005 | 0.068 | 38 | 3 | 30000 | graph compile + optimized callees |
| sliding puzzle | 2x2 pure source full frontier step | n/a | 2.585 | 2.585 | n/a | 2.585 | 0.133 | 1.806 | 0.932 | 0.000 | 0.000 | 0.000 | 65 | 0 | 0 | 0 | 0.003 | 0.055 | 38 | 3 | 30000 | graph compile + optimized callees |
| sliding puzzle | 2x2 pure compile-pass full frontier step | 13.629 | 0.170 | 13.799 | n/a | 13.800 | 0.373 | 12.250 | 5.516 | 0.000 | 0.000 | 0.000 | 66 | 0 | 0 | 0 | 0.002 | 0.040 | 57 | 3 | 60000 | compile-pass setup; graph compile + optimized callees |
| sliding puzzle | 3x3 pure source depth-8 step | n/a | 40.654 | 40.654 | n/a | 40.654 | 0.117 | 39.230 | 32.932 | 0.000 | 0.000 | 0.000 | 176 | 0 | 0 | 0 | 0.003 | 0.058 | 38 | 3 | 30000 | graph compile + optimized callees |
| sliding puzzle | 3x3 pure compile-pass depth-8 step | 133.542 | 0.245 | 133.787 | n/a | 133.788 | 0.718 | 130.491 | 101.323 | 0.000 | 0.000 | 0.000 | 177 | 0 | 0 | 0 | 0.003 | 0.062 | 57 | 3 | 60000 | compile-pass setup; graph compile + optimized callees |
| sliding puzzle | 4x4 pure source depth-5 step | n/a | 61.480 | 61.480 | n/a | 61.481 | 0.157 | 59.274 | 44.069 | 0.000 | 0.000 | 0.000 | 341 | 0 | 0 | 0 | 0.003 | 0.063 | 38 | 3 | 30000 | graph compile + optimized callees |
| n-queens | MORKL 8x8 source | n/a | 326.349 | 326.349 | n/a | 326.349 | 0.134 | 325.160 | 321.410 | 0.000 | 0.000 | 0.000 | 130 | 0 | 0 | 0 | 0.003 | 0.064 | 38 | 3 | 30000 | graph compile + optimized callees |
| n-queens | MORKL 8x8 compile-pass | 408.572 | 0.306 | 408.878 | n/a | 408.879 | 0.642 | 407.309 | 399.969 | 0.000 | 0.000 | 0.000 | 131 | 0 | 0 | 0 | 0.002 | 0.075 | 57 | 3 | 60000 | compile-pass setup; graph compile + optimized callees |

| benchmark | variant | result | ms | note |
|---|---:|---:|---:|---|
| n-queens bit reference | n=8 | 92 | 0.043 | independent reference, not MORKL eval |
| n-queens bit reference | n=9 | 352 | 0.032 | independent reference, not MORKL eval |
| n-queens bit reference | n=10 | 724 | 0.185 | independent reference, not MORKL eval |
| n-queens bit reference | n=11 | 2680 | 0.728 | independent reference, not MORKL eval |
| n-queens bit reference | n=12 | 14200 | 4.024 | independent reference, not MORKL eval |

## Interpretation

The trie evaluator remains strongest on native path algebra with shared prefixes, joins, restriction, unwrap, and first-symbol iteration. The direct `execT` backend compounds that advantage when the graph is lowered to native ops and optimized callee graphs because it avoids rebuilding old `PathValue` and `SpaceValue` intermediates.

`evalZ` is included as a correctness-backed zipper traversal prototype, not yet as the winning runtime for every source shape. Memoized virtual zippers now keep the sliding-puzzle source rows near `evalTrie`, and top-level union-saturating self recursion is rejected as unsupported rather than falling back to concrete `evalTrie` materialization. The separate `ZIPPER_LARGE_BENCHMARKS.md` report contains larger asymptotic product-selector rows where zipper traversal avoids broad intermediate scans. Direct source n-queens remains omitted for `evalZ` with an explicit note because that high-level search tree still needs a dedicated recursive zipper strategy.

The pure Game-of-Life source is now intentionally a lowering benchmark: direct `evalTrie` still interprets the high-level relation program, while the graph backend lowers `Range`, `Unwrap`, joins, and literal coordinate relations into direct operations; use the `ROG execT` and compile+`ROG execT` columns for the lowered result.

Graph timings are intentionally conservative about semantics. Raw routine calls are not timed as raw helper graphs; helper routines are inlined/expanded and lowered before graph execution when possible, and surviving nonrecursive calls use optimized callee graphs. The fixpoint lowering handles the `Routines.fixpoint` union-saturating shape, while recursive residual call cycles still need dedicated lowering before `exec`/`execT` can be reported honestly.

Compilation is now explicitly bounded by wall-clock budgets as well as structural caps. Source normalization records every pass attempt, graph optimization records `hoist_loop_invariant_subgraphs`, `push_out`, and `optimize_sharing` per round, constant folding records eval/evalTrie/evalZ/execT time and call counts, and the compilation table separates setup SC time, lowering/inlining time, source-pass time, graph-build time, graph-optimization time, compile/run ratio, and compile+run time so runtime speedups are not mistaken for free compilation.

`budget ms sum` is the sum of the independent compile invocation budgets used for a row, including optimized callee-graph compiles when present; it is not elapsed time and not a single shared deadline across all rows.

Rows marked `compiled away; use compile+run` have been fully residualized to a one-node literal by the compile pipeline before execution. Their executor timings are still shown as an implementation sanity check, but their graph speedup factors are intentionally reported as `n/a`; the meaningful number is the compile+`ROG execT` total in the compilation table.

The independent bit-recursive n-queens rows are scaling references, not MORKL runtime rows. They record the known targets through 12x12 while the pure MORKL path-algebra program is executed exactly at 8x8 and compiled/residualized for graph execution.
