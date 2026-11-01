# Adiabatic cursor reuse and before/after performance

All measurements use the same Symba h4d xlarge. The baseline is commit `00c4163`; the final compiler/runtime is `fdf15dd`. Timings include execution and exact result cardinality. The complete [664-row matrix](before-after.csv) contains every native workload, including the new length-prefix encoding. All 498 common rows have matching result cardinalities.

The baseline ran eight benchmark workers; the final run used four while validation, C++ benchmarks and exports used other cores. These are throughput-run measurements. Large effects are useful; small differences need isolated repeats. No warmup, counting or reference-representation equivalence is assumed across the different evaluator implementations.

## Cursor reuse alone

This ablation compiles the same optimized graphs twice with `morkl.pathmap.reuse=false/true`, using the same runtime, counting method, inputs and five timing samples. The measured program walks a fixed byte prefix and reconstructs it. Every output was checked exactly before timing.

| Prefix bytes | Input paths | Reuse off ms | Reuse on ms | Speedup |
| --- | --- | --- | --- | --- |
| 4 | 1,024 | 0.004 | 0.004 | 1.20× |
| 4 | 16,384 | 0.090 | 0.053 | 1.72× |
| 4 | 65,536 | 0.640 | 0.214 | 2.99× |
| 8 | 1,024 | 2.372 | 0.208 | 11.43× |
| 8 | 16,384 | 39.095 | 3.678 | 10.63× |
| 8 | 65,536 | 160.504 | 24.098 | 6.66× |
| 16 | 1,024 | 4.182 | 0.404 | 10.35× |
| 16 | 16,384 | 68.975 | 7.827 | 8.81× |
| 16 | 65,536 | 272.061 | 36.296 | 7.50× |

The optimized generated scanners contain one read cursor, one output map/write cursor, zero cursor forks and zero `concat_path` calls. Their final head projection writes from the child mask directly, using native masked grafts for dense masks at empty output focuses. The disabled versions retain nested maps, cursor forks and copied concatenations. The [ablation sources](../../output/pathmap-adiabatic/after/reuse/cursor-reuse/) and [raw rows](../validation/pathmap-adiabatic/cursor-reuse.csv) are retained.

## Complete implementation comparison

This includes cursor/path/output reuse, faster checked physical-node cardinality, helper sharing and proven scanner specialization. Dense heads and multi-branch tails use native bulk algebra; a discarded per-byte version regressed dense-prefix and OEIS build performance. It does not isolate those contributions. A speedup below 1 is a regression.

| Encoding | Paired rows | Geometric mean speedup | Median speedup | Worst speedup |
| --- | --- | --- | --- | --- |
| bytes-interned | 166 | 2.63× | 1.55× | 0.52× |
| bytes-strings | 166 | 2.99× | 1.69× | 0.40× |
| bits-bytes | 166 | 3.29× | 1.55× | 0.88× |

| Family | Paired rows | Geometric mean speedup | Median speedup |
| --- | --- | --- | --- |
| programs | 66 | 3.28× | 1.21× |
| large | 42 | 1.15× | 1.13× |
| algebra | 198 | 2.54× | 1.52× |
| iter-range-tail | 9 | 3.73× | 0.92× |
| algebra-scaling | 48 | 8.13× | 7.58× |
| construction | 24 | 5.59× | 7.21× |
| incremental-union | 12 | 1.05× | 1.03× |
| independent-product | 24 | 1.03× | 1.04× |
| cost-counter | 63 | 5.25× | 3.54× |
| cost-correlation | 12 | 2.65× | 2.25× |

The eight largest improvements and eight largest regressions are shown below; the CSV retains every row. Some constant programs mostly measure result cardinality, and are marked in the CSV.

