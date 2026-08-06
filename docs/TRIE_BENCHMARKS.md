# Trie Runtime Benchmarks

Measured with `TrieBenchmarks` on this worktree. Runtime times are average milliseconds per evaluation after two warmup runs. The first table excludes compilation/supercompilation and graph construction; rows labelled `process-sc` or `compile-pass` time the residual/compiled runtime. Runtime speedup cells and compile/run ratios are deliberately omitted for graphs that have been completely residualized to a one-node literal; use the compile+run column in the second table for those rows. The second table reports setup supercompilation time, graph compilation time, total compile time, compile/run ratio for non-residual graph execution, compile+`ROG execT` time, timed compile-stage totals, constant-folding eval time, and optimization-pass totals under explicit compile budgets. Graph optimization includes loop-invariant subgraph hoisting, push-out, and sharing. `ROG exec` is the legacy `RecursiveOpGraph` executor over `SpaceValue`; `evalZ` is the declarative zipper traversal evaluator that composes logical trie cursors and materializes only at the result boundary; `ROG execT` is the interned trie executor over `TrieSpace` and `List[Int]` path slots. Graph rows use fully optimized graphs: helper functions are inlined/expanded/lowered where possible, any surviving nonrecursive `Call`s dispatch to optimized callee graphs, and graph-note cells list retained calls found in both the top graph and optimized callee graphs. Recursive source `Call` cycles that survive lowering are marked unsupported.

## Runtime

| benchmark | variant | result paths | eval Set ms | ROG exec ms | evalTrie ms | evalZ ms | ROG execT ms | evalTrie / eval | evalZ / eval | evalZ / evalTrie | ROG exec / eval | ROG execT / eval | ROG execT / evalTrie | graph nodes | graph note |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| aunt | reference | 3 | 0.197 | 0.161 | 0.439 | 0.698 | 0.175 | 0.45 x | 0.28 x | 0.63 x | 1.22 x | 1.12 x | 2.51 x | 21 |  |
| aunt | process-sc static family | 3 | 0.027 | 0.189 | 0.252 | 0.559 | 0.198 | 0.11 x | 0.05 x | 0.45 x | 0.14 x | 0.14 x | 1.27 x | 20 |  |
| aunt synthetic | reference 60 people | 36 | 4.064 | 1.651 | 0.919 | 2.712 | 0.719 | 4.42 x | 1.50 x | 0.34 x | 2.46 x | 5.65 x | 1.28 x | 21 |  |
| aunt royal92 | reference royal92_simple.metta fallback | 3 | 0.037 | 0.051 | 0.089 | 0.128 | 0.058 | 0.42 x | 0.29 x | 0.69 x | 0.73 x | 0.64 x | 1.53 x | 21 |  |
| aunt synthetic | process-sc static family 24 people | 12 | 0.124 | 0.223 | 0.842 | 1.167 | 0.168 | 0.15 x | 0.11 x | 0.72 x | 0.56 x | 0.74 x | 5.00 x | 20 |  |
| graph two-hop | reference 90-chain | 264 | 3.710 | 1.888 | 1.120 | 11.741 | 0.342 | 3.31 x | 0.32 x | 0.10 x | 1.97 x | 10.86 x | 3.28 x | 15 |  |
| graph mutual | reference 90-chain | 0 | 3.665 | 1.254 | 0.306 | 1.751 | 0.184 | 11.96 x | 2.09 x | 0.17 x | 2.92 x | 19.93 x | 1.67 x | 15 |  |
| datalog semi-naive | reference 24-chain | 300 | 269.683 | 0.001 | 5.965 | n/a | 0.002 | 45.21 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ rejects the recursive top-level self-union; use evalTrie/graph rows |
| datalog semi-naive | process-sc 24-chain | 300 | 252.157 | 21.430 | 9.350 | n/a | 1.986 | 26.97 x | n/a | n/a | 11.77 x | 126.98 x | 4.71 x | 6 | exec fallback Call dispatch to optimized callee graph(s): step708533063_sc1; direct evalZ rejects the residual recursive top-level self-union; use evalTrie/graph rows |
| life | reference random 24x24 | 125 | 33.593 | 20.557 | 558.367 | 628.438 | 2.512 | 0.06 x | 0.05 x | 0.89 x | 1.63 x | 13.37 x | 222.25 x | 2,667 | exec fallback Call dispatch to optimized callee graph(s): neigh |
| life | compile-pass random 24x24 | 125 | 29.151 | 0.001 | 756.048 | 778.251 | 0.001 | 0.04 x | 0.04 x | 0.97 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| life | compile-pass random 24x24 initial literal | 125 | 0.000 | 0.001 | 0.040 | 0.038 | 0.001 | 0.01 x | 0.01 x | 1.06 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| temperature | NOAA committed slice | 430 | 0.099 | 0.080 | 0.007 | 0.030 | 0.004 | 14.73 x | 3.30 x | 0.22 x | 1.23 x | 22.35 x | 1.52 x | 3 |  |
| temperature | synthetic 32x64 | 448 | 1.280 | 1.288 | 0.020 | 0.047 | 0.015 | 63.73 x | 27.28 x | 0.43 x | 0.99 x | 85.72 x | 1.35 x | 3 |  |
| sliding puzzle | 2x2 pure source full frontier step | 12 | 8.740 | 0.002 | 5.031 | 7.438 | 0.001 | 1.74 x | 1.18 x | 0.68 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| sliding puzzle | 2x2 pure compile-pass full frontier step | 12 | 0.001 | 0.001 | 0.009 | 0.008 | 0.001 | 0.11 x | 0.12 x | 1.09 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| sliding puzzle | 3x3 pure source depth-8 step | 420 | 323.890 | 0.001 | 386.034 | n/a | 0.001 | 0.84 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ source search tree exceeds the benchmark heap; use evalTrie/graph rows |
| sliding puzzle | 3x3 pure compile-pass depth-8 step | 420 | 0.000 | 0.001 | 0.207 | 0.211 | 0.001 | 0.00 x | 0.00 x | 0.98 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |
| sliding puzzle | 4x4 pure source depth-5 step | 202 | 489.636 | 0.001 | 662.127 | n/a | 0.001 | 0.74 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ source search tree exceeds the benchmark heap; use evalTrie/graph rows |
| n-queens | MORKL 8x8 source | 92 | 453.544 | 0.001 | 1406.447 | n/a | 0.001 | 0.32 x | n/a | n/a | n/a | n/a | n/a | 1 | compiled away; use compile+run; direct evalZ is currently omitted for the high-level source search tree; use compile-pass/graph rows |
| n-queens | MORKL 8x8 compile-pass | 92 | 0.001 | 0.001 | 0.049 | 0.039 | 0.001 | 0.01 x | 0.02 x | 1.27 x | n/a | n/a | n/a | 1 | compiled away; use compile+run |

