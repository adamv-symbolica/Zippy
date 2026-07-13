# Trie Runtime Benchmarks

Measured with `TrieBenchmarks` on this worktree. Runtime times are average milliseconds per evaluation after two warmup runs. The first table excludes compilation/supercompilation and graph construction; rows labelled `process-sc` or `compile-pass` time the residual/compiled runtime. Runtime speedup cells and compile/run ratios are deliberately omitted for graphs that have been completely residualized to a one-node literal; use the compile+run column in the second table for those rows. The second table reports setup supercompilation time, graph compilation time, total compile time, compile/run ratio for non-residual graph execution, compile+`ROG execT` time, timed compile-stage totals, constant-folding eval time, and optimization-pass totals under explicit compile budgets. Graph optimization includes loop-invariant subgraph hoisting, push-out, and sharing. `ROG exec` is the legacy `RecursiveOpGraph` executor over `SpaceValue`; `evalZ` is the declarative zipper traversal evaluator that composes logical trie cursors and materializes only at the result boundary; `ROG execT` is the interned trie executor over `TrieSpace` and `List[Int]` path slots. Graph rows use fully optimized graphs: helper functions are inlined/expanded/lowered where possible, any surviving nonrecursive `Call`s dispatch to optimized callee graphs, and graph-note cells list retained calls found in both the top graph and optimized callee graphs. Recursive source `Call` cycles that survive lowering are marked unsupported.

## Runtime

| benchmark | variant | result paths | eval Set ms | ROG exec ms | evalTrie ms | evalZ ms | ROG execT ms | evalTrie / eval | evalZ / eval | evalZ / evalTrie | ROG exec / eval | ROG execT / eval | ROG execT / evalTrie | graph nodes | graph note |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| aunt | reference | 3 | 0.068 | 0.165 | 0.333 | 0.644 | 0.199 | 0.20 x | 0.11 x | 0.52 x | 0.41 x | 0.34 x | 1.67 x | 21 |  |
| aunt | process-sc static family | 3 | 0.030 | 0.118 | 0.205 | 0.453 | 0.134 | 0.15 x | 0.07 x | 0.45 x | 0.26 x | 0.23 x | 1.52 x | 20 |  |
| aunt synthetic | reference 60 people | 36 | 3.271 | 1.281 | 1.132 | 3.762 | 0.789 | 2.89 x | 0.87 x | 0.30 x | 2.55 x | 4.15 x | 1.43 x | 21 |  |
| aunt royal92 | reference royal92_simple.metta fallback | 3 | 0.042 | 0.047 | 0.062 | 0.167 | 0.061 | 0.67 x | 0.25 x | 0.37 x | 0.90 x | 0.69 x | 1.02 x | 21 |  |
| aunt synthetic | process-sc static family 24 people | 12 | 0.138 | 0.199 | 1.007 | 2.034 | 0.313 | 0.14 x | 0.07 x | 0.50 x | 0.69 x | 0.44 x | 3.22 x | 20 |  |
| graph two-hop | reference 90-chain | 264 | 3.614 | 1.492 | 0.893 | 12.115 | 0.390 | 4.05 x | 0.30 x | 0.07 x | 2.42 x | 9.28 x | 2.29 x | 15 |  |
| graph mutual | reference 90-chain | 0 | 3.536 | 1.180 | 0.798 | 1.365 | 0.204 | 4.43 x | 2.59 x | 0.58 x | 3.00 x | 17.30 x | 3.90 x | 15 |  |
| datalog semi-naive | reference 24-chain | 300 | 291.353 | 0.002 | 12.202 | n/a | 0.002 | 23.88 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ rejects the recursive top-level self-union; use evalTrie/graph rows |
| datalog semi-naive | process-sc 24-chain | 300 | 253.066 | 22.633 | 6.647 | n/a | 3.148 | 38.07 x | n/a | n/a | 11.18 x | 80.39 x | 2.11 x | 6 | exec fallback Call dispatch to optimized callee graph(s): step63468833_sc1; direct evalZ rejects the residual recursive top-level self-union; use evalTrie/graph rows |
| life | reference random 24x24 | 125 | 37.041 | 24.975 | 531.059 | 646.321 | 3.344 | 0.07 x | 0.06 x | 0.82 x | 1.48 x | 11.08 x | 158.79 x | 2,667 | exec fallback Call dispatch to optimized callee graph(s): neigh |
| life | compile-pass random 24x24 | 125 | 29.435 | 0.001 | 698.170 | 756.348 | 0.001 | 0.04 x | 0.04 x | 0.92 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| life | compile-pass random 24x24 initial literal | 125 | 0.000 | 0.001 | 0.040 | 0.034 | 0.001 | 0.01 x | 0.01 x | 1.18 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| temperature | NOAA committed slice | 430 | 0.152 | 0.120 | 0.006 | 0.028 | 0.004 | 24.62 x | 5.45 x | 0.22 x | 1.27 x | 34.82 x | 1.41 x | 3 |  |
| temperature | synthetic 32x64 | 448 | 1.033 | 1.079 | 0.021 | 0.047 | 0.016 | 50.16 x | 22.04 x | 0.44 x | 0.96 x | 66.26 x | 1.32 x | 3 |  |
| sliding puzzle | 2x2 pure source full frontier step | 12 | 4.478 | 0.001 | 4.981 | 7.945 | 0.001 | 0.90 x | 0.56 x | 0.63 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| sliding puzzle | 2x2 pure compile-pass full frontier step | 12 | 0.000 | 0.001 | 0.003 | 0.004 | 0.001 | 0.10 x | 0.07 x | 0.77 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| sliding puzzle | 3x3 pure source depth-8 step | 420 | 293.440 | 0.001 | 382.957 | n/a | 0.001 | 0.77 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ source search tree exceeds the benchmark heap; use evalTrie/graph rows |
| sliding puzzle | 3x3 pure compile-pass depth-8 step | 420 | 0.001 | 0.001 | 0.185 | 0.194 | 0.001 | 0.00 x | 0.00 x | 0.95 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| sliding puzzle | 4x4 pure source depth-5 step | 202 | 490.567 | 0.001 | 650.582 | n/a | 0.001 | 0.75 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ source search tree exceeds the benchmark heap; use evalTrie/graph rows |
| n-queens | MORKL 8x8 source | 92 | 518.156 | 0.003 | 1561.251 | n/a | 0.001 | 0.33 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ is currently omitted for the high-level source search tree; use compile-pass/graph rows |
| n-queens | MORKL 8x8 compile-pass | 92 | 0.001 | 0.001 | 0.034 | 0.205 | 0.001 | 0.03 x | 0.00 x | 0.16 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |

