# Standalone C++ cornerstone programs

The current review directory is [`output/pathmap-adiabatic/cpp-cornerstones/`](../../output/pathmap-adiabatic/cpp-cornerstones/README.md).
It contains **108 complete C++ programs**: the symbol, bit and byte dialects for
22 cornerstone variants and 14 supplementary large cases. Each case includes
optimized graphs, symbolic source, codeword dictionaries, input bindings and
wire inputs. The shared delayed OpenFst runtime and CMake project are in `runtime/`.

All 108 sources were rebuilt and executed on the h4d on 2026-11-01. All decoded
outputs match the independent Scala trie fixtures from the companion Rust
bundle. [Build/execution checks](../validation/pathmap-adiabatic/cpp-standalone-export-checks.json)
and the [independent-oracle check](../validation/pathmap-adiabatic/cpp-oracle-checks.json)
are retained. Review builds use `-O0`; the native benchmark run uses its normal
optimized build configuration.

The [per-example tables](../pathmap-adiabatic-benchmarks/README.md) contain all
434 current evaluator/compiled measurements. [The full C++ matrix](../validation/pathmap-adiabatic/openfst/summary.json)
completed all 37 jobs and 498 rows across nine native workload families.

Restore the committed [portable source archive](cornerstone-review.tar.gz):

```sh
mkdir -p output
tar -xzf docs/compiled-cornerstones/cornerstone-review.tar.gz -C output
```

[manifest.json](manifest.json) identifies each source and SHA-256 hash;
[archive.json](archive.json) identifies the archive and source totals.
OpenFst is an external build dependency. The generated C++ sources remain
byte-identical to the previous review bundle; they were checked again against
the current inputs and backend on this node.