## Compilation And Optimization

| benchmark | variant | prep compile ms | graph compile ms | total compile ms | compile/run | compile+ROG execT ms | lower+inline ms | source pass ms | const-fold eval ms | const-fold evalTrie ms | const-fold evalZ ms | const-fold execT ms | const-fold eval calls | const-fold evalTrie calls | const-fold evalZ calls | const-fold execT calls | graph transpile ms | graph optimize ms | source pass attempts | graph pass steps | budget ms sum | note |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| aunt | reference | n/a | 5.057 | 5.057 | 28.90 x | 5.232 | 0.061 | 0.482 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.323 | 2.092 | 38 | 6 | 30000 | graph compile + optimized callees |
| aunt | process-sc static family | 32.916 | 4.860 | 37.776 | 190.44 x | 37.974 | 2.693 | 0.793 | 0.005 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.060 | 0.942 | 19 | 6 | 60000 | process SC setup; graph compile + optimized callees |
| aunt synthetic | reference 60 people | n/a | 1.517 | 1.517 | 2.11 x | 2.236 | 0.034 | 0.344 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.082 | 0.827 | 38 | 6 | 30000 | graph compile + optimized callees |
| aunt royal92 | reference royal92_simple.metta fallback | n/a | 1.547 | 1.547 | 26.67 x | 1.605 | 0.032 | 0.298 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.074 | 0.786 | 38 | 6 | 30000 | graph compile + optimized callees |
| aunt synthetic | process-sc static family 24 people | 6.111 | 1.288 | 7.399 | 43.94 x | 7.568 | 0.078 | 0.324 | 0.004 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.062 | 0.647 | 19 | 6 | 60000 | process SC setup; graph compile + optimized callees |
| graph two-hop | reference 90-chain | n/a | 1.456 | 1.456 | 4.26 x | 1.797 | 0.017 | 0.175 | 0.020 | 0.000 | 0.000 | 0.000 | 2 | 0 | 0 | 0 | 0.039 | 0.872 | 57 | 9 | 30000 | graph compile + optimized callees |
| graph mutual | reference 90-chain | n/a | 1.394 | 1.394 | 7.58 x | 1.578 | 0.018 | 0.091 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0 | 0 | 0 | 0.049 | 0.867 | 38 | 9 | 30000 | graph compile + optimized callees |
| datalog semi-naive | reference 24-chain | n/a | 18.170 | 18.170 | n/a | 18.172 | 0.367 | 16.640 | 0.206 | 15.193 | 0.000 | 0.000 | 9 | 1 | 0 | 0 | 0.005 | 0.103 | 38 | 3 | 30000 | graph compile + optimized callees |
| datalog semi-naive | process-sc 24-chain | 53.891 | 11.185 | 65.076 | 32.77 x | 67.062 | 0.157 | 2.887 | 0.010 | 0.000 | 0.000 | 0.000 | 10 | 0 | 0 | 0 | 0.605 | 5.398 | 38 | 12 | 90000 | process SC setup; graph compile + optimized callees |
| life | reference random 24x24 | n/a | 2846.416 | 2846.416 | 1133.00 x | 2848.928 | 0.552 | 2557.462 | 0.385 | 0.000 | 0.000 | 0.000 | 1384 | 0 | 0 | 0 | 2.359 | 283.125 | 114 | 12 | 60000 | graph compile + optimized callees |
| life | compile-pass random 24x24 | 75.192 | 20.378 | 95.570 | n/a | 95.570 | 0.982 | 52.019 | 19.921 | 0.000 | 0.000 | 0.000 | 114 | 0 | 0 | 0 | 1.438 | 30.223 | 95 | 12 | 60000 | compile-pass setup; graph compile + optimized callees |
| life | compile-pass random 24x24 initial literal | 77.199 | 0.278 | 77.477 | n/a | 77.478 | 0.177 | 75.852 | 71.035 | 0.000 | 0.000 | 0.000 | 73 | 0 | 0 | 0 | 0.014 | 0.223 | 57 | 6 | 60000 | compile-pass setup with initial grid literal; graph compile + optimized callees |
| temperature | NOAA committed slice | n/a | 0.445 | 0.445 | 100.34 x | 0.449 | 0.024 | 0.079 | 0.006 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.011 | 0.052 | 38 | 3 | 30000 | graph compile + optimized callees |
| temperature | synthetic 32x64 | n/a | 0.334 | 0.334 | 22.38 x | 0.349 | 0.011 | 0.091 | 0.010 | 0.000 | 0.000 | 0.000 | 5 | 0 | 0 | 0 | 0.005 | 0.064 | 38 | 3 | 30000 | graph compile + optimized callees |
| sliding puzzle | 2x2 pure source full frontier step | n/a | 4.180 | 4.180 | n/a | 4.181 | 0.120 | 2.680 | 1.485 | 0.000 | 0.000 | 0.000 | 65 | 0 | 0 | 0 | 0.005 | 0.127 | 38 | 3 | 30000 | graph compile + optimized callees |
| sliding puzzle | 2x2 pure compile-pass full frontier step | 11.479 | 0.523 | 12.002 | n/a | 12.003 | 0.161 | 10.918 | 3.472 | 0.000 | 0.000 | 0.000 | 66 | 0 | 0 | 0 | 0.004 | 0.140 | 57 | 3 | 60000 | compile-pass setup; graph compile + optimized callees |
| sliding puzzle | 3x3 pure source depth-8 step | n/a | 36.335 | 36.335 | n/a | 36.335 | 0.137 | 35.017 | 32.546 | 0.000 | 0.000 | 0.000 | 176 | 0 | 0 | 0 | 0.003 | 0.076 | 38 | 3 | 30000 | graph compile + optimized callees |
| sliding puzzle | 3x3 pure compile-pass depth-8 step | 105.583 | 0.218 | 105.801 | n/a | 105.802 | 0.316 | 103.631 | 79.462 | 0.000 | 0.000 | 0.000 | 177 | 0 | 0 | 0 | 0.003 | 0.053 | 57 | 3 | 60000 | compile-pass setup; graph compile + optimized callees |
| sliding puzzle | 4x4 pure source depth-5 step | n/a | 55.836 | 55.836 | n/a | 55.837 | 0.150 | 53.677 | 43.998 | 0.000 | 0.000 | 0.000 | 341 | 0 | 0 | 0 | 0.004 | 0.057 | 38 | 3 | 30000 | graph compile + optimized callees |
| n-queens | MORKL 8x8 source | n/a | 350.837 | 350.837 | n/a | 350.838 | 0.132 | 349.725 | 345.651 | 0.000 | 0.000 | 0.000 | 130 | 0 | 0 | 0 | 0.003 | 0.056 | 38 | 3 | 30000 | graph compile + optimized callees |
| n-queens | MORKL 8x8 compile-pass | 430.201 | 0.398 | 430.599 | n/a | 430.599 | 0.643 | 428.417 | 415.403 | 0.000 | 0.000 | 0.000 | 131 | 0 | 0 | 0 | 0.006 | 0.063 | 57 | 3 | 60000 | compile-pass setup; graph compile + optimized callees |