## Compilation And Optimization

| benchmark | variant | prep compile ms | graph compile ms | total compile ms | compile/run | compile+ROG execT ms | lower+inline ms | source pass ms | const-fold eval ms | const-fold evalTrie ms | const-fold evalZ ms | const-fold execT ms | const-fold eval calls | const-fold evalTrie calls | const-fold evalZ calls | const-fold execT calls | graph transpile ms | graph optimize ms | source pass attempts | graph pass steps | budget ms sum | note |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| aunt | reference | n/a | 3.121 | 3.121 | 15.70 x | 3.319 | 0.159 | 0.488 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.115 | 1.169 | 38 | 6 | 30000 | graph compile + optimized callees |
| aunt | process-sc static family | 36.565 | 2.554 | 39.119 | 291.25 x | 39.253 | 1.262 | 0.281 | 0.003 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.048 | 0.783 | 19 | 6 | 60000 | process SC setup; graph compile + optimized callees |
| aunt synthetic | reference 60 people | n/a | 1.510 | 1.510 | 1.91 x | 2.298 | 0.043 | 0.281 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.080 | 0.881 | 38 | 6 | 30000 | graph compile + optimized callees |
| aunt royal92 | reference royal92_simple.metta fallback | n/a | 2.401 | 2.401 | 39.52 x | 2.462 | 0.037 | 0.309 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.292 | 1.458 | 38 | 6 | 30000 | graph compile + optimized callees |
| aunt synthetic | process-sc static family 24 people | 6.790 | 1.242 | 8.031 | 25.64 x | 8.345 | 0.087 | 0.305 | 0.004 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.038 | 0.620 | 19 | 6 | 60000 | process SC setup; graph compile + optimized callees |
| graph two-hop | reference 90-chain | n/a | 1.469 | 1.469 | 3.77 x | 1.858 | 0.017 | 0.204 | 0.052 | 0.000 | 0.000 | 0.000 | 2 | 0 | 0 | 0 | 0.031 | 0.852 | 57 | 9 | 30000 | graph compile + optimized callees |
| graph mutual | reference 90-chain | n/a | 1.177 | 1.177 | 5.76 x | 1.381 | 0.040 | 0.080 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.030 | 0.752 | 38 | 9 | 30000 | graph compile + optimized callees |
| datalog semi-naive | reference 24-chain | n/a | 20.452 | 20.452 | n/a | 20.454 | 0.315 | 19.662 | 0.169 | 18.530 | 0.000 | 0.000 | 9 | 1 | 0 | 0 | 0.003 | 0.082 | 38 | 3 | 30000 | graph compile + optimized callees |
| datalog semi-naive | process-sc 24-chain | 54.166 | 10.227 | 64.394 | 20.46 x | 67.542 | 0.155 | 1.965 | 0.006 | 0.000 | 0.000 | 0.000 | 10 | 0 | 0 | 0 | 0.339 | 5.518 | 38 | 12 | 90000 | process SC setup; graph compile + optimized callees |
| life | reference random 24x24 | n/a | 3248.424 | 3248.424 | 971.29 x | 3251.769 | 0.752 | 2941.863 | 0.368 | 0.000 | 0.000 | 0.000 | 1384 | 0 | 0 | 0 | 2.036 | 299.731 | 114 | 12 | 60000 | graph compile + optimized callees |
| life | compile-pass random 24x24 | 69.485 | 21.805 | 91.290 | n/a | 91.291 | 1.065 | 56.172 | 21.515 | 0.000 | 0.000 | 0.000 | 114 | 0 | 0 | 0 | 0.761 | 22.315 | 95 | 12 | 60000 | compile-pass setup; graph compile + optimized callees |
| life | compile-pass random 24x24 initial literal | 109.068 | 0.279 | 109.347 | n/a | 109.348 | 0.176 | 108.037 | 102.086 | 0.000 | 0.000 | 0.000 | 73 | 0 | 0 | 0 | 0.008 | 0.300 | 57 | 6 | 60000 | compile-pass setup with initial grid literal; graph compile + optimized callees |
| temperature | NOAA committed slice | n/a | 0.434 | 0.434 | 99.42 x | 0.438 | 0.030 | 0.091 | 0.006 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.004 | 0.070 | 38 | 3 | 30000 | graph compile + optimized callees |
| temperature | synthetic 32x64 | n/a | 0.261 | 0.261 | 16.72 x | 0.276 | 0.008 | 0.066 | 0.009 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.003 | 0.063 | 38 | 3 | 30000 | graph compile + optimized callees |
| sliding puzzle | 2x2 pure source full frontier step | n/a | 2.170 | 2.170 | n/a | 2.170 | 0.117 | 1.628 | 0.935 | 0.000 | 0.000 | 0.000 | 65 | 0 | 0 | 0 | 0.002 | 0.057 | 38 | 3 | 30000 | graph compile + optimized callees |
| sliding puzzle | 2x2 pure compile-pass full frontier step | 8.217 | 0.315 | 8.533 | n/a | 8.533 | 0.196 | 7.665 | 3.666 | 0.000 | 0.000 | 0.000 | 66 | 0 | 0 | 0 | 0.002 | 0.140 | 57 | 3 | 60000 | compile-pass setup; graph compile + optimized callees |
| sliding puzzle | 3x3 pure source depth-8 step | n/a | 37.789 | 37.789 | n/a | 37.790 | 0.133 | 37.089 | 34.817 | 0.000 | 0.000 | 0.000 | 176 | 0 | 0 | 0 | 0.002 | 0.048 | 38 | 3 | 30000 | graph compile + optimized callees |
| sliding puzzle | 3x3 pure compile-pass depth-8 step | 108.296 | 0.488 | 108.784 | n/a | 108.785 | 0.495 | 107.443 | 79.324 | 0.000 | 0.000 | 0.000 | 177 | 0 | 0 | 0 | 0.002 | 0.094 | 57 | 3 | 60000 | compile-pass setup; graph compile + optimized callees |
| sliding puzzle | 4x4 pure source depth-5 step | n/a | 57.001 | 57.001 | n/a | 57.002 | 0.208 | 55.909 | 45.489 | 0.000 | 0.000 | 0.000 | 341 | 0 | 0 | 0 | 0.003 | 0.056 | 38 | 3 | 30000 | graph compile + optimized callees |
| n-queens | MORKL 8x8 source | n/a | 409.373 | 409.373 | n/a | 409.373 | 0.389 | 406.557 | 401.812 | 0.000 | 0.000 | 0.000 | 130 | 0 | 0 | 0 | 0.002 | 0.299 | 38 | 3 | 30000 | graph compile + optimized callees |
| n-queens | MORKL 8x8 compile-pass | 424.417 | 2.457 | 426.874 | n/a | 426.875 | 0.693 | 424.192 | 414.276 | 0.000 | 0.000 | 0.000 | 131 | 0 | 0 | 0 | 0.013 | 0.288 | 57 | 3 | 60000 | compile-pass setup; graph compile + optimized callees |

