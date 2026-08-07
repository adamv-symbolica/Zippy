# Trie Runtime Benchmarks

Measured with `TrieBenchmarks` on this worktree. Runtime times are average milliseconds per evaluation after two warmup runs. The first table excludes compilation/supercompilation and graph construction; rows labelled `process-sc` or `compile-pass` time the residual/compiled runtime. Runtime speedup cells and compile/run ratios are deliberately omitted for graphs that have been completely residualized to a one-node literal; use the compile+run column in the second table for those rows. The second table reports setup supercompilation time, graph compilation time, total compile time, compile/run ratio for non-residual graph execution, compile+`ROG execT` time, timed compile-stage totals, constant-folding eval time, and optimization-pass totals under explicit compile budgets. Graph optimization includes loop-invariant subgraph hoisting, push-out, and sharing. `ROG exec` is the legacy `RecursiveOpGraph` executor over `SpaceValue`; `evalZ` is the declarative zipper traversal evaluator that composes logical trie cursors and materializes only at the result boundary; `ROG execT` is the interned trie executor over `TrieSpace` and `List[Int]` path slots. Graph rows use fully optimized graphs: helper functions are inlined/expanded/lowered where possible, any surviving nonrecursive `Call`s dispatch to optimized callee graphs, and graph-note cells list retained calls found in both the top graph and optimized callee graphs. Recursive source `Call` cycles that survive lowering are marked unsupported.

## Runtime

| benchmark | variant | result paths | eval Set ms | ROG exec ms | evalTrie ms | evalZ ms | ROG execT ms | evalTrie / eval | evalZ / eval | evalZ / evalTrie | ROG exec / eval | ROG execT / eval | ROG execT / evalTrie | graph nodes | graph note |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| aunt | reference | 3 | 0.089 | 0.208 | 0.351 | 1.015 | 0.426 | 0.25 x | 0.09 x | 0.35 x | 0.43 x | 0.21 x | 0.82 x | 21 |  |
| aunt | process-sc static family | 3 | 0.118 | 0.141 | 0.278 | 1.160 | 0.150 | 0.42 x | 0.10 x | 0.24 x | 0.83 x | 0.79 x | 1.85 x | 20 |  |
| aunt synthetic | reference 60 people | 36 | 3.623 | 1.693 | 1.208 | 4.095 | 0.723 | 3.00 x | 0.88 x | 0.30 x | 2.14 x | 5.01 x | 1.67 x | 21 |  |
| aunt royal92 | reference royal92_simple.metta fallback | 3 | 0.089 | 0.045 | 0.062 | 0.310 | 0.058 | 1.45 x | 0.29 x | 0.20 x | 1.97 x | 1.54 x | 1.06 x | 21 |  |
| aunt synthetic | process-sc static family 24 people | 12 | 0.350 | 0.198 | 0.798 | 1.169 | 0.176 | 0.44 x | 0.30 x | 0.68 x | 1.76 x | 1.99 x | 4.54 x | 20 |  |
| graph two-hop | reference 90-chain | 264 | 3.633 | 3.302 | 2.185 | 12.107 | 0.375 | 1.66 x | 0.30 x | 0.18 x | 1.10 x | 9.68 x | 5.82 x | 15 |  |
| graph mutual | reference 90-chain | 0 | 3.458 | 0.972 | 0.276 | 3.164 | 0.163 | 12.55 x | 1.09 x | 0.09 x | 3.56 x | 21.28 x | 1.70 x | 15 |  |
| datalog semi-naive | reference 24-chain | 300 | 273.856 | 0.002 | 6.150 | n/a | 0.002 | 44.53 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ rejects the recursive top-level self-union; use evalTrie/graph rows |
| datalog semi-naive | process-sc 24-chain | 300 | 254.213 | 20.183 | 8.899 | n/a | 3.930 | 28.57 x | n/a | n/a | 12.60 x | 64.68 x | 2.26 x | 6 | exec fallback Call dispatch to optimized callee graph(s): step708533063_sc1; direct evalZ rejects the residual recursive top-level self-union; use evalTrie/graph rows |
| life | reference random 24x24 | 125 | 32.432 | 22.587 | 566.482 | 619.837 | 7.640 | 0.06 x | 0.05 x | 0.91 x | 1.44 x | 4.24 x | 74.14 x | 2,667 | exec fallback Call dispatch to optimized callee graph(s): neigh |
| life | compile-pass random 24x24 | 125 | 28.281 | 0.001 | 770.782 | 819.601 | 0.001 | 0.04 x | 0.03 x | 0.94 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| life | compile-pass random 24x24 initial literal | 125 | 0.000 | 0.001 | 0.039 | 0.037 | 0.001 | 0.01 x | 0.01 x | 1.07 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| temperature | NOAA committed slice | 430 | 0.144 | 0.120 | 0.007 | 0.030 | 0.004 | 19.54 x | 4.85 x | 0.25 x | 1.20 x | 32.05 x | 1.64 x | 3 |  |
| temperature | synthetic 32x64 | 448 | 1.530 | 1.043 | 0.041 | 0.095 | 0.016 | 37.46 x | 16.04 x | 0.43 x | 1.47 x | 96.65 x | 2.58 x | 3 |  |
| sliding puzzle | 2x2 pure source full frontier step | 12 | 5.466 | 0.001 | 4.540 | 11.913 | 0.001 | 1.20 x | 0.46 x | 0.38 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| sliding puzzle | 2x2 pure compile-pass full frontier step | 12 | 0.000 | 0.001 | 0.004 | 0.008 | 0.001 | 0.11 x | 0.05 x | 0.43 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| sliding puzzle | 3x3 pure source depth-8 step | 420 | 300.110 | 0.002 | 397.900 | n/a | 0.001 | 0.75 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ source search tree exceeds the benchmark heap; use evalTrie/graph rows |
| sliding puzzle | 3x3 pure compile-pass depth-8 step | 420 | 0.001 | 0.001 | 0.219 | 0.240 | 0.001 | 0.00 x | 0.00 x | 0.91 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| sliding puzzle | 4x4 pure source depth-5 step | 202 | 531.261 | 0.001 | 696.288 | n/a | 0.001 | 0.76 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ source search tree exceeds the benchmark heap; use evalTrie/graph rows |
| n-queens | MORKL 8x8 source | 92 | 483.595 | 0.002 | 1472.936 | n/a | 0.001 | 0.33 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ is currently omitted for the high-level source search tree; use compile-pass/graph rows |
| n-queens | MORKL 8x8 compile-pass | 92 | 0.001 | 0.001 | 0.038 | 0.040 | 0.001 | 0.01 x | 0.01 x | 0.95 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |

