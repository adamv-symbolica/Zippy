# Trie Runtime Benchmarks

Measured with `TrieBenchmarks` on this worktree. Runtime times are average milliseconds per evaluation after two warmup runs. The first table excludes compilation/supercompilation and graph construction; rows labelled `process-sc` or `compile-pass` time the residual/compiled runtime. Runtime speedup cells and compile/run ratios are deliberately omitted for graphs that have been completely residualized to a one-node literal; use the compile+run column in the second table for those rows. The second table reports setup supercompilation time, graph compilation time, total compile time, compile/run ratio for non-residual graph execution, compile+`ROG execT` time, timed compile-stage totals, constant-folding eval time, and optimization-pass totals under explicit compile budgets. Graph optimization includes loop-invariant subgraph hoisting, push-out, and sharing. `ROG exec` is the legacy `RecursiveOpGraph` executor over `SpaceValue`; `evalZ` is the declarative zipper traversal evaluator that composes logical trie cursors and materializes only at the result boundary; `ROG execT` is the interned trie executor over `TrieSpace` and `List[Int]` path slots. Graph rows use fully optimized graphs: helper functions are inlined/expanded/lowered where possible, any surviving nonrecursive `Call`s dispatch to optimized callee graphs, and graph-note cells list retained calls found in both the top graph and optimized callee graphs. Recursive source `Call` cycles that survive lowering are marked unsupported.

## Runtime

| benchmark | variant | result paths | eval Set ms | ROG exec ms | evalTrie ms | evalZ ms | ROG execT ms | evalTrie / eval | evalZ / eval | evalZ / evalTrie | ROG exec / eval | ROG execT / eval | ROG execT / evalTrie | graph nodes | graph note |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| aunt | reference | 3 | 0.494 | 0.303 | 0.717 | 1.361 | 0.423 | 0.69 x | 0.36 x | 0.53 x | 1.63 x | 1.17 x | 1.70 x | 21 |  |
| aunt | process-sc static family | 3 | 0.103 | 0.640 | 1.387 | 1.123 | 1.589 | 0.07 x | 0.09 x | 1.24 x | 0.16 x | 0.06 x | 0.87 x | 20 |  |
| aunt synthetic | reference 60 people | 36 | 12.069 | 3.009 | 3.469 | 8.585 | 3.022 | 3.48 x | 1.41 x | 0.40 x | 4.01 x | 3.99 x | 1.15 x | 21 |  |
| aunt royal92 | reference royal92_simple.metta fallback | 3 | 0.081 | 0.088 | 0.098 | 0.343 | 0.079 | 0.83 x | 0.24 x | 0.29 x | 0.92 x | 1.02 x | 1.23 x | 21 |  |
| aunt synthetic | process-sc static family 24 people | 12 | 0.286 | 0.543 | 2.922 | 4.321 | 0.435 | 0.10 x | 0.07 x | 0.68 x | 0.53 x | 0.66 x | 6.72 x | 20 |  |
| graph two-hop | reference 90-chain | 264 | 5.928 | 3.142 | 1.130 | 20.605 | 1.018 | 5.25 x | 0.29 x | 0.05 x | 1.89 x | 5.83 x | 1.11 x | 15 |  |
| graph mutual | reference 90-chain | 0 | 5.875 | 2.310 | 0.385 | 1.977 | 0.533 | 15.24 x | 2.97 x | 0.19 x | 2.54 x | 11.03 x | 0.72 x | 15 |  |
| scc | direct mutual reachability | 8 | 0.151 | 0.003 | 0.382 | 1.906 | 0.002 | 0.40 x | 0.08 x | 0.20 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| datalog semi-naive | reference 24-chain | 300 | 527.808 | 0.004 | 13.574 | 185.510 | 0.003 | 38.88 x | 2.85 x | 0.07 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| datalog semi-naive | process-sc 24-chain | 300 | 434.644 | 39.489 | 17.899 | 44.787 | 8.025 | 24.28 x | 9.70 x | 0.40 x | 11.01 x | 54.16 x | 2.23 x | 6 | exec fallback Call dispatch to optimized callee graph(s): step352598575_sc1 |
| life | reference random 24x24 | 125 | 37.116 | 23.088 | 383.515 | 495.160 | 6.249 | 0.10 x | 0.07 x | 0.77 x | 1.61 x | 5.94 x | 61.37 x | 2,667 | exec fallback Call dispatch to optimized callee graph(s): neigh |
| life | compile-pass random 24x24 | 125 | 30.927 | 0.002 | 274.184 | 309.459 | 0.001 | 0.11 x | 0.10 x | 0.89 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| life | compile-pass random 24x24 initial literal | 125 | 0.001 | 0.001 | 0.041 | 0.040 | 0.001 | 0.01 x | 0.01 x | 1.03 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| temperature | NOAA committed slice | 12 | 0.003 | 0.003 | 0.014 | 0.024 | 0.017 | 0.25 x | 0.14 x | 0.57 x | 1.07 x | 0.20 x | 0.81 x | 3 |  |
| temperature | synthetic 32x64 | 448 | 1.493 | 1.475 | 0.017 | 0.046 | 0.011 | 86.96 x | 32.37 x | 0.37 x | 1.01 x | 137.63 x | 1.58 x | 3 |  |
| sliding puzzle | 2x2 pure source full frontier step | 12 | 5.982 | 0.001 | 4.438 | 11.747 | 0.001 | 1.35 x | 0.51 x | 0.38 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| sliding puzzle | 2x2 pure compile-pass full frontier step | 12 | 0.001 | 0.001 | 0.007 | 0.007 | 0.001 | 0.10 x | 0.10 x | 1.03 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| sliding puzzle | 3x3 pure source depth-8 step | 420 | 487.038 | 0.003 | 682.629 | n/a | 0.001 | 0.71 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ source search tree exceeds the benchmark heap; use evalTrie/graph rows |
| sliding puzzle | 3x3 pure compile-pass depth-8 step | 420 | 0.002 | 0.001 | 0.366 | 0.350 | 0.001 | 0.00 x | 0.00 x | 1.05 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| sliding puzzle | 4x4 pure source depth-5 step | 202 | 899.359 | 0.021 | 1715.831 | n/a | 0.011 | 0.52 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ source search tree exceeds the benchmark heap; use evalTrie/graph rows |
| n-queens | MORKL 8x8 source | 92 | 777.882 | 0.004 | 2289.130 | n/a | 0.001 | 0.34 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ is currently omitted for the high-level source search tree; use compile-pass/graph rows |
| n-queens | MORKL 8x8 compile-pass | 92 | 0.002 | 0.001 | 0.060 | 0.065 | 0.001 | 0.03 x | 0.03 x | 0.93 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |

## Compilation And Optimization

| benchmark | variant | prep compile ms | graph compile ms | total compile ms | compile/run | compile+ROG execT ms | lower+inline ms | source pass ms | const-fold eval ms | const-fold evalTrie ms | const-fold evalZ ms | const-fold execT ms | const-fold eval calls | const-fold evalTrie calls | const-fold evalZ calls | const-fold execT calls | graph transpile ms | graph optimize ms | source pass attempts | graph pass steps | budget ms sum | note |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| aunt | reference | n/a | 13.715 | 13.715 | 32.45 x | 14.138 | 0.152 | 1.132 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.259 | 2.050 | 38 | 6 | 30000 | graph compile + optimized callees |
| aunt | process-sc static family | 76.755 | 5.823 | 82.577 | 51.95 x | 84.167 | 0.144 | 3.121 | 0.007 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.111 | 1.499 | 19 | 6 | 60000 | process SC setup; graph compile + optimized callees |
| aunt synthetic | reference 60 people | n/a | 10.527 | 10.527 | 3.48 x | 13.549 | 0.074 | 5.522 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.200 | 4.059 | 38 | 6 | 30000 | graph compile + optimized callees |
| aunt royal92 | reference royal92_simple.metta fallback | n/a | 10.041 | 10.041 | 126.79 x | 10.121 | 0.073 | 4.520 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.533 | 4.319 | 38 | 6 | 30000 | graph compile + optimized callees |
| aunt synthetic | process-sc static family 24 people | 9.970 | 6.064 | 16.034 | 36.90 x | 16.469 | 0.198 | 0.783 | 0.004 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.260 | 1.732 | 19 | 6 | 60000 | process SC setup; graph compile + optimized callees |
| graph two-hop | reference 90-chain | n/a | 5.884 | 5.884 | 5.78 x | 6.902 | 0.032 | 0.571 | 0.099 | 0.000 | 0.000 | 0.000 | 2 | 0 | 0 | 0 | 0.074 | 4.040 | 57 | 9 | 30000 | graph compile + optimized callees |
| graph mutual | reference 90-chain | n/a | 3.999 | 3.999 | 7.51 x | 4.532 | 0.031 | 0.173 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.049 | 3.195 | 38 | 9 | 30000 | graph compile + optimized callees |
| scc | direct mutual reachability | n/a | 6.721 | 6.721 | n/a | 6.723 | 0.236 | 3.635 | 0.204 | 1.087 | 0.000 | 0.000 | 5 | 2 | 0 | 0 | 0.010 | 0.208 | 38 | 3 | 30000 | graph compile + optimized callees |
| datalog semi-naive | reference 24-chain | n/a | 19.462 | 19.462 | n/a | 19.464 | 0.199 | 17.764 | 0.237 | 16.396 | 0.000 | 0.000 | 9 | 1 | 0 | 0 | 0.008 | 0.206 | 38 | 3 | 30000 | graph compile + optimized callees |
| datalog semi-naive | process-sc 24-chain | 92.282 | 19.955 | 112.236 | 13.99 x | 120.262 | 0.213 | 3.026 | 0.007 | 0.000 | 0.000 | 0.000 | 10 | 0 | 0 | 0 | 0.312 | 11.643 | 38 | 12 | 90000 | process SC setup; graph compile + optimized callees |
| life | reference random 24x24 | n/a | 4257.191 | 4257.191 | 681.24 x | 4263.440 | 0.336 | 3906.037 | 0.399 | 0.000 | 0.000 | 0.000 | 1384 | 0 | 0 | 0 | 2.318 | 344.211 | 114 | 12 | 60000 | graph compile + optimized callees |
| life | compile-pass random 24x24 | 132.432 | 20.781 | 153.214 | n/a | 153.214 | 1.664 | 83.102 | 20.633 | 0.000 | 0.000 | 0.000 | 114 | 0 | 0 | 0 | 2.067 | 48.848 | 95 | 12 | 60000 | compile-pass setup; graph compile + optimized callees |
| life | compile-pass random 24x24 initial literal | 153.531 | 0.370 | 153.901 | n/a | 153.902 | 0.407 | 149.540 | 135.700 | 0.000 | 0.000 | 0.000 | 73 | 0 | 0 | 0 | 0.025 | 0.627 | 57 | 6 | 60000 | compile-pass setup with initial grid literal; graph compile + optimized callees |
| temperature | NOAA committed slice | n/a | 0.426 | 0.426 | 25.21 x | 0.443 | 0.033 | 0.078 | 0.008 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.004 | 0.060 | 38 | 3 | 30000 | graph compile + optimized callees |
| temperature | synthetic 32x64 | n/a | 0.290 | 0.290 | 26.74 x | 0.301 | 0.014 | 0.059 | 0.013 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.004 | 0.050 | 38 | 3 | 30000 | graph compile + optimized callees |
| sliding puzzle | 2x2 pure source full frontier step | n/a | 2.995 | 2.995 | n/a | 2.996 | 0.106 | 2.083 | 1.233 | 0.000 | 0.000 | 0.000 | 65 | 0 | 0 | 0 | 0.003 | 0.064 | 38 | 3 | 30000 | graph compile + optimized callees |
| sliding puzzle | 2x2 pure compile-pass full frontier step | 21.423 | 0.277 | 21.700 | n/a | 21.701 | 0.332 | 20.393 | 5.981 | 0.000 | 0.000 | 0.000 | 66 | 0 | 0 | 0 | 0.002 | 0.076 | 57 | 3 | 60000 | compile-pass setup; graph compile + optimized callees |
| sliding puzzle | 3x3 pure source depth-8 step | n/a | 55.154 | 55.154 | n/a | 55.155 | 0.117 | 53.176 | 49.238 | 0.000 | 0.000 | 0.000 | 176 | 0 | 0 | 0 | 0.005 | 0.106 | 38 | 3 | 30000 | graph compile + optimized callees |
| sliding puzzle | 3x3 pure compile-pass depth-8 step | 178.527 | 0.416 | 178.943 | n/a | 178.944 | 0.690 | 176.104 | 148.187 | 0.000 | 0.000 | 0.000 | 177 | 0 | 0 | 0 | 0.003 | 0.085 | 57 | 3 | 60000 | compile-pass setup; graph compile + optimized callees |
| sliding puzzle | 4x4 pure source depth-5 step | n/a | 118.775 | 118.775 | n/a | 118.786 | 0.336 | 115.544 | 94.124 | 0.000 | 0.000 | 0.000 | 341 | 0 | 0 | 0 | 0.004 | 0.082 | 38 | 3 | 30000 | graph compile + optimized callees |
| n-queens | MORKL 8x8 source | n/a | 575.540 | 575.540 | n/a | 575.541 | 0.145 | 574.221 | 568.406 | 0.000 | 0.000 | 0.000 | 130 | 0 | 0 | 0 | 0.004 | 0.082 | 38 | 3 | 30000 | graph compile + optimized callees |
| n-queens | MORKL 8x8 compile-pass | 927.984 | 0.419 | 928.403 | n/a | 928.403 | 0.997 | 924.118 | 886.616 | 0.000 | 0.000 | 0.000 | 131 | 0 | 0 | 0 | 0.003 | 0.081 | 57 | 3 | 60000 | compile-pass setup; graph compile + optimized callees |

