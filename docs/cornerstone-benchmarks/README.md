# Cornerstone benchmarks, example by example

Recorded on **2026-10-29**, Symba **h4d xlarge**, 192 CPUs (AMD EPYC 9B45).
These tables combine the retained evaluator/C++ matrix with a new run of the two digit-set reference interpreters.
There are **22 cornerstone variants**, **14 supplementary large cases**, and **108 C++ dialect measurements**.
The primary variants include all five original Scala evaluator columns (three `evalZ` omissions are explicitly retained), plus **44 additional bit/byte set-reference measurements**.
The supplementary large suite originally measures `evalTrie` and `evalZ` only.
All 108 native results were checked exactly against the trie result.

| Example | Primary variants | Large variants | C++ rows |
| --- | --- | --- | --- |
| [Aunt queries](aunt.md) | 5 | 2 | 21 |
| [Graph queries](graph.md) | 2 | 3 | 15 |
| [Strongly connected components](scc.md) | 1 | 1 | 6 |
| [Semi-naive Datalog](datalog.md) | 2 | 2 | 12 |
| [Game of Life](life.md) | 3 | 0 | 9 |
| [Temperature](temperature.md) | 2 | 0 | 6 |
| [Sliding puzzle](sliding-puzzle.md) | 5 | 0 | 15 |
| [N-queens](n-queens.md) | 2 | 0 | 6 |
| [Supplement: large selective products](large-products.md) | 0 | 6 | 18 |


[Combined evaluator CSV](evaluators.csv) · [Scala compilation detail](scala-compilation.csv) · [Constant-fold sweep](constant-fold.csv) · [Generated C++ bundle](../../output/cornerstone-review/README.md) · [Artifact manifest](../../output/cornerstone-review/manifest.json)

## Measurement notes

- Scala primary runtime values are averages after two warmups. The supplementary large Scala values are medians after two warmups. Native values are medians of five adaptive batches (one sample for invocations over one second), with an untimed setup invocation.
- The bit/byte set references were measured in an additional h4d run on the same date: two warmups, median of five individual evaluations (one timed evaluation if the first exceeds one second), with four isolated JVM workers, 128 GiB heap limits, and eight active processors per JVM. Translation, input encoding, and exact-result decoding/checking are outside the timed interval. These are a separate run with a different load profile, not a controlled speedup comparison with the retained matrix.
- Native times exclude compilation, initial input acceptor construction, request/result IO, and result enumeration. Opaque host callbacks include their bridge IO and JVM work. Each source artifact records its callback count; standalone rows need no callback bridge.
- `ROG exec` and `ROG execT` use the optimizing graph pipeline. Some entire programs become one-node literals, marked **precomputed literal**; the corresponding compilation cost is shown. Native C++ emits the selected source/residual expression directly. It does not implicitly apply that graph pipeline. Rows labelled `compile-pass` or `process-sc` already use residual expressions in all columns.
- Native `compile_ms` is session setup and may hit a cache. It is shown separately and is not a uniformly cold compiler benchmark. The single cold `reference_trie_ms` correctness timing is deliberately excluded from these comparison tables.
- The bit transpilation and reference use Fibonacci-coded intern IDs. The byte transpilation and reference use four-byte big-endian intern IDs. Zero-terminated strings and the other injection routes were correctness-tested separately; no per-cornerstone timing for those routes was recorded. The supplementary large suite retains its original evaluator coverage; the 44 additional reference timings cover the 22 cornerstone variants.
- The retained evaluator/C++ matrix came from separate report processes on the original node, with 16 benchmark workers and the differential audit active. Those values are measurements under shared load, not isolated latency or a controlled speedup study. The additional reference run used a fresh h4d node with four workers. No cross-protocol speedup is inferred.
- The royal92 case uses its explicitly labelled fallback; NOAA uses the committed data slice. `evalZ` is omitted for the 3x3 and 4x4 source puzzle searches and the source 8-queens search because of the original benchmark's memory limits.
- Generated source and input fixtures were exported later from the validated compiler and case factories. Fresh internal routine names can differ from the node's cached source. Export hashes identify the exact review files; these are not claimed to be byte-for-byte copies of the now-terminated node's compiler cache.

Raw evidence: [primary Scala report](../validation/openfst/existing-reports/trieBenchmarkReport.log), [large Scala report](../validation/openfst/existing-reports/zipperLargeBenchmarkReport.log), [primary native CSV](../validation/openfst/programs.csv), [large native CSV](../validation/openfst/large.csv), [bit/byte reference CSV](../validation/openfst/cornerstone-radix/reference.csv), [additional run environment and results](../validation/openfst/cornerstone-radix/summary.json), [environment and validated source hashes](../validation/openfst/environment.json).

## Reproduce this review bundle

From the repository root, with a new output directory:

```sh
scala-cli run src/main/scala src/test/scala --test --server=false \
  --scala 3.8.1 --source 3.3 \
  --dependency org.scalameta::munit:1.2.1 \
  --dependency org.scala-lang.modules::scala-collection-contrib:0.3.0 \
  --java-opt -Xmx12g --java-opt -Xss64m \
  --main-class morkl.exportCornerstoneCpp -- output/cornerstone-review true
python3 tools/report_cornerstones.py
```

The source exporter emits C++ without invoking the C++ compiler. The report join checks all 36 case names, all 108 dialect results, all 44 digit-reference results, result cardinalities, artifact SHA-256 hashes, and the validated source hashes. The generated directory is under the repository's existing ignored `output/` tree; the exporter and per-example benchmark reports are committed. A portable source archive is retained in `docs/compiled-cornerstones/`.

To rerun the additional digit references on an h4d node, package a portable assembly and copy it, `tools/benchmark_cornerstone_radix.py`, and `src/test/resources/noaa_slice.txt` to the same relative paths on that node:

```sh
scala-cli --power package src/main/scala src/test/scala --server=false \
  --scala 3.8.1 --source 3.3 \
  --dependency org.scalameta::munit:1.2.1 \
  --dependency org.scala-lang.modules::scala-collection-contrib:0.3.0 \
  --assembly --main-class morkl.cornerstoneRadixReference -o reference.jar
python3 tools/benchmark_cornerstone_radix.py --jar reference.jar \
  --output reference-results --workers 4 --seconds 600
```
