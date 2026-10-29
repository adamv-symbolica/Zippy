# OpenFst benchmark measurements — 2026-10-29

All native benchmark jobs and the 14 existing benchmark/analysis report entry
points finished successfully on the Symba h4d node. The exact machine-readable
summaries, raw CSVs, environment, and existing report logs are committed in
[validation/openfst](validation/openfst/).

## Coverage

The native logical matrix contains **166 workloads × 3 dialects = 498 rows**, plus **132 C++ acceptor microbenchmark rows**.

| Native family | Logical workloads | Rows |
|---|---:|---:|
| programs | 22 | 66 |
| large | 14 | 42 |
| algebra | 66 | 198 |
| iter-range-tail | 3 | 9 |
| algebra-scaling | 16 | 48 |
| construction | 12 | 36 |
| independent-product | 8 | 24 |
| cost-counter | 21 | 63 |
| cost-correlation | 4 | 12 |

The algebra matrix uses the exact 1%, 50%, and 90% sharing scenarios from the
existing suite. The iteration/range matrix uses (heads, fanout) = (32, 12),
(96, 16), and (192, 16). Construction and incremental union span 256–32,768
paths; algebra scaling reaches 65,536 paths. Independent products and large
queries retain their original source programs and inputs. Native microbenchmarks
also cover identity, root-disjoint, mixed-quarter, and interwoven key regimes.

Correctness checks compare **489 rows exactly** with the trie evaluator. The other **9 rows** exceed the 250,000-path enumeration threshold and use exact cardinality checks. Every microbenchmark row checks its result cardinality.

## Representative native measurements

Milliseconds per invocation after warmup; columns use the same logical input.
Compile time is recorded separately in the CSVs.

| Workload | Symbols ms | Bits ms | Bytes ms |
|---|---:|---:|---:|
| algebra-scaling/union-subset/65536 | 1.548431 | 5.328252 | 1.018817 |
| algebra-scaling/restriction-quarter/65536 | 0.555893 | 5.093330 | 0.571387 |
| incremental-union/32768 | 365.272589 | 6531.302107 | 1795.232407 |
| algebra/1%/unary/tails-intersection | 1.908452 | 46438.389753 | 12084.789704 |
| algebra/1%/range/first(512) | 3.325171 | 49744.738154 | 3764.793848 |

The largest checked result has **576,000,000 paths**. Its bits acceptor uses **122,741 states and 142,860 arcs**. The CSVs preserve every dialect's graph sizes and timing.

Encoded recursive folds, ordered ranges, and tail intersections can cost substantially more than
native symbolic operations. The backend therefore retains native head/tail
projection, deterministic graph products, suffix sharing, arbitrary-precision
range counts, and bounded Fibonacci/ordered prefix selection; the table records
the remaining costs rather than implying uniform speedups.

## Measurement method and limits

- C++ timing starts after input acceptors are built and one untimed setup invocation.
  It excludes compilation, request/result protocol IO, and output enumeration.
  The benchmark runner disables redundant internal warmups; exact rows use
  their correctness invocation as setup, and cardinality-only rows explicitly
  execute a discarded setup invocation before measurement. Inputs are
  immutable, and every measured invocation executes the generated function.
- If a workload uses an opaque grounded JVM function, its measured invocation
  includes that host callback and its bridge IO. Such a row is not pure C++
  throughput. Native C++ bindings can remove this boundary, as validated by the
  standalone export test.
- Logical rows use a median of five adaptive batches, targeting 10 ms per batch
  with at most 4,096 repetitions. A first sample above one second uses one
  sample; the `samples` column records this. C++ microbenchmarks use five samples
  except expensive incremental unions, which use one.
- The bit logical matrix uses Fibonacci-interned symbols; bytes use fixed
  four-byte intern IDs. The full-suite audit separately verifies all seven
  translation directions, including zero-terminated strings and injections.
- The largest expanded puzzle program exceeded the initial 600-second compiler
  cap. The final backend exposes `morkl.openfst.compileSeconds` (default 1,800),
  and the complete program family was rerun successfully with that budget.
  The final summary contains the complete successful matrix.
- `compile_ms` measures session construction and can include a cache hit. It is
  not a uniformly cold compiler benchmark. Emitted constants can make residual
  programs nearly free after warmup; their compilation is not counted as native
  execution.
- `reference_trie_ms` is a single cold correctness evaluation, not a comparable
  warmed benchmark. No speedup ratios are claimed from it. The original report
  logs retain their own timing protocols.
- The recorded matrix used 16 benchmark workers, with six independent shards
  for every logical family, while the differential audit was active. The node has 192 CPUs, but these are throughput measurements under
  shared load. For isolated latency, rerun with `--workers 1` on an idle node.
- Existing fixture selection is retained, including the explicitly named
  royal92 fallback when its optional external dataset is absent. The NOAA
  workload uses the committed slice.
- The historical `slowProgramScTimingRun` is a replay utility requiring an
  uncommitted prior fuzzer CSV; that input is absent. It is not one of the
  self-contained benchmark/report jobs above. The actual zipper fuzzer tests
  are included in the complete Scala suite and native differential audit.

## Reproduction

```bash
python3 tools/benchmark_openfst.py --workers 1 --output /tmp/openfst-benchmarks
```

The runner compiles one private Scala snapshot to an immutable JAR before
launching benchmark processes. It runs all 14 existing reports, all nine native
logical families, and the C++ acceptor microbenchmark. See
[backend setup](OPENFST_BACKEND.md) for dependencies and configuration, and
[validation details](OPENFST_TEST_REPORT.md) for toolchain versions and checks.