## Compilation And Optimization

| benchmark | variant | prep compile ms | graph compile ms | total compile ms | compile/run | compile+ROG execT ms | lower+inline ms | source pass ms | const-fold eval ms | const-fold evalTrie ms | const-fold evalZ ms | const-fold execT ms | const-fold eval calls | const-fold evalTrie calls | const-fold evalZ calls | const-fold execT calls | graph transpile ms | graph optimize ms | source pass attempts | graph pass steps | budget ms sum | note |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| aunt | reference | n/a | 4.668 | 4.668 | 10.96 x | 5.094 | 0.125 | 0.864 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.202 | 1.714 | 38 | 6 | 30000 | graph compile + optimized callees |
| aunt | process-sc static family | 39.884 | 4.640 | 44.524 | 296.87 x | 44.674 | 2.277 | 0.729 | 0.007 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.061 | 1.243 | 19 | 6 | 60000 | process SC setup; graph compile + optimized callees |
| aunt synthetic | reference 60 people | n/a | 1.813 | 1.813 | 2.51 x | 2.536 | 0.045 | 0.355 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.078 | 0.918 | 38 | 6 | 30000 | graph compile + optimized callees |
| aunt royal92 | reference royal92_simple.metta fallback | n/a | 2.899 | 2.899 | 50.14 x | 2.957 | 0.072 | 0.586 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.149 | 1.643 | 38 | 6 | 30000 | graph compile + optimized callees |
| aunt synthetic | process-sc static family 24 people | 7.168 | 1.424 | 8.592 | 48.92 x | 8.768 | 0.091 | 0.354 | 0.004 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.070 | 0.688 | 19 | 6 | 60000 | process SC setup; graph compile + optimized callees |
| graph two-hop | reference 90-chain | n/a | 1.544 | 1.544 | 4.11 x | 1.919 | 0.019 | 0.188 | 0.020 | 0.000 | 0.000 | 0.000 | 2 | 0 | 0 | 0 | 0.042 | 0.915 | 57 | 9 | 30000 | graph compile + optimized callees |
| graph mutual | reference 90-chain | n/a | 2.404 | 2.404 | 14.79 x | 2.567 | 0.032 | 0.182 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.078 | 1.437 | 38 | 9 | 30000 | graph compile + optimized callees |
| datalog semi-naive | reference 24-chain | n/a | 26.582 | 26.582 | n/a | 26.584 | 0.417 | 25.022 | 0.167 | 23.617 | 0.000 | 0.000 | 9 | 1 | 0 | 0 | 0.005 | 0.104 | 38 | 3 | 30000 | graph compile + optimized callees |
| datalog semi-naive | process-sc 24-chain | 80.951 | 10.888 | 91.839 | 23.37 x | 95.769 | 0.323 | 2.390 | 0.009 | 0.000 | 0.000 | 0.000 | 10 | 0 | 0 | 0 | 0.388 | 5.476 | 38 | 12 | 90000 | process SC setup; graph compile + optimized callees |
| life | reference random 24x24 | n/a | 3056.103 | 3056.103 | 400.00 x | 3063.744 | 0.571 | 2740.788 | 0.445 | 0.000 | 0.000 | 0.000 | 1384 | 0 | 0 | 0 | 2.496 | 308.755 | 114 | 12 | 60000 | graph compile + optimized callees |
| life | compile-pass random 24x24 | 116.166 | 18.704 | 134.870 | n/a | 134.870 | 2.372 | 64.417 | 20.398 | 0.000 | 0.000 | 0.000 | 114 | 0 | 0 | 0 | 1.377 | 49.562 | 95 | 12 | 60000 | compile-pass setup; graph compile + optimized callees |
| life | compile-pass random 24x24 initial literal | 111.710 | 0.382 | 112.092 | n/a | 112.093 | 0.204 | 110.021 | 103.207 | 0.000 | 0.000 | 0.000 | 73 | 0 | 0 | 0 | 0.013 | 0.258 | 57 | 6 | 60000 | compile-pass setup with initial grid literal; graph compile + optimized callees |
| temperature | NOAA committed slice | n/a | 0.413 | 0.413 | 92.01 x | 0.418 | 0.028 | 0.086 | 0.005 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.005 | 0.055 | 38 | 3 | 30000 | graph compile + optimized callees |
| temperature | synthetic 32x64 | n/a | 0.442 | 0.442 | 27.92 x | 0.458 | 0.017 | 0.160 | 0.018 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.007 | 0.086 | 38 | 3 | 30000 | graph compile + optimized callees |
| sliding puzzle | 2x2 pure source full frontier step | n/a | 2.615 | 2.615 | n/a | 2.616 | 0.107 | 1.552 | 0.927 | 0.000 | 0.000 | 0.000 | 65 | 0 | 0 | 0 | 0.006 | 0.114 | 38 | 3 | 30000 | graph compile + optimized callees |
| sliding puzzle | 2x2 pure compile-pass full frontier step | 17.450 | 0.408 | 17.858 | n/a | 17.859 | 0.180 | 16.260 | 8.378 | 0.000 | 0.000 | 0.000 | 66 | 0 | 0 | 0 | 0.004 | 0.115 | 57 | 3 | 60000 | compile-pass setup; graph compile + optimized callees |
| sliding puzzle | 3x3 pure source depth-8 step | n/a | 45.835 | 45.835 | n/a | 45.836 | 0.141 | 44.400 | 42.231 | 0.000 | 0.000 | 0.000 | 176 | 0 | 0 | 0 | 0.003 | 0.074 | 38 | 3 | 30000 | graph compile + optimized callees |
| sliding puzzle | 3x3 pure compile-pass depth-8 step | 140.520 | 0.222 | 140.741 | n/a | 140.742 | 0.860 | 137.365 | 113.153 | 0.000 | 0.000 | 0.000 | 177 | 0 | 0 | 0 | 0.003 | 0.054 | 57 | 3 | 60000 | compile-pass setup; graph compile + optimized callees |
| sliding puzzle | 4x4 pure source depth-5 step | n/a | 56.648 | 56.648 | n/a | 56.649 | 0.160 | 54.404 | 45.146 | 0.000 | 0.000 | 0.000 | 341 | 0 | 0 | 0 | 0.003 | 0.056 | 38 | 3 | 30000 | graph compile + optimized callees |
| n-queens | MORKL 8x8 source | n/a | 348.194 | 348.194 | n/a | 348.194 | 0.120 | 347.065 | 343.303 | 0.000 | 0.000 | 0.000 | 130 | 0 | 0 | 0 | 0.003 | 0.055 | 38 | 3 | 30000 | graph compile + optimized callees |
| n-queens | MORKL 8x8 compile-pass | 393.964 | 0.345 | 394.308 | n/a | 394.309 | 0.398 | 393.075 | 384.276 | 0.000 | 0.000 | 0.000 | 131 | 0 | 0 | 0 | 0.003 | 0.062 | 57 | 3 | 60000 | compile-pass setup; graph compile + optimized callees |

