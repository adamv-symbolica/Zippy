# Trie Runtime Benchmarks

Measured with `TrieBenchmarks` on this worktree. Runtime times are average milliseconds per evaluation after two warmup runs. The first table excludes compilation/supercompilation and graph construction; rows labelled `process-sc` or `compile-pass` time the residual/compiled runtime. Runtime speedup cells and compile/run ratios are deliberately omitted for graphs that have been completely residualized to a one-node literal; use the compile+run column in the second table for those rows. The second table reports setup supercompilation time, graph compilation time, total compile time, compile/run ratio for non-residual graph execution, compile+`ROG execT` time, timed compile-stage totals, constant-folding eval time, and optimization-pass totals under explicit compile budgets. Graph optimization includes loop-invariant subgraph hoisting, push-out, and sharing. `ROG exec` is the legacy `RecursiveOpGraph` executor over `SpaceValue`; `evalZ` is the declarative zipper traversal evaluator that composes logical trie cursors and materializes only at the result boundary; `ROG execT` is the interned trie executor over `TrieSpace` and `List[Int]` path slots. Graph rows use fully optimized graphs: helper functions are inlined/expanded/lowered where possible, any surviving nonrecursive `Call`s dispatch to optimized callee graphs, and graph-note cells list retained calls found in both the top graph and optimized callee graphs. Recursive source `Call` cycles that survive lowering are marked unsupported.

## Runtime

| benchmark | variant | result paths | eval Set ms | ROG exec ms | evalTrie ms | evalZ ms | ROG execT ms | evalTrie / eval | evalZ / eval | evalZ / evalTrie | ROG exec / eval | ROG execT / eval | ROG execT / evalTrie | graph nodes | graph note |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| aunt | reference | 3 | 0.187 | 0.218 | 0.395 | 1.164 | 0.308 | 0.47 x | 0.16 x | 0.34 x | 0.86 x | 0.61 x | 1.28 x | 21 |  |
| aunt | process-sc static family | 3 | 0.119 | 0.289 | 0.605 | 0.550 | 0.342 | 0.20 x | 0.22 x | 1.10 x | 0.41 x | 0.35 x | 1.77 x | 20 |  |
| aunt synthetic | reference 60 people | 36 | 3.647 | 0.947 | 1.619 | 2.240 | 1.433 | 2.25 x | 1.63 x | 0.72 x | 3.85 x | 2.55 x | 1.13 x | 21 |  |
| aunt royal92 | reference royal92_simple.metta fallback | 3 | 0.037 | 0.047 | 0.092 | 0.145 | 0.080 | 0.40 x | 0.25 x | 0.63 x | 0.77 x | 0.46 x | 1.15 x | 21 |  |
| aunt synthetic | process-sc static family 24 people | 12 | 0.124 | 0.217 | 1.102 | 1.228 | 0.287 | 0.11 x | 0.10 x | 0.90 x | 0.57 x | 0.43 x | 3.84 x | 20 |  |
| graph two-hop | reference 90-chain | 264 | 5.568 | 2.306 | 1.650 | 16.707 | 1.511 | 3.37 x | 0.33 x | 0.10 x | 2.41 x | 3.69 x | 1.09 x | 15 |  |
| graph mutual | reference 90-chain | 0 | 4.476 | 0.840 | 0.196 | 0.921 | 0.182 | 22.85 x | 4.86 x | 0.21 x | 5.33 x | 24.56 x | 1.07 x | 15 |  |
| datalog semi-naive | reference 24-chain | 300 | 314.122 | 0.005 | 8.469 | n/a | 0.004 | 37.09 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ rejects the recursive top-level self-union; use evalTrie/graph rows |
| datalog semi-naive | process-sc 24-chain | 300 | 298.066 | 30.465 | 9.469 | n/a | 3.086 | 31.48 x | n/a | n/a | 9.78 x | 96.59 x | 3.07 x | 6 | exec fallback Call dispatch to optimized callee graph(s): step352598575_sc1; direct evalZ rejects the residual recursive top-level self-union; use evalTrie/graph rows |
| life | reference random 24x24 | 125 | 42.803 | 23.946 | 365.660 | 389.393 | 4.372 | 0.12 x | 0.11 x | 0.94 x | 1.79 x | 9.79 x | 83.64 x | 2,667 | exec fallback Call dispatch to optimized callee graph(s): neigh |
| life | compile-pass random 24x24 | 125 | 32.361 | 0.001 | 264.429 | 282.948 | 0.001 | 0.12 x | 0.11 x | 0.93 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| life | compile-pass random 24x24 initial literal | 125 | 0.000 | 0.001 | 0.029 | 0.030 | 0.001 | 0.01 x | 0.01 x | 0.96 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| temperature | NOAA committed slice | 430 | 0.083 | 0.072 | 0.009 | 0.030 | 0.006 | 9.17 x | 2.76 x | 0.30 x | 1.15 x | 12.93 x | 1.41 x | 3 |  |
| temperature | synthetic 32x64 | 448 | 1.124 | 1.105 | 0.052 | 0.051 | 0.020 | 21.62 x | 22.22 x | 1.03 x | 1.02 x | 55.99 x | 2.59 x | 3 |  |
| sliding puzzle | 2x2 pure source full frontier step | 12 | 4.683 | 0.001 | 6.634 | 12.396 | 0.001 | 0.71 x | 0.38 x | 0.54 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| sliding puzzle | 2x2 pure compile-pass full frontier step | 12 | 0.001 | 0.001 | 0.006 | 0.007 | 0.001 | 0.09 x | 0.08 x | 0.88 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| sliding puzzle | 3x3 pure source depth-8 step | 420 | 396.044 | 0.001 | 517.448 | n/a | 0.001 | 0.77 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ source search tree exceeds the benchmark heap; use evalTrie/graph rows |
| sliding puzzle | 3x3 pure compile-pass depth-8 step | 420 | 0.001 | 0.001 | 0.284 | 0.625 | 0.001 | 0.00 x | 0.00 x | 0.45 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| sliding puzzle | 4x4 pure source depth-5 step | 202 | 597.053 | 0.002 | 954.730 | n/a | 0.001 | 0.63 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ source search tree exceeds the benchmark heap; use evalTrie/graph rows |
| n-queens | MORKL 8x8 source | 92 | 566.197 | 0.002 | 2112.835 | n/a | 0.001 | 0.27 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ is currently omitted for the high-level source search tree; use compile-pass/graph rows |
| n-queens | MORKL 8x8 compile-pass | 92 | 0.000 | 0.001 | 0.053 | 0.049 | 0.001 | 0.01 x | 0.01 x | 1.06 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |

## Compilation And Optimization

| benchmark | variant | prep compile ms | graph compile ms | total compile ms | compile/run | compile+ROG execT ms | lower+inline ms | source pass ms | const-fold eval ms | const-fold evalTrie ms | const-fold evalZ ms | const-fold execT ms | const-fold eval calls | const-fold evalTrie calls | const-fold evalZ calls | const-fold execT calls | graph transpile ms | graph optimize ms | source pass attempts | graph pass steps | budget ms sum | note |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| aunt | reference | n/a | 2.649 | 2.649 | 8.60 x | 2.957 | 0.098 | 0.449 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.114 | 0.889 | 38 | 6 | 30000 | graph compile + optimized callees |
| aunt | process-sc static family | 53.605 | 5.676 | 59.281 | 173.18 x | 59.623 | 2.427 | 1.362 | 0.010 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.113 | 1.409 | 19 | 6 | 60000 | process SC setup; graph compile + optimized callees |
| aunt synthetic | reference 60 people | n/a | 1.548 | 1.548 | 1.08 x | 2.981 | 0.033 | 0.365 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.079 | 0.818 | 38 | 6 | 30000 | graph compile + optimized callees |
| aunt royal92 | reference royal92_simple.metta fallback | n/a | 1.333 | 1.333 | 16.71 x | 1.413 | 0.029 | 0.275 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.089 | 0.757 | 38 | 6 | 30000 | graph compile + optimized callees |
| aunt synthetic | process-sc static family 24 people | 13.189 | 1.437 | 14.626 | 50.96 x | 14.913 | 0.079 | 0.256 | 0.003 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.050 | 0.865 | 19 | 6 | 60000 | process SC setup; graph compile + optimized callees |
| graph two-hop | reference 90-chain | n/a | 1.504 | 1.504 | 1.00 x | 3.015 | 0.018 | 0.169 | 0.015 | 0.000 | 0.000 | 0.000 | 2 | 0 | 0 | 0 | 0.041 | 0.911 | 57 | 9 | 30000 | graph compile + optimized callees |
| graph mutual | reference 90-chain | n/a | 1.325 | 1.325 | 7.27 x | 1.507 | 0.018 | 0.104 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.038 | 0.847 | 38 | 9 | 30000 | graph compile + optimized callees |
| datalog semi-naive | reference 24-chain | n/a | 27.161 | 27.161 | n/a | 27.165 | 0.412 | 25.778 | 0.153 | 24.453 | 0.000 | 0.000 | 9 | 1 | 0 | 0 | 0.004 | 0.088 | 38 | 3 | 30000 | graph compile + optimized callees |
| datalog semi-naive | process-sc 24-chain | 88.860 | 13.330 | 102.190 | 33.11 x | 105.276 | 0.167 | 1.720 | 0.006 | 0.000 | 0.000 | 0.000 | 10 | 0 | 0 | 0 | 0.377 | 8.451 | 38 | 12 | 90000 | process SC setup; graph compile + optimized callees |
| life | reference random 24x24 | n/a | 3752.392 | 3752.392 | 858.29 x | 3756.764 | 4.540 | 3379.907 | 0.361 | 0.000 | 0.000 | 0.000 | 1384 | 0 | 0 | 0 | 2.573 | 361.212 | 114 | 12 | 60000 | graph compile + optimized callees |
| life | compile-pass random 24x24 | 105.567 | 21.022 | 126.589 | n/a | 126.590 | 1.565 | 68.880 | 19.331 | 0.000 | 0.000 | 0.000 | 114 | 0 | 0 | 0 | 2.373 | 42.829 | 95 | 12 | 60000 | compile-pass setup; graph compile + optimized callees |
| life | compile-pass random 24x24 initial literal | 103.900 | 0.203 | 104.103 | n/a | 104.104 | 0.136 | 101.903 | 95.120 | 0.000 | 0.000 | 0.000 | 73 | 0 | 0 | 0 | 0.013 | 0.205 | 57 | 6 | 60000 | compile-pass setup with initial grid literal; graph compile + optimized callees |
| temperature | NOAA committed slice | n/a | 0.419 | 0.419 | 65.25 x | 0.426 | 0.028 | 0.075 | 0.004 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.005 | 0.067 | 38 | 3 | 30000 | graph compile + optimized callees |
| temperature | synthetic 32x64 | n/a | 0.182 | 0.182 | 9.04 x | 0.202 | 0.007 | 0.051 | 0.006 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.004 | 0.040 | 38 | 3 | 30000 | graph compile + optimized callees |
| sliding puzzle | 2x2 pure source full frontier step | n/a | 3.421 | 3.421 | n/a | 3.422 | 0.125 | 2.498 | 1.084 | 0.000 | 0.000 | 0.000 | 65 | 0 | 0 | 0 | 0.003 | 0.056 | 38 | 3 | 30000 | graph compile + optimized callees |
| sliding puzzle | 2x2 pure compile-pass full frontier step | 15.578 | 0.181 | 15.759 | n/a | 15.759 | 0.258 | 14.115 | 4.608 | 0.000 | 0.000 | 0.000 | 66 | 0 | 0 | 0 | 0.002 | 0.050 | 57 | 3 | 60000 | compile-pass setup; graph compile + optimized callees |
| sliding puzzle | 3x3 pure source depth-8 step | n/a | 42.851 | 42.851 | n/a | 42.852 | 0.471 | 40.577 | 37.624 | 0.000 | 0.000 | 0.000 | 176 | 0 | 0 | 0 | 0.003 | 0.102 | 38 | 3 | 30000 | graph compile + optimized callees |
| sliding puzzle | 3x3 pure compile-pass depth-8 step | 117.676 | 1.562 | 119.237 | n/a | 119.238 | 0.768 | 115.692 | 90.446 | 0.000 | 0.000 | 0.000 | 177 | 0 | 0 | 0 | 0.004 | 0.338 | 57 | 3 | 60000 | compile-pass setup; graph compile + optimized callees |
| sliding puzzle | 4x4 pure source depth-5 step | n/a | 66.391 | 66.391 | n/a | 66.392 | 0.416 | 61.971 | 51.485 | 0.000 | 0.000 | 0.000 | 341 | 0 | 0 | 0 | 0.007 | 0.097 | 38 | 3 | 30000 | graph compile + optimized callees |
| n-queens | MORKL 8x8 source | n/a | 390.155 | 390.155 | n/a | 390.155 | 0.125 | 388.742 | 384.920 | 0.000 | 0.000 | 0.000 | 130 | 0 | 0 | 0 | 0.003 | 0.112 | 38 | 3 | 30000 | graph compile + optimized callees |
| n-queens | MORKL 8x8 compile-pass | 477.137 | 1.908 | 479.045 | n/a | 479.046 | 0.650 | 476.438 | 470.338 | 0.000 | 0.000 | 0.000 | 131 | 0 | 0 | 0 | 0.015 | 0.558 | 57 | 3 | 60000 | compile-pass setup; graph compile + optimized callees |