| Workload | Encoding | Before ms | After ms | Speedup |
| --- | --- | --- | --- | --- |
| programs/n-queens/MORKL 8x8 compile-pass | bits-bytes | 0.079094 | 0.000466 | 169.73× |
| programs/sliding puzzle/3x3 pure compile-pass depth-8 step | bits-bytes | 0.381122 | 0.002470 | 154.30× |
| programs/n-queens/MORKL 8x8 compile-pass | bytes-interned | 0.062397 | 0.000640 | 97.50× |
| programs/temperature/synthetic 32x64 | bytes-interned | 0.428603 | 0.005431 | 78.92× |
| programs/temperature/synthetic 32x64 | bits-bytes | 0.316602 | 0.004096 | 77.30× |
| iter-range-tail/heads=32/fanout=12 | bytes-strings | 30.724616 | 0.421795 | 72.84× |
| programs/temperature/synthetic 32x64 | bytes-strings | 0.265892 | 0.003651 | 72.83× |
| programs/sliding puzzle/3x3 pure compile-pass depth-8 step | bytes-interned | 0.263537 | 0.003981 | 66.20× |
| algebra/50%/combination/diff then restriction | bytes-strings | 1.176995 | 2.962974 | 0.40× |
| large/semi-naive datalog over generated chain graph/80 nodes, 159 edge facts | bytes-interned | 53.105288 | 102.471126 | 0.52× |
| algebra/50%/combination/range of union | bytes-strings | 1.575797 | 2.438082 | 0.65× |
| large/semi-naive datalog over generated chain graph/40 nodes, 79 edge facts | bytes-interned | 12.068463 | 17.036285 | 0.71× |
| independent-product/original/256 | bytes-interned | 36.750544 | 51.220671 | 0.72× |
| iter-range-tail/heads=192/fanout=16 | bytes-interned | 10.648702 | 13.314241 | 0.80× |
| iter-range-tail/heads=96/fanout=16 | bytes-interned | 2.841081 | 3.440105 | 0.83× |
| algebra/1%/range/last(512) | bytes-interned | 560.278294 | 671.870657 | 0.83× |

## Length-prefix folds and OEIS

A generic ordered fold initially rescanned all remaining length-prefixed heads at each step. The compiler now proves the ordinary length scanner and streams native fixed-depth cursors once in payload order. This preserves string ordering independently of the leading length byte. The 32,768-head fold is included in construction and in the complete CSV. The old generic length-prefix implementation has no completed timing at that size; it is not included as a claimed speedup.

| Fold heads | Interned bytes ms | Terminated strings ms | Length-prefixed strings ms | Fibonacci bits ms |
| --- | --- | --- | --- | --- |
| 512 | 1.889526 | 0.879244 | 0.843294 | 1.441188 |
| 2048 | 7.778819 | 3.916600 | 3.748718 | 6.926580 |
| 8192 | 33.675824 | 16.983775 | 17.124887 | 34.232028 |
| 32768 | 169.536375 | 77.698287 | 73.578728 | 178.702634 |

The [OEIS report](../OEIS_BENCHMARK.md) compares Zippy with the upstream PathMap algorithm on all 375,842 records. Execution and counting are reported separately. Native bulk length projection follows a proof of all 256 graph branches; no OEIS-specific runtime primitive or encoding metadata is introduced.

## Single-worker regression checks

The two largest throughput-run regressions were repeated in three sequential JVM pairs, pinned to CPUs 80–87. Each JVM retains five native timing samples. These checks are separate from the original matrix; no rows were replaced.

| Workload | Encoding | Before ms | After ms | Speedup |
| --- | --- | --- | --- | --- |
| 50%/combination/diff then restriction | bytes-interned | 0.571747 | 0.519842 | 1.10× |
| 50%/combination/diff then restriction | bytes-strings | 1.173335 | 0.944513 | 1.24× |
| 50%/combination/diff then restriction | bits-bytes | 1.526371 | 1.106954 | 1.38× |
| semi-naive datalog over generated chain graph/80 nodes, 159 edge facts | bytes-interned | 53.094792 | 102.069594 | 0.52× |
| semi-naive datalog over generated chain graph/80 nodes, 159 edge facts | bytes-strings | 1281.468434 | 1216.634602 | 1.05× |
| semi-naive datalog over generated chain graph/80 nodes, 159 edge facts | bits-bytes | 2640.724578 | 2382.441274 | 1.11× |

The string diff/restriction loss did not reproduce in these sequential runs. The 80-node Datalog workload with interned bytes retains a **0.52× speedup** (about 1.92× slower); this remains a performance limitation. String and Fibonacci encodings improved modestly on the same workload. [Raw samples and run metadata](../validation/pathmap-adiabatic/isolated-regressions/summary.json) are retained.