| benchmark | variant | result | ms | note |
|---|---:|---:|---:|---|
| n-queens bit reference | n=8 | 92 | 0.012 | independent reference, not MORKL eval |
| n-queens bit reference | n=9 | 352 | 0.032 | independent reference, not MORKL eval |
| n-queens bit reference | n=10 | 724 | 0.181 | independent reference, not MORKL eval |
| n-queens bit reference | n=11 | 2680 | 0.696 | independent reference, not MORKL eval |
| n-queens bit reference | n=12 | 14200 | 4.135 | independent reference, not MORKL eval |

## Interpretation

The trie evaluator remains strongest on native path algebra with shared prefixes, joins, restriction, unwrap, and first-symbol iteration. The direct `execT` backend compounds that advantage when the graph is lowered to native ops and optimized callee graphs because it avoids rebuilding old `PathValue` and `SpaceValue` intermediates.

`evalZ` is included as a correctness-backed zipper traversal prototype, not yet as the winning runtime for every source shape. Memoized virtual zippers now keep the sliding-puzzle source rows near `evalTrie`, and top-level union-saturating self recursion is rejected as unsupported rather than falling back to concrete `evalTrie` materialization. The separate `ZIPPER_LARGE_BENCHMARKS.md` report contains larger asymptotic product-selector rows where zipper traversal avoids broad intermediate scans. Direct source n-queens remains omitted for `evalZ` with an explicit note because that high-level search tree still needs a dedicated recursive zipper strategy.