| benchmark | variant | result | ms | note |
|---|---:|---:|---:|---|
| n-queens bit reference | n=8 | 92 | 0.065 | independent reference, not MORKL eval |
| n-queens bit reference | n=9 | 352 | 0.032 | independent reference, not MORKL eval |
| n-queens bit reference | n=10 | 724 | 0.180 | independent reference, not MORKL eval |
| n-queens bit reference | n=11 | 2680 | 0.760 | independent reference, not MORKL eval |
| n-queens bit reference | n=12 | 14200 | 4.582 | independent reference, not MORKL eval |

## Interpretation

The trie evaluator remains strongest on native path algebra with shared prefixes, joins, restriction, unwrap, and first-symbol iteration. The direct `execT` backend compounds that advantage when the graph is lowered to native ops and optimized callee graphs because it avoids rebuilding old `PathValue` and `SpaceValue` intermediates.

`evalZ` is included as a correctness-backed zipper traversal prototype, not yet as the winning runtime for every source shape. Memoized virtual zippers now keep the sliding-puzzle source rows near `evalTrie`, and top-level union-saturating self recursion is rejected as unsupported rather than falling back to concrete `evalTrie` materialization. The separate `ZIPPER_LARGE_BENCHMARKS.md` report contains larger asymptotic product-selector rows where zipper traversal avoids broad intermediate scans. Direct source n-queens remains omitted for `evalZ` with an explicit note because that high-level search tree still needs a dedicated recursive zipper strategy.