| benchmark | variant | result | ms | note |
|---|---:|---:|---:|---|
| n-queens bit reference | n=8 | 92 | 0.010 | independent reference, not MORKL eval |
| n-queens bit reference | n=9 | 352 | 0.032 | independent reference, not MORKL eval |
| n-queens bit reference | n=10 | 724 | 0.167 | independent reference, not MORKL eval |
| n-queens bit reference | n=11 | 2680 | 1.026 | independent reference, not MORKL eval |
| n-queens bit reference | n=12 | 14200 | 4.161 | independent reference, not MORKL eval |

## Interpretation

The trie evaluator remains strongest on native path algebra with shared prefixes, joins, restriction, unwrap, and first-symbol iteration. The direct `execT` backend compounds that advantage when the graph is lowered to native ops and optimized callee graphs because it avoids rebuilding old `PathValue` and `SpaceValue` intermediates.

`evalZ` is included as a correctness-backed zipper traversal prototype, not yet as the winning runtime for every source shape. Memoized virtual zippers now keep the sliding-puzzle source rows near `evalTrie`, and top-level union-saturating self recursion is rejected as unsupported rather than falling back to concrete `evalTrie` materialization. The separate `ZIPPER_LARGE_BENCHMARKS.md` report contains larger asymptotic product-selector rows where zipper traversal avoids broad intermediate scans. Direct source n-queens remains omitted for `evalZ` with an explicit note because that high-level search tree still needs a dedicated recursive zipper strategy.