| benchmark | variant | result | ms | note |
|---|---:|---:|---:|---|
| n-queens bit reference | n=8 | 92 | 0.050 | independent reference, not MORKL eval |
| n-queens bit reference | n=9 | 352 | 0.032 | independent reference, not MORKL eval |
| n-queens bit reference | n=10 | 724 | 0.170 | independent reference, not MORKL eval |
| n-queens bit reference | n=11 | 2680 | 0.865 | independent reference, not MORKL eval |
| n-queens bit reference | n=12 | 14200 | 4.408 | independent reference, not MORKL eval |

## Interpretation

The trie evaluator remains strongest on native path algebra with shared prefixes, joins, restriction, unwrap, and first-symbol iteration. The direct `execT` backend compounds that advantage when the graph is lowered to native ops and optimized callee graphs because it avoids rebuilding old `PathValue` and `SpaceValue` intermediates.

`evalZ` is included as a correctness-backed zipper traversal prototype, not yet as the winning runtime for every source shape. Memoized virtual zippers now keep the sliding-puzzle source rows near `evalTrie`, and top-level union-saturating self recursion is rejected as unsupported rather than falling back to concrete `evalTrie` materialization. The separate `ZIPPER_LARGE_BENCHMARKS.md` report contains larger asymptotic product-selector rows where zipper traversal avoids broad intermediate scans. Direct source n-queens remains omitted for `evalZ` with an explicit note because that high-level search tree still needs a dedicated recursive zipper strategy.

