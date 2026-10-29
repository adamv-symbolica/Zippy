# Optimized-graph / delayed OpenFst benchmarks — 2026-10-29

All **51 benchmark jobs passed** on the Symba h4d xlarge: 14 existing report
entry points, four shards of each of nine native families, and the native
acceptor microbenchmark. [Raw results and environment](validation/openfst-graph/)
identify the validated sources and toolchain.

The [per-example cornerstone tables](cornerstone-benchmarks/README.md) contain
**290 measured evaluator rows**, with **no omitted runtime cells**: all five
Scala evaluators, the two digit-set references, and three C++ dialects for the
22 cornerstone variants. Fourteen supplementary large cases retain their two
Scala evaluators and three C++ dialects. The three previously omitted source
`evalZ` cases were measured and checked exactly in separate 192 GiB JVMs.

## Coverage

**166 workloads × three dialects = 498 native rows**, plus
**132 direct acceptor microbenchmark rows**. 489 native rows compare
complete results with the trie evaluator; the remaining 9 exceed the
250,000-path enumeration threshold and check exact arbitrary-precision cardinality.
Every microbenchmark also checks cardinality.

| Family | Logical workloads | Rows |
|---|---:|---:|
| algebra | 66 | 198 |
| algebra-scaling | 16 | 48 |
| construction | 12 | 36 |
| cost-correlation | 4 | 12 |
| cost-counter | 21 | 63 |
| independent-product | 8 | 24 |
| iter-range-tail | 3 | 9 |
| large | 14 | 42 |
| programs | 22 | 66 |


Algebra covers 1%, 50% and 90% sharing; scaling reaches 65,536 input paths.
Construction and incremental union reach 32,768 paths. The largest result has
576,000,000 paths, represented as an automaton rather than enumerated.

## Representative native measurements

Milliseconds per invocation, with compilation and setup recorded separately.
These are measurements under shared load, not isolated speedup comparisons.

| Workload | Symbols ms | Bits ms | Bytes ms |
|---|---:|---:|---:|
| algebra-scaling/union-subset/65536 | 27.756495 | 11.356695 | 7.961833 |
| algebra-scaling/restriction-quarter/65536 | 0.541026 | 5.206389 | 0.528224 |
| incremental-union/32768 | 0.630832 | 6374.502379 | 2694.426563 |
| algebra/1%/unary/tails-intersection | 1.279345 | 76900.825146 | 11435.071608 |
| algebra/1%/range/first(512) | 0.169918 | 30319.865225 | 5018.222599 |


The native emitter uses optimized `RecursiveOpGraph` coordinates, typed locals
and inline loops. Operators build delayed deterministic OpenFst machines.
Persistent prefix edits retain suffix sharing; completed wrap/unwrap views and
literal epsilon concatenations cancel. Native timing forces the final graph
and exact DAG counts inside every invocation.

## Measurement details

- The node has 192 AMD EPYC 9B45 CPUs and approximately 1,488 GiB RAM. The matrix
  uses 12 workers and four shards, with the differential audit active. Rerun
  with one worker on an idle node for isolated latency.
- Native timings include final lazy-automaton expansion and cardinality
  analysis. They exclude compilation, input-acceptor construction, protocol IO
  and output enumeration. Grounded host calls, when present, include their
  bridge IO and host execution. All 108 cornerstone programs are standalone.
- Logical rows use a median of five adaptive batches targeting 10 ms, capped at
  4,096 repetitions. A first invocation above one second uses one sample.
  Microbenchmarks use five samples, except incremental unions, which use one.
- `graph_compile_ms` measures source lowering, normalization, graph optimization
  and C++ emission. `graph_nodes`, `generated_bytes` and `precomputed` expose
  what remains after optimization. 18 native rows are precomputed programs;
  their execution times must be read together with compilation costs.
- `compile_ms` is native session setup and may hit the compilation cache.
  Normal code uses `-O2`; cold units over two million characters use `-O0` unless
  overridden. The shared automaton runtime uses Release optimization.
- Reported states/arcs describe the final productive automaton view, not peak
  memory or all intermediate caches. Operators do not minimize or copy entire
  graphs after each operation.
- The logical bit matrix uses Fibonacci-coded intern IDs; bytes use four-byte
  big-endian IDs. Direct microbenchmarks use fixed binary bit paths and fixed
  byte paths. All seven translation routes, including terminated strings and
  injections, are checked separately by the full audit.
- The two set-reference dialects and three additional source `evalZ` rows use
  separate JVM experiments; their exact protocols and raw values are linked
  from the cornerstone tables. Source `evalZ` uses four untimed evaluations
  including correctness, followed by one timed evaluation.
- Historical eager-runtime measurements remain in
  [validation/openfst](validation/openfst/). The graph pipeline, precomputation
  and final-consumption protocol changed, so those runs are not controlled
  before/after speedup measurements.
- Fixture selection is unchanged: royal92 uses its labelled fallback and NOAA
  uses the committed slice. The historical `slowProgramScTimingRun` requires an
  absent prior fuzzer CSV; it is not a self-contained report entry point.

## Reproduce

```sh
python3 tools/benchmark_openfst.py --workers 1 --output /tmp/openfst-benchmarks
```

See [backend setup](OPENFST_BACKEND.md), [validation](OPENFST_TEST_REPORT.md),
and the [compiled source bundle](compiled-cornerstones/README.md).