The pure Game-of-Life source is now intentionally a lowering benchmark: direct `evalTrie` still interprets the high-level relation program, while the graph backend lowers `Range`, `Unwrap`, joins, and literal coordinate relations into direct operations; use the `ROG execT` and compile+`ROG execT` columns for the lowered result.

Graph timings are intentionally conservative about semantics. Raw routine calls are not timed as raw helper graphs; helper routines are inlined/expanded and lowered before graph execution when possible, and surviving nonrecursive calls use optimized callee graphs. The fixpoint lowering handles the `Routines.fixpoint` union-saturating shape, while recursive residual call cycles still need dedicated lowering before `exec`/`execT` can be reported honestly.

Compilation is now explicitly bounded by wall-clock budgets as well as structural caps. Source normalization records every pass attempt, graph optimization records `hoist_loop_invariant_subgraphs`, `push_out`, and `optimize_sharing` per round, constant folding records eval/evalTrie/evalZ/execT time and call counts, and the compilation table separates setup SC time, lowering/inlining time, source-pass time, graph-build time, graph-optimization time, compile/run ratio, and compile+run time so runtime speedups are not mistaken for free compilation.

`budget ms sum` is the sum of the independent compile invocation budgets used for a row, including optimized callee-graph compiles when present; it is not elapsed time and not a single shared deadline across all rows.

Rows marked `compiled away; use compile+run` have been fully residualized to a one-node literal by the compile pipeline before execution. Their executor timings are still shown as an implementation sanity check, but their graph speedup factors are intentionally reported as `n/a`; the meaningful number is the compile+`ROG execT` total in the compilation table.

The independent bit-recursive n-queens rows are scaling references, not MORKL runtime rows. They record the known targets through 12x12 while the pure MORKL path-algebra program is executed exactly at 8x8 and compiled/residualized for graph execution.
