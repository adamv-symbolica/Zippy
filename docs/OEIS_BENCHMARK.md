# OEIS with Zippy

Zippy reproduces [PathMap’s OEIS benchmark](https://github.com/Adam-Vandervorst/PathMap/blob/master/benches/oeis.rs): build a sequence trie, index suffixes at offsets 0–10, and query the prefix `1,2,3,5,8,13`. The eleven per-offset content counts are measured separately as well. The upstream file contains the content-count routine but does not register it among its benchmark arguments.

The full local OEIS snapshot contains **375,842 records** (2024-09-12). Numeric symbols use the upstream signed big-endian `BigInt` bytes, preceded by one length byte. This uses the same framing as the new string translation, with numeric payloads supplied as existing byte paths. The original PathMap comparison retains `usize` terminal IDs; Zippy stores unit terminals and compares the exact key sets. Duplicate sequences therefore have one terminal in both structures.

## Results

Median of five samples on the Symba h4d xlarge, 2026-11-01. All times are milliseconds. Compilation, text parsing, record-trie ingestion, protocol IO and exact key-set comparisons are outside execution timings. Native execution and cardinality are separate columns. For content stages, execution includes counting.

| Stage | Paths | PathMap exec ms | Zippy exec ms | PathMap count ms | Zippy count ms |
| --- | --- | --- | --- | --- | --- |
| build_map | 373,311 | 289.955954 | 834.294131 | 60.978387 | 174.251468 |
| drophead_cold | 3,996,625 | 1443.351418 | 1406.341904 | 792.204848 | 838.880982 |
| drophead_steady | 3,996,625 | 2835.574524 | 1441.992691 | 0.0 | 873.764498 |
| query | 222 | 0.013099 | 0.008422 | 4.280942 | 0.027692 |
| contents_0 | 373,311 | 115.570265 | 179.158158 | 0.0 | 0.0 |
| contents_1 | 372,920 | 126.031978 | 172.160531 | 0.0 | 0.0 |
| contents_2 | 372,371 | 133.002174 | 170.22646 | 0.0 | 0.0 |
| contents_3 | 371,361 | 141.716204 | 170.51951 | 0.0 | 0.0 |
| contents_4 | 369,266 | 146.455724 | 169.361182 | 0.0 | 0.0 |
| contents_5 | 366,254 | 151.859059 | 169.448113 | 0.0 | 0.0 |
| contents_6 | 362,746 | 153.856725 | 167.418889 | 0.0 | 0.0 |
| contents_7 | 359,204 | 153.327101 | 161.490246 | 0.0 | 0.0 |
| contents_8 | 354,722 | 150.777677 | 158.368593 | 0.0 | 0.0 |
| contents_9 | 349,946 | 146.909094 | 148.509996 | 0.0 | 0.0 |
| contents_10 | 344,524 | 139.279369 | 136.711077 | 0.0 | 0.0 |

Cold drophead starts from the original map. Steady drophead starts from the indexed map, matching the repeated in-place upstream benchmark. Zippy’s pure operation returns the corresponding indexed key set. The upstream steady sample does not include a cardinality call; its zero count column is intentional. Query cardinality can differ in cost because unit-valued PathMap representation shares more state than maps retaining sequence IDs. These are not interchangeable storage or ingestion benchmarks.

Exact native/Zippy key-set comparisons passed for the build, all offsets and the query: **373,311** unique initial sequences, **3,996,625** indexed paths, and **222** matching query paths. Independent small-model tests include negative and multibyte integers, duplicate sequences, incomplete suffixes and short records.

## Program and reproduction

[OEISBenchmark.scala](../src/test/scala/OEISBenchmark.scala) constructs ordinary byte-language operations and functions. The build drops the four-byte record identifier and wraps offset zero. Drophead shares ten calls to a proven ordinary length-dispatch helper. Query uses native prefix restriction. No new Zippy expression case or OEIS runtime opcode is added. The [generated programs and harness](../output/pathmap-adiabatic/oeis/sources/) contain fourteen standalone stage programs and the combined native comparison harness.

```sh
python3 tools/benchmark_oeis.py --data /path/to/oeis_stripped.txt \
  --output output/oeis-rust --samples 5
```

Dataset: `76,128,173` bytes; SHA-256 `71308b63dfd641dacd1e9be9b6ea9f474866f75cad1ed1c6faf73faff9bdebc3`. The large input is not committed. [Raw samples](validation/pathmap-adiabatic/oeis/samples.csv), [medians](validation/pathmap-adiabatic/oeis/median.csv), [run metadata](validation/pathmap-adiabatic/oeis/summary.json) and [execution log](validation/pathmap-adiabatic/oeis/execution.log) are retained.