| benchmark | variant | result | ms | note |
|---|---:|---:|---:|---|
| n-queens bit reference | n=8 | 92 | 0.034 | independent reference, not MORKL eval |
| n-queens bit reference | n=9 | 352 | 0.067 | independent reference, not MORKL eval |
| n-queens bit reference | n=10 | 724 | 0.302 | independent reference, not MORKL eval |
| n-queens bit reference | n=11 | 2680 | 1.475 | independent reference, not MORKL eval |
| n-queens bit reference | n=12 | 14200 | 7.608 | independent reference, not MORKL eval |

## Interpretation

The trie evaluator remains strongest on native path algebra with shared prefixes, joins, restriction, unwrap, and first-symbol iteration. The direct `execT` backend compounds that advantage when the graph is lowered to native ops and optimized callee graphs because it avoids rebuilding old `PathValue` and `SpaceValue` intermediates.

`evalZ` is included as a correctness-backed zipper traversal prototype, not yet as the winning runtime for every source shape. Memoized virtual zippers now keep the sliding-puzzle source rows near `evalTrie`, and union-saturating recursion is lowered and measured directly. Structurally positive, productive steps use prefix-demand cells; the SCC tail projection and semi-naive Datalog's state-negative delta subtraction use the lazy exact synchronous fallback on first observation. The separate `ZIPPER_LARGE_BENCHMARKS.md` report contains larger asymptotic product-selector rows where zipper traversal avoids broad intermediate scans. Direct source n-queens remains omitted for `evalZ` with an explicit note because that high-level search tree still needs a dedicated recursive zipper strategy.