The pure Game-of-Life source is now intentionally a lowering benchmark: direct `evalTrie` still interprets the high-level relation program, while the graph backend lowers `Range`, `Unwrap`, joins, and literal coordinate relations into direct operations; use the `ROG execT` and compile+`ROG execT` columns for the lowered result.

Graph timings are intentionally conservative about semantics. Raw routine calls are not timed as raw helper graphs; helper routines are inlined/expanded and lowered before graph execution when possible, and surviving nonrecursive calls use optimized callee graphs. The fixpoint lowering handles the `Routines.fixpoint` union-saturating shape, while recursive residual call cycles still need dedicated lowering before `exec`/`execT` can be reported honestly.

Compilation is now explicitly bounded by wall-clock budgets as well as structural caps. Source normalization records every pass attempt, graph optimization records `hoist_loop_invariant_subgraphs`, `push_out`, and `optimize_sharing` per round, constant folding records eval/evalTrie/evalZ/execT time and call counts, and the compilation table separates setup SC time, lowering/inlining time, source-pass time, graph-build time, graph-optimization time, compile/run ratio, and compile+run time so runtime speedups are not mistaken for free compilation.

`budget ms sum` is the sum of the independent compile invocation budgets used for a row, including optimized callee-graph compiles when present; it is not elapsed time and not a single shared deadline across all rows.

Rows marked `compiled away; use compile+run` have been fully residualized to a one-node literal by the compile pipeline before execution. Their executor timings are still shown as an implementation sanity check, but their graph speedup factors are intentionally reported as `n/a`; the meaningful number is the compile+`ROG execT` total in the compilation table.

The independent bit-recursive n-queens rows are scaling references, not MORKL runtime rows. They record the known targets through 12x12 while the pure MORKL path-algebra program is executed exactly at 8x8 and compiled/residualized for graph execution.