The pure Game-of-Life source is now intentionally a lowering benchmark: direct `evalTrie` still interprets the high-level relation program, while the graph backend lowers `Range`, `Unwrap`, joins, and literal coordinate relations into direct operations; use the `ROG execT` and compile+`ROG execT` columns for the lowered result.

Graph timings are intentionally conservative about semantics. Raw routine calls are not timed as raw helper graphs; helper routines are inlined/expanded and lowered before graph execution when possible, and surviving nonrecursive calls use optimized callee graphs. The fixpoint lowering handles the `Routines.fixpoint` union-saturating shape, while recursive residual call cycles still need dedicated lowering before `exec`/`execT` can be reported honestly.

Compilation is now explicitly bounded by wall-clock budgets as well as structural caps. Source normalization records every pass attempt, graph optimization records `hoist_loop_invariant_subgraphs`, `push_out`, and `optimize_sharing` per round, constant folding records eval/evalTrie/evalZ/execT time and call counts, and the compilation table separates setup SC time, lowering/inlining time, source-pass time, graph-build time, graph-optimization time, compile/run ratio, and compile+run time so runtime speedups are not mistaken for free compilation.

`budget ms sum` is the sum of the independent compile invocation budgets used for a row, including optimized callee-graph compiles when present; it is not elapsed time and not a single shared deadline across all rows.

Rows marked `compiled away; use compile+run` have been fully residualized to a one-node literal by the compile pipeline before execution. Their executor timings are still shown as an implementation sanity check, but their graph speedup factors are intentionally reported as `n/a`; the meaningful number is the compile+`ROG execT` total in the compilation table.

The independent bit-recursive n-queens rows are scaling references, not MORKL runtime rows. They record the known targets through 12x12 while the pure MORKL path-algebra program is executed exactly at 8x8 and compiled/residualized for graph execution.