The pure Game-of-Life source is now intentionally a lowering benchmark: direct `evalTrie` still interprets the high-level relation program, while the graph backend lowers `Range`, `Unwrap`, joins, and literal coordinate relations into direct operations; use the `ROG execT` and compile+`ROG execT` columns for the lowered result.

Graph timings are intentionally conservative about semantics. Raw routine calls are not timed as raw helper graphs; helper routines are inlined/expanded and lowered before graph execution when possible, and surviving nonrecursive calls use optimized callee graphs. The fixpoint lowering handles the `Routines.fixpoint` union-saturating shape, while recursive residual call cycles still need dedicated lowering before `exec`/`execT` can be reported honestly.

Compilation is now explicitly bounded by wall-clock budgets as well as structural caps. Source normalization records every pass attempt, graph optimization records `hoist_loop_invariant_subgraphs`, `push_out`, and `optimize_sharing` per round, constant folding records eval/evalTrie/evalZ/execT time and call counts, and the compilation table separates setup SC time, lowering/inlining time, source-pass time, graph-build time, graph-optimization time, compile/run ratio, and compile+run time so runtime speedups are not mistaken for free compilation.

`budget ms sum` is the sum of the independent compile invocation budgets used for a row, including optimized callee-graph compiles when present; it is not elapsed time and not a single shared deadline across all rows.

Rows marked `compiled away; use compile+run` have been fully residualized to a one-node literal by the compile pipeline before execution. Their executor timings are still shown as an implementation sanity check, but their graph speedup factors are intentionally reported as `n/a`; the meaningful number is the compile+`ROG execT` total in the compilation table.

The independent bit-recursive n-queens rows are scaling references, not MORKL runtime rows. They record the known targets through 12x12 while the pure MORKL path-algebra program is executed exactly at 8x8 and compiled/residualized for graph execution.
