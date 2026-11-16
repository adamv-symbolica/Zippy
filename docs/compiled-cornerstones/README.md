# Standalone C++ cornerstone programs

The current review directory is [`output/pathmap-adiabatic/cpp-cornerstones/`](../../output/pathmap-adiabatic/cpp-cornerstones/README.md).
It contains **108 complete C++ programs**: the symbol, bit and byte dialects for
22 cornerstone variants and 14 supplementary large cases. Each case includes
optimized graphs, symbolic source, codeword dictionaries, input bindings and
wire inputs. The shared delayed OpenFst runtime and CMake project are in `runtime/`.

All 108 sources were regenerated, rebuilt and executed on the h4d on 2026-11-16
at compiler/runtime revision `3fe2820`. All decoded
outputs match the independent Scala trie fixtures from the companion Rust
bundle. [Build/execution checks](../validation/cornerstone-regressions/fix/cpp-standalone-checks.json)
and the [independent-oracle check](../validation/cornerstone-regressions/fix/standalone-oracle-checks.json)
are retained. Review builds use `-O0`; the native benchmark run uses its normal
optimized build configuration.

The [per-example comparison](../validation/cornerstone-regressions/fix/comparison.csv)
contains all 434 current evaluator/compiled measurements against the earlier
baseline. The [regression report](../EVIDENCE_ELIMINATION.md#cornerstone-regression-cause-and-fix)
explains the fix and its validation.

Restore the committed [portable source archive](cornerstone-review.tar.gz):

```sh
mkdir -p output
tar -xzf docs/compiled-cornerstones/cornerstone-review.tar.gz -C output
```

[manifest.json](manifest.json) identifies each source and SHA-256 hash;
[archive.json](archive.json) identifies the archive and source totals.
OpenFst is an external build dependency. The bundle includes the fixed
singleton-range lowering and its optimized execution graphs.