The pure Game-of-Life source is now intentionally a lowering benchmark: direct `evalTrie` still interprets the high-level relation program, while the graph backend lowers `Range`, `Unwrap`, joins, and literal coordinate relations into direct operations; use the `ROG execT` and compile+`ROG execT` columns for the lowered result.

Graph timings are intentionally conservative about semantics. Raw routine calls are not timed as raw helper graphs; helper routines are inlined/expanded and lowered before graph execution when possible, and surviving nonrecursive calls use optimized callee graphs. The fixpoint lowering handles the `Routines.fixpoint` union-saturating shape, while recursive residual call cycles still need dedicated lowering before `exec`/`execT` can be reported honestly.

Compilation is now explicitly bounded by wall-clock budgets as well as structural caps. Source normalization records every pass attempt, graph optimization records `hoist_loop_invariant_subgraphs`, `push_out`, and `optimize_sharing` per round, constant folding records eval/evalTrie/evalZ/execT time and call counts, and the compilation table separates setup SC time, lowering/inlining time, source-pass time, graph-build time, graph-optimization time, compile/run ratio, and compile+run time so runtime speedups are not mistaken for free compilation.

`budget ms sum` is the sum of the independent compile invocation budgets used for a row, including optimized callee-graph compiles when present; it is not elapsed time and not a single shared deadline across all rows.

Rows marked `compiled away; use compile+run` have been fully residualized to a one-node literal by the compile pipeline before execution. Their executor timings are still shown as an implementation sanity check, but their graph speedup factors are intentionally reported as `n/a`; the meaningful number is the compile+`ROG execT` total in the compilation table.

The independent bit-recursive n-queens rows are scaling references, not MORKL runtime rows. They record the known targets through 12x12 while the pure MORKL path-algebra program is executed exactly at 8x8 and compiled/residualized for graph execution.
